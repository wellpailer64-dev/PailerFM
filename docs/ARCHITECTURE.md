# Pailer Player — Arquitetura

> **Regra de ouro deste projeto:** preservar o comportamento atual antes de refatorar.
> Toda mudança estrutural mantém as funcionalidades existentes e é feita incrementalmente.
> O app já funciona e está em uso diário. Nada de "clean architecture deluxe" que mate a rádio no processo.

App Android pessoal de música local que simula uma **rádio FM**: fila de músicas com
abertura em vinheta gravada e boletins de notícia narrados por locutores virtuais.

**16/09/2026:** o boletim deixou de ser gerado dentro do app. Redator (local/Gemini),
busca de RSS e síntese de voz (local/Gemini/TTS Android) foram removidos - hoje o app só
baixa boletins já prontos (roteiro + áudio) de um feed publicado por uma central de
broadcast externa (`BroadcastFeedRepository`). Ver ADR-034/ADR-035 em
[DECISIONS.md](DECISIONS.md) e a seção "Feed remoto" em [RADIO_PIPELINE.md](RADIO_PIPELINE.md).

Outros docs: [RADIO_PIPELINE](RADIO_PIPELINE.md) · [BROADCAST_METADATA](BROADCAST_METADATA.md) · [BROADCAST_LOCAL_WORKBENCH](BROADCAST_LOCAL_WORKBENCH.md) · [STATE_MACHINE](STATE_MACHINE.md) · [APP_FOLDER](APP_FOLDER.md) · [DECISIONS](DECISIONS.md) · [TODO](TODO.md) · [RELEASE](RELEASE.md)

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Kotlin, coroutines |
| UI | Jetpack Compose (BOM 2024.06.00), Material 3, Navigation Compose |
| Áudio | Media3 / ExoPlayer 1.3.1 (`MediaSessionService`) |
| Boletim | `BroadcastFeedRepository` baixa `.wav` + roteiro prontos de um feed Cloudflare (`HttpURLConnection` puro) |
| Metadados | jaudiotagger 3.0.1 + MediaStore |
| Build | compileSdk 34, targetSdk 33, minSdk 26, ABI `arm64-v8a`, JVM 17 |

## Módulos e responsabilidades

```
app/src/main/java/com/pailer/localtune/
├── MainActivity.kt                  # host da Activity Compose
├── ui/
│   ├── LocalTuneApp.kt              # TODAS as telas (~3.4k linhas — monólito conhecido)
│   └── theme/Theme.kt
├── player/
│   ├── LocalTuneViewModel.kt        # hub de estado (~3.7k linhas — monólito conhecido)
│   └── MusicPlaybackService.kt      # MediaSessionService + ExoPlayer + ações do widget
├── data/
│   ├── LocalSong.kt                 # modelos (LocalSong, LocalAlbum, LocalArtist, LocalRadio)
│   ├── MusicLibraryRepository.kt    # MediaStore + cache JSON + rádios + overrides de metadado
│   ├── RadioBulletin.kt             # data classes do roteiro (RadioScript/RadioScriptLine/...)
│   ├── BroadcastFeedRepository.kt   # baixa boletim aprovado (roteiro+audio) do feed Cloudflare
│   └── AlbumGenreSuggestionRepository.kt
├── util/
│   └── DayPeriod.kt                 # manhã/tarde/noite por hora, compartilhado UI + widget
└── widget/
    ├── PlayerWidget.kt              # widgets compacto/grande (RemoteViews)
    ├── RadioGifFrameCache.kt        # extrai 1 frame estático do gif do período (fundo do widget ao vivo)
    └── (providers/receiver/renderer/actions)
```

### Responsabilidades por arquivo

- **LocalTuneViewModel** — dono de todo estado observável pela UI (`mutableStateOf`:
  `LibraryUiState`, `LibraryContentUiState`, `PlayerUiState`, `MetadataUiState`,
  `RadioBulletinUiState`, `RadioBulletinBufferUiState`). Conecta ao `MediaController`,
  coordena modo rádio, boletins, metadados e favoritos.
- **MusicPlaybackService** — `MediaSessionService`: cria o `ExoPlayer`, expõe a sessão
  (notificação/lockscreen), trata ações PLAY_PAUSE/NEXT/PREVIOUS vindas dos widgets e
  re-renderiza os widgets a cada evento do player.
- **BroadcastFeedRepository** — lê o `manifest.json` publicado pela central de broadcast
  externa, baixa o próximo boletim aprovado ainda não usado (roteiro + `.wav`), valida
  tamanho/hash. Única fonte de conteúdo de boletim hoje - ver [RADIO_PIPELINE.md](RADIO_PIPELINE.md).
- **Repositórios** (`data/`) — sem estado global compartilhado; I/O em `Dispatchers.IO`.

## Fluxos principais

### Reprodução

```
UI (Compose) ──► LocalTuneViewModel ──► MediaController (Media3)
                                             │
                                             ▼
                                   MusicPlaybackService
                                   (ExoPlayer + MediaSession)
                                             │
                              notificação / lockscreen / widgets
```

- O ViewModel conecta via `SessionToken` e observa eventos com um `Player.Listener`
  (`onEvents` → atualiza estado; `onMediaItemTransition` AUTO → dispara boletins).
- Há também um **loop de polling** no `init {}` que atualiza `playerState` a cada
  1,5 s (com mídia) ou 3 s (idle). É intencional (posição do seek bar); não remover
  sem substituir por fonte de ticks equivalente.

### Biblioteca

```
MediaStore (áudio > 15 s) ──► loadSongs() ──► applyLibrarySongs()
        ▲                                        │
        │                                        ▼
library_cache.json ◄──────────────────── saveCachedSongs()
```

- Cache inicial em `filesDir/library_cache.json` para abrir rápido na segunda vez.
- Overrides de metadados (gênero, artista, título de álbum, unificação de artistas)
  ficam em SharedPreferences `metadata_overrides` (com schema version).
- "Rádios" são derivadas das músicas: perfil fixo Grunge + rádios dinâmicas por gênero +
  "Rádio recente". Sessões evitam repetir a sequência anterior
  (ver [RADIO_PIPELINE.md](RADIO_PIPELINE.md)).

### Boletim (feed remoto)

```
refillBulletinBuffer() ── BroadcastFeedRepository.downloadNextApprovedBulletin() ──► Cloudflare
                                                                       │
                                                    baixa .wav + roteiro, valida tamanho/hash
                                                                       │
                                              bulletinBuffer.addLast(PreparedBulletin)
                                                                       │
                                                                       ▼
                                     MediaPlayer toca o WAV (anúncio) e deleta ao terminar
```

Sem boletim aprovado novo, a vaga do buffer fica vazia e a rádio simplesmente não insere
boletim naquele intervalo - sem alternativa. Detalhes completos: [RADIO_PIPELINE.md](RADIO_PIPELINE.md).

### Vinhetas de abertura da rádio

Arquivos gravados em `app/src/main/res/raw/` (`radio_intro.mp3` + um complemento por
rádio, ex. `vinheta_grunge.wav`) tocados via `MediaPlayer.create()` antes da primeira
música de uma sessão nova. Mapa rádio → complemento:
`LocalTuneViewModel.VINHETA_BY_RADIO_KEY`. Ver [RADIO_PIPELINE.md](RADIO_PIPELINE.md) e
[`vinhetas/README.md`](../vinhetas/README.md).

## Persistência (mapa rápido)

| Onde | O quê |
|---|---|
| `SharedPreferences("playback_history")` | histórico ("Ouvir de novo"), última faixa/posição |
| `SharedPreferences("favorites")` | álbuns/músicas/artistas favoritos |
| `SharedPreferences("radio_bulletins")` | modo/duração/preferências dos boletins |
| `SharedPreferences("metadata_overrides")` | correções de tag e unificações de artista |
| `filesDir/library_cache.json` | cache da biblioteca |
| `filesDir/lyrics/<songId>.lrc` + `index.json` | letras (colada/editada, tag embutida cacheada, LRCLIB) — ADR-023 |
| `filesDir/radio_bulletins_ready/` + `manifest.json` | buffer de boletins baixados do feed remoto (roteiro + `.wav`) |

## Pontos de atenção conhecidos (não são bugs novos, são dívida documentada)

1. **Monólitos**: `LocalTuneApp.kt` (~8.9k linhas) e `LocalTuneViewModel.kt` (~3.7k linhas).
   Split planejado — ver [TODO.md](TODO.md).
2. **Estado do boletim em vars soltas** (`speakingNews`, `resumeAfterNews`, ...) com races
   conhecidas — catalogadas em [STATE_MACHINE.md](STATE_MACHINE.md).
3. **Sem testes automatizados**. As funções puras (anti-repetição de sessão, normalização
   de chave de dedup do feed remoto) são os primeiros alvos quando isso mudar.
