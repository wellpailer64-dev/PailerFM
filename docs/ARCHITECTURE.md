# Pailer Player — Arquitetura

> **Regra de ouro deste projeto:** preservar o comportamento atual antes de refatorar.
> Toda mudança estrutural mantém as funcionalidades existentes e é feita incrementalmente.
> O app já funciona e está em uso diário. Nada de "clean architecture deluxe" que mate a rádio no processo.

App Android pessoal de música local que simula uma **rádio FM**: fila de músicas com
abertura em vinheta gravada e boletins de notícia narrados por locutores virtuais
(TTS 100% offline).

Outros docs: [RADIO_PIPELINE](RADIO_PIPELINE.md) · [TTS](TTS.md) · [STATE_MACHINE](STATE_MACHINE.md) · [DECISIONS](DECISIONS.md) · [TODO](TODO.md) · [RELEASE](RELEASE.md)

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Kotlin, coroutines |
| UI | Jetpack Compose (BOM 2024.06.00), Material 3, Navigation Compose |
| Áudio | Media3 / ExoPlayer 1.3.1 (`MediaSessionService`) |
| TTS offline | sherpa-onnx (`app/libs/sherpa-onnx-static-link-onnxruntime-1.13.6.aar`), motores VITS/Piper/Kokoro/Supertonic |
| Fallback de voz | `android.speech.tts.TextToSpeech` (TTS do sistema, pt-BR) |
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
│   ├── LocalTuneViewModel.kt        # hub de estado (~1.5k linhas — monólito conhecido)
│   ├── MusicPlaybackService.kt      # MediaSessionService + ExoPlayer + ações do widget
│   ├── RadioVoiceSynthesisService.kt# síntese TTS em processo separado (:radio_voice)
│   └── LocalRadioVoiceEngine.kt     # wrapper sherpa-onnx OfflineTts + escritor WAV manual
├── data/
│   ├── LocalSong.kt                 # modelos (LocalSong, LocalAlbum, LocalArtist, LocalRadio)
│   ├── MusicLibraryRepository.kt    # MediaStore + cache JSON + rádios + overrides de metadado
│   ├── RadioBulletin.kt             # roteiros de boletim + writers (fallback / LLM opcional)
│   ├── NewsBulletinRepository.kt    # manchetes via RSS (g1)
│   ├── RadioVoicePackageRepository.kt # pacote .zip de vozes: import, validação, manifest
│   └── AlbumGenreSuggestionRepository.kt
├── util/
│   └── DayPeriod.kt                 # manhã/tarde/noite por hora, compartilhado UI + widget
└── widget/
    ├── PlayerWidget.kt              # widgets compacto/grande (RemoteViews)
    ├── RadioGifFrameCache.kt        # extrai 1 frame estático do gif do período (fundo do widget ao vivo)
    └── (providers/receiver/renderer/actions)
```

### Responsabilidades por arquivo

- **LocalTuneViewModel** — dono de todo estado observável pela UI (6 `mutableStateOf`:
  `LibraryUiState`, `LibraryContentUiState`, `PlayerUiState`, `MetadataUiState`,
  `RadioBulletinUiState`, `RadioVoiceUiState`). Conecta ao `MediaController`, coordena
  modo rádio, boletins, metadados e favoritos.
- **MusicPlaybackService** — `MediaSessionService`: cria o `ExoPlayer`, expõe a sessão
  (notificação/lockscreen), trata ações PLAY_PAUSE/NEXT/PREVIOUS vindas dos widgets e
  re-renderiza os widgets a cada evento do player.
- **RadioVoiceSynthesisService** — roda no processo `:radio_voice`. Recebe um script por
  Intent (+ `ResultReceiver`), sintetiza e devolve caminho de WAV. Ver [TTS.md](TTS.md).
- **LocalRadioVoiceEngine** — carrega `OfflineTts` conforme o motor do pacote, gera as falas,
  concatena amostras com gaps de silêncio e escreve o WAV à mão.
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

### Voz local

```
ViewModel ── startService(Intent + texts/speakers + ResultReceiver) ──► :radio_voice
                                                              RadioVoiceSynthesisService
                                                                       │
                                                        LocalRadioVoiceEngine (sherpa)
                                                                       │
        ViewModel ◄──── ResultReceiver(path do WAV, detail, elapsed) ──┘
             │
             ▼
     MediaPlayer toca o WAV (anúncio) e deleta o arquivo ao terminar
```

Detalhes completos: [TTS.md](TTS.md).

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
| `filesDir/radio_voice_package/` + `package.ready` | pacote de voz instalado |
| `filesDir/radio_voice_import/` | temporário durante import do .zip |
| `cacheDir/radio_voice_*.wav` | áudio dos anúncios (deletado após tocar) |

## Pontos de atenção conhecidos (não são bugs novos, são dívida documentada)

1. **Monólitos**: `LocalTuneApp.kt` (~3.4k linhas) e `LocalTuneViewModel.kt` (~1.5k linhas).
   Split planejado — ver [TODO.md](TODO.md).
2. **Estado do boletim em vars soltas** (`speakingNews`, `resumeAfterNews`,
   `nextBulletinIndex`, ...) com races conhecidas — catalogadas em
   [STATE_MACHINE.md](STATE_MACHINE.md).
3. **Engine TTS recarregado a cada request** (sem cache persistente entre boletins) e
   requests simultâneos no serviço não serializados — ver [DECISIONS.md](DECISIONS.md) ADR-003.
4. **Sem testes automatizados**. As funções puras (limites de palavras, filtros,
   anti-repetição de sessão) são os primeiros alvos quando isso mudar.
