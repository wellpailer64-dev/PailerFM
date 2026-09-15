# Vinhetas

Os áudios enviados já foram movidos e renomeados para `app/src/main/res/raw/` (só lá
viram parte do APK). Mapeamento atual, por rádio:

| Rádio (nome no app) | Intro comum | Complemento |
|---|---|---|
| todas | `radio_intro.mp3` | — |
| Grunge | | `vinheta_grunge.wav` |
| Rock | | `vinheta_rock.wav` |
| Punk | | `vinheta_punk_coldwave.wav` |
| Post-punk / cold wave | | `vinheta_punk_coldwave.wav` |
| Indie | | `vinheta_indie_psicodelico.wav` |
| Indie / psicodelico | | `vinheta_indie_psicodelico.wav` |
| MPB | | `vinheta_mpb.wav` |
| Hip-Hop/Rap | | `vinheta_hip_hop.wav` |
| Rap nacional | | `vinheta_rap.wav` |
| Jazz | | `vinheta_jazz.wav` |
| Anos 2000 | | `vinheta_anos_2000.wav` |

Ordem de reprodução ao entrar numa rádio (`playRadioSession` em
`LocalTuneViewModel.kt`): intro comum → complemento da rádio (se houver) → toca a
música. Rádios sem complemento na tabela (Metal, Pop, Rádio recente, etc.) tocam só a
intro comum. Detalhes em [docs/DECISIONS.md](../docs/DECISIONS.md) (ADR-010).

"Anos 2000" não é gênero — é um perfil por ano (`RadioProfile.yearRange` em
`MusicLibraryRepository.kt`, faixa 2000-2009, lido do `YEAR` do MediaStore/tag do
arquivo). Só forma rádio se houver músicas suficientes com esse ano preenchido na tag.

## Passagens (transição do boletim)

`passagem_1.mp3`/`passagem_2.mp3`/`passagem_3.mp3` em `res/raw` (fonte: `passagem.mp3`/
`passagem 2.mp3`/`passagem 3.mp3` aqui) — pontes curtas tocadas em volta do boletim de
notícias: música → passagem → boletim → passagem → música, sem gap entre elas. Alternam
em sequência a cada uso (`nextPassagemIndex`/`PASSAGEM_RESOURCES` em
`LocalTuneViewModel.kt`, `playPassagem()`), volume reduzido em -8dB
(`PASSAGEM_VOLUME`). Não são por gênero de rádio como as vinhetas acima.

`passagem 3.mp3` foi reprocessada em 15/09/2026 (fonte tocava ~7dB mais baixo que as
outras duas) — se for regravada, remedir o volume antes de decidir se precisa do mesmo
tratamento, ver ADR-027 em [docs/DECISIONS.md](../docs/DECISIONS.md).

## Vinhetas de despedida (sair da rádio)

`vinheta_tchauzinho.mp3`/`vinheta_ate_mais.mp3` em `res/raw` (fonte: `tchauzinho_1.mp3`/
`até mais edited.mp3` aqui) — tocam ao sair de uma rádio pelo botão "Sair da rádio",
alternando em sequência a cada uso (`nextExitVinhetaIndex`/`EXIT_VINHETA_RESOURCES` em
`LocalTuneViewModel.kt`, `playExitVinheta()`), volume cheio (1.0, igual à intro). Ver
ADR-027 em [docs/DECISIONS.md](../docs/DECISIONS.md).

Pra adicionar/trocar uma vinheta: solte o `.wav`/`.mp3` aqui de novo com um nome
descritivo e avise — eu movo para `res/raw` (nome de arquivo vira minúsculo,
`snake_case`, sem espaço/acento) e atualizo o mapa `VINHETA_BY_RADIO_KEY` no
ViewModel.

## Música de fundo do boletim

Diferente das vinhetas/passagens acima (não vão pro APK) - a música de fundo mora
**dentro do pacote de voz** (`voice-models/supertonic-3-int8/`, ver ADR-019 em
[DECISIONS.md](../docs/DECISIONS.md)), porque é misturada por baixo do WAV do boletim em
`LocalRadioVoiceEngine.mixBackgroundMusic()`, não tocada como arquivo separado. Fonte
atual: `Concrete Tunnel.mp3` e `Concrete Tunnel 2.mp3` aqui na pasta (autorais, sem
direitos do Epidemic Sound — trocado em 04/09/2026, as antigas `ES_Save It for a Rainy
Day - Margareta.mp3`/`ES_Devil Disguised (Instrumental Version) - Torii Wolf.mp3` foram
apagadas), convertidos pra `bed1.pcm`/`bed2.pcm` (PCM16 mono 44100Hz sem cabeçalho, via
`ffmpeg -ar 44100 -ac 1 -f s16le`) e alternados por boletim. `voice-models/
Pailer-Radio-Voices-Supertonic3-Fran-Nico.zip` já foi reempacotado com os beds novos —
falta só reimportar esse zip no app (Configurações > pacote de voz) pra valer no
aparelho. Pra trocar de novo: solte o `.mp3` aqui e avise — reconverto e reempacoto o
zip de voz.
