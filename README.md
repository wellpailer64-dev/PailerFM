# Pailer Player

Player Android pessoal para musicas locais que simula uma **radio FM**: fila de musicas
intercalada com abertura falada (data, hora, temperatura) e boletins de noticias narrados
por locutores virtuais, com TTS 100% offline.

Feito em Kotlin, Jetpack Compose e Media3/ExoPlayer.

## O que faz

### Player
- Le musicas locais pelo MediaStore (MP3, FLAC, OGG, M4A e outros formatos do Android).
- Abas Inicio, Artistas, Albuns, Musicas e Playlists automaticas.
- Historico local leve para a area "Ouvir de novo" e retomada de posicao.
- Albuns sugeridos em carrossel visual; busca por musica, artista e album.
- Reproducao em segundo plano via MediaSessionService, com controles na notificacao,
  lockscreen e widgets (compacto e grande).
- Fila, proxima/anterior, shuffle, repeat off/all/one e seek.

### Modo Radio
- Radios derivadas da biblioteca (perfil Grunge, radios por genero, Rádio recente) com
  fila que evita repetir a sessao anterior.
- Abertura falada: data/hora + temperatura (open-meteo) + primeira faixa.
- Boletins de noticia entre as musicas (RSS do g1), em modo manchete ou dialogo entre
  locutora e locutor, com duracao configuravel.
- Vozes locais offline via sherpa-onnx (VITS/Piper/Kokoro/Supertonic) rodando em processo
  separado (`:radio_voice`); pacote .zip de vozes instalavel pela UI. Fallback para o
  TTS do sistema quando nao ha pacote ativo.

### Ferramentas de biblioteca
- Revisao de metadados: albuns sem genero/artista reconhecido, sugestoes e aprovacao.
- Unificacao de artistas duplicados e correcao de tags (jaudiotagger/MediaStore),
  com overrides reversiveis fora dos arquivos de audio.
- Apagar musica/album/artista com toque longo + confirmacao; remove de vez do aparelho
  via MediaStore (pede consentimento do sistema no Android 11+).

## Documentacao

Documentacao tecnica completa em [`docs/`](docs/):

| Doc | Conteudo |
|---|---|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | modulos, responsabilidades e fluxos gerais |
| [RADIO_PIPELINE.md](docs/RADIO_PIPELINE.md) | pipeline da radio de ponta a ponta |
| [TTS.md](docs/TTS.md) | vozes, pacote manifest.json, processo `:radio_voice` |
| [STATE_MACHINE.md](docs/STATE_MACHINE.md) | estados do boletim, races conhecidas e contrato alvo |
| [DECISIONS.md](docs/DECISIONS.md) | ADRs — decisoes e por que nao desfaze-las |
| [TODO.md](docs/TODO.md) | divida tecnica priorizada |

> Regra do projeto: preservar o comportamento atual antes de refatorar. Mudancas
> estruturais sao incrementais e nunca quebram funcionalidades existentes.

## Build local

Abra esta pasta no Android Studio ou rode:

```powershell
gradle assembleDebug
```

O APK debug fica em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Para uma versao assinada de uso diario, crie uma signing config release no Android Studio.
