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

Pra adicionar/trocar uma vinheta: solte o `.wav`/`.mp3` aqui de novo com um nome
descritivo e avise — eu movo para `res/raw` (nome de arquivo vira minúsculo,
`snake_case`, sem espaço/acento) e atualizo o mapa `VINHETA_BY_RADIO_KEY` no
ViewModel.
