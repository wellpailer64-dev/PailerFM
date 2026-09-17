# Máquina de Estados do Boletim

> **Regra de ouro deste projeto:** preservar o comportamento atual antes de refatorar.
> Toda mudança estrutural mantém as funcionalidades existentes e é feita incrementalmente.
> O app já funciona e está em uso diário. Nada de "clean architecture deluxe" que mate a rádio no processo.

**16/09/2026:** redator local/Gemini, RSS (`NewsBulletinRepository`), síntese de voz local/
Gemini/TTS Android (`RadioVoiceSynthesisService`, processo `:radio_voice`) foram todos
removidos — ver ADR-035 em [DECISIONS.md](DECISIONS.md). Boletim hoje só vem pronto do
feed remoto (`BroadcastFeedRepository`). As races R5, R6, R7 e R9 abaixo (e a regra 5/7 da
Seção B) descrevem esse pipeline antigo e **não se aplicam mais** — mantidas como
histórico, marcadas como resolvidas por remoção. `ttsReady`/`newsBulletins` também não
existem mais como variáveis do ViewModel.

Este doc tem duas metas:

1. **Seção A** — registrar o comportamento implícito de hoje (vars soltas no
   `LocalTuneViewModel`) e as races conhecidas, para ninguém "descobrir" isso de novo;
2. **Seção B** — definir o contrato alvo da FSM que o `RadioBulletinController`
   vai implementar. **Esta seção é a spec de implementação** do próximo passo
   ([TODO.md](TODO.md) P1).

---

## Seção A — Comportamento atual

### Variáveis implícitas (`LocalTuneViewModel`)

| Variável | Papel | Quem muta |
|---|---|---|
| `radioNewsEnabled` | modo rádio ativo (boletins ligados) | `startRadioNewsMode`, `stopRadioNewsMode` |
| `completedRadioSongs` | contador p/ intervalo entre boletins | `onMediaItemTransition`, resets |
| `bulletinBuffer` | boletins baixados do feed remoto, prontos pra tocar | `refillBulletinBuffer` (async) |
| `speakingNews` | boletim em andamento | `speakNextNewsBreak`, `finishNewsBreak` |
| `currentNewsHeadline` / intro text | texto exibido na UI | vários |
| `resumeAfterNews` | "estava tocando antes da fala" | capturado no início da fala, consumido no fim |
| `pendingRadioIntro` | intro da rádio pendente/emitindo | `speakRadioIntro`, `finishRadioIntro` |
| `announcementPlayer` | `MediaPlayer` do anúncio local | `playAnnouncementFile`, `stopRadioNewsMode` |

`newsBulletins`/`nextBulletinIndex`/`ttsReady`/`ttsRequested` existiam aqui até
16/09/2026 (RSS + TTS legado) — removidos junto do redator/voz local, ver ADR-035.

Nenhuma dessas variáveis é protegida por lock ou token: callbacks chegam da main thread,
do binder thread do `ResultReceiver` e das threads do `MediaPlayer`/TTS sem coordenação.

### Races conhecidas (catalogadas em 2026-08)

| # | Sintoma | Causa raiz |
|---|---|---|
| R1 | Pausa manual durante o boletim é anulada: a música volta sozinha quando a fala termina | `finishNewsBreak()` retoma se `resumeAfterNews`, capturado antes e nunca revalidado |
| R2 | Play/pause pelo widget/notificação durante a locução faz a música tocar por cima da fala | controles continuam agindo no player; nada informa ao fluxo do boletim |
| R3 | Skip durante geração/fala: o boletim da música que já passou toca depois | skip não cancela job nem invalida resultado; transição manual também não conta para o intervalo |
| R4 | WAVs órfãos em `filesDir/radio_bulletins_ready` | download do feed remoto ou morte do app antes do item ser registrado no buffer; coberto hoje por `ORPHAN_CLEANUP_GRACE_MS` em `saveCoreBufferManifest()` |
| ~~R5~~ | ~~Dois engines TTS na RAM simultaneamente~~ | **resolvida por remoção (16/09/2026):** processo `:radio_voice`/`RadioVoiceSynthesisService` não existe mais |
| ~~R6~~ | ~~Intro atrasa até ~1,8 s mesmo com sherpa ativo~~ | **resolvida por remoção:** `ttsReady`/TTS legado removidos |
| ~~R7~~ | ~~Sem TTS do sistema funcional, rádio perde intro/boletim~~ | **resolvida por remoção:** sem TTS do sistema no fluxo automático - buffer vazio só cancela a entrada, nunca bloqueia intro |
| R8 | Música fica pausada para sempre se a fala travar; Media3 rebaixa o serviço e o sistema mata o processo | nenhum caminho garantia chamada a `finishNewsBreak()`/`finishRadioIntro()` |
| ~~R9~~ | ~~Boletim nunca mais toca na sessão~~ | **resolvida por remoção:** não existe mais `newsBulletins`/RSS; feed remoto é reconsultado a cada `refillBulletinBuffer()`, sem estado "carregado uma vez" que possa ficar vazio pra sempre |

**Mitigação atual (ADR-009 em [DECISIONS.md](DECISIONS.md)):** watchdog de 90 s com
token por anúncio força a retomada quando R8 acontece. Correção de raiz é o controller
da Seção B.

**Mitigação de R9 (29/08/2026):** `speakNextNewsBreak()` e `prepareUpcomingBulletin()`
agora logam (`PailerRadioVoice`) e chamam `reloadNewsBulletinsIfNeeded()` quando
`newsBulletins` está vazio, em vez de desistir pro resto da sessão. Não resolve a causa
(feeds indisponíveis continuam indisponíveis), mas recupera sozinho assim que a rede
volta e dá rastro pra diagnosticar via logcat.

---

## Seção B — Contrato alvo (spec do `RadioBulletinController`)

FSM pequena de propósito — três estados, um token de sessão, regras explícitas.

### Estados

```
        StartRadio                SynthesisOk(file) / TtsStarted
 Idle ──────────────► Generating ───────────────────────────► Speaking
  ▲ ▲                    │                                        │
  │ │      UserSkip/UserPause/StopRadio (cancela job + descarta WAV)
  │ │                    ▼                                        │
  │ └────── SynthesisFail / erro ◄── PlaybackFinished ────────────┘
  │                              (retoma música se permitido)
  └──────────────────── StopRadio
```

- `Idle` — rádio não iniciada ou encerrada.
- `Generating(kind: INTRO | BULLETIN)` — síntese em andamento; música tocando.
- `Speaking(kind)` — áudio do anúncio em reprodução; música pausada.

### Eventos

`StartRadio(name)` · `SongTransition(reason: AUTO | MANUAL)` · `UserPlay` ·
`UserPause` · `UserSkip` · `SynthesisOk(token, file)` · `SynthesisFail(token, reason)`
· `PlaybackFinished(file)` · `StopRadio`

### Regras invioláveis

1. **Token de sessão:** todo `StartRadio` gera um novo `generationId`. Todo request de
   síntese carrega o token atual. Resultado com token antigo → **descartar + deletar WAV**,
   sem tocar, sem mudar estado.
2. **Todo evento de usuário invalida:** `UserPause`/`UserSkip`/`UserPlay`/`StopRadio`
   durante `Generating` cancelam a síntese pendente (cooperativamente) e marcam o
   resultado como morto (regra 1 cobre o caso de o serviço ainda entregar o arquivo).
3. **Resume condicional:** ao sair de `Speaking`, retoma a música somente se
   `(a)` estava tocando quando a fala começou **e** `(b)` nenhum evento de usuário
   ocorreu desde então. Caso contrário fica pausado.
4. **Contagem só com transição AUTO** e apenas fora de `Speaking`.
5. **Requests serializados:** fila única no processo `:radio_voice` — nunca dois engines.
6. **WAV sempre rastreável:** nome derivado do token (`radio_voice_<token>.wav`);
   dono único = controller; limpeza de órfãos ao iniciar sessão e ao receber
   `SynthesisOk` com token morto.
7. **Caminho sherpa independente do TTS legado:** ausência/falha do TTS do sistema não
   bloqueia síntese local (mata R6/R7).

### Tabela estado × evento

| Estado \ Evento | UserPause | UserSkip | SongTransition(AUTO) | SynthesisOk | SynthesisFail | PlaybackFinished |
|---|---|---|---|---|---|---|
| **Idle** | — | — | — | descarta+deleta | ignora | — |
| **Generating** | pausa música, marca resume=off, invalida token | idem + next song | incrementa contador (pode disparar novo boletim) | se token atual → `Speaking`; senão descarta+deleta | volta a tocar se estava | — |
| **Speaking** | mata anúncio (release/TTS.stop), fica pausado | mata anúncio + deleta WAV + next song | ignorado | — | — | deleta WAV; retoma só se regra 3 permitir |

`StopRadio` em qualquer estado: cancela tudo, deleta pendências, volta a `Idle`.

### Não-objetivos (por enquanto)

- Não mover a reprodução musical para dentro do controller — ExoPlayer continua
  sendo operado via `MediaController` como hoje;
- Não criar FSM genérica/reutilizável; três estados bastam;
- Implementação incremental: primeiro serialização + token (mata R1–R5), depois
  desacoplamento do TTS legado (R6/R7).
