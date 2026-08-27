# Pipeline da Rádio

> **Regra de ouro deste projeto:** preservar o comportamento atual antes de refatorar.
> Toda mudança estrutural mantém as funcionalidades existentes e é feita incrementalmente.
> O app já funciona e está em uso diário. Nada de "clean architecture deluxe" que mate a rádio no processo.

Este documento descreve **como a rádio funciona hoje**, de ponta a ponta: o que dispara cada
fala, quem gera, onde cacheia, quem reproduz e quem libera. As races conhecidas estão
catalogadas em [STATE_MACHINE.md](STATE_MACHINE.md).

## O conceito de "Rádio"

Não existe stream de rede: uma rádio é uma **fila derivada da biblioteca local** com
identidade própria (`MusicLibraryRepository.radiosFrom`):

- Perfil fixo **Grunge** (se houver músicas que batem no perfil);
- Rádios dinâmicas por gênero (até `MAX_HOME_RADIOS = 10`);
- Fallback **"Rádio recente"** (30 faixas mais novas, `RADIO_LIMIT = 30`).

`radioSessionFrom(radio)` monta a fila com **anti-repetição**: gera até algumas tentativas
com seeds aleatórias e escolhe a que menos se parece com a última sessão tocada
(similaridade de sequência), persistindo a nova ordem em `metadata_overrides`.

## Fluxo completo

```
Usuário toca numa Rádio
        │
        ▼
playRadioSession()                          [LocalTuneViewModel]
  ├─ radioSessionFrom() → fila anti-repetição
  ├─ startRadioNewsMode(radioName)
  │    ├─ zera contadores/flags do boletim
  │    ├─ setupTextToSpeech()               (fallback; ver TTS.md)
  │    └─ carrega boletins em background:
  │         RadioBulletinRepository.loadScripts()
  ├─ controller.setMediaItems + prepare      (autoPlay=false se vai tocar vinheta)
  └─ playRadioVinhetas(radioName)            (só quando startIndex == 0, entrada nova)
        ├─ playVinhetaResource(R.raw.radio_intro)
        ├─ complemento por rádio (VINHETA_BY_RADIO_KEY[normalizeRadioKey(radioName)]), se houver
        └─ finishVinhetas() → controller.play()

        ▼  (sessão rodando)
onMediaItemTransition(reason = AUTO)        [Player.Listener]
  ├─ completedRadioSongs += 1
  └─ a cada settings.songsBetweenBulletins (padrão 3):
       speakNextNewsBreak()
         ├─ pega próximo script da lista (índice circular)
         ├─ resumeAfterNews = player.isPlaying; player.pause()
         ├─ síntese local (sherpa, timeout 12 s) OU TTS do sistema
         ├─ MediaPlayer toca o WAV (cacheDir/radio_voice_<timestamp>.wav)
         └─ finishNewsBreak() → retoma playback se resumeAfterNews
```

Gatilho importante: boletins só contam em transição **AUTO** (música acabou sozinha).
Skip manual não conta nem cancela nada — ver races R3/R6 em [STATE_MACHINE.md](STATE_MACHINE.md).

> **Abertura falada → vinhetas gravadas (26/08/2026 — ADR-010, ADR-011):** a rádio não
> fala mais data/hora/clima. No lugar, toca uma vinheta gravada
> (`app/src/main/res/raw/radio_intro.mp3` + complemento específico da rádio, se houver —
> mapa completo em [`vinhetas/README.md`](../vinhetas/README.md)) e só então entra a
> música. Só acontece na entrada nova (`startIndex == 0`, botão "Entrar"); pular pra uma
> faixa específica da sessão ao vivo não replay a vinheta.

## Fontes de notícia

`NewsBulletinRepository.loadStories()`:

- Feeds RSS/Atom do **g1**: Política, Economia, Mundo, Ciência e saúde;
- Até 4 manchetes por feed, dedup por título, limite total de 8;
- Timeout de rede 4,5 s; feed que falhar é ignorado silenciosamente;
- Limpeza de HTML/entities nos títulos.

## Escritores de roteiro (`RadioBulletin.kt`)

Interface `RadioScriptWriter` com duas implementações:

| Escritor | Quando é usado | Comportamento |
|---|---|---|
| `OptionalLocalLlmRadioScriptWriter` | modo Dialogue + pacote LLM instalado (`filesDir/radio_writer/model.ready`) | **Ainda não implementado** — lança erro; o chamador cai no fallback via `runCatching`. Ponto de extensão futuro. |
| `FallbackRadioScriptWriter` | sempre disponível | Diálogo fixo de 3 falas (locutora → locutor → locutora) ou headline curta (2 falas). |

Modos do usuário (`RadioBulletinMode`): `Off`, `Headlines` (só manchete), `Dialogue`
(diálogo completo, tenta redator local primeiro).

Duração → limite de palavras aplicado por `fitFor()`:

| Duração | Segundos alvo | Máx. palavras |
|---|---|---|
| Short | 20 | 55 |
| Normal | 30 | 80 |
| Long | 45 | 115 |

Cada fala vira uma linha `RadioScriptLine(speaker, text)` — o speaker define qual voz
do pacote sintetiza aquela linha (ver [TTS.md](TTS.md)).

## Arquivos temporários e limpeza

- WAVs de anúncio: `cacheDir/radio_voice_<timestamp>.wav`;
- Deletados em `onCompletion`, `onError` ou falha de preparo do `MediaPlayer`;
- **Órfãos conhecidos:** se o ViewModel estourar o timeout de 12 s (`withTimeoutOrNull`)
  ou for destruído antes do callback, o serviço ainda escreve o WAV e ninguém deleta.
  Correção planejada — ver [TODO.md](TODO.md).

## Quem manda no áudio durante um anúncio

- Música: pausada explicitamente antes da fala (`player.pause()`), retomada depois —
  no caso da vinheta de entrada, a música nem começa (`autoPlay=false`) até ela acabar;
- Anúncio local (sherpa) e vinhetas gravadas dividem o mesmo `MediaPlayer` dedicado
  (`announcementPlayer`) e o mesmo watchdog de 90 s — um por vez, release do anterior
  antes do novo;
- Anúncio fallback (boletim sem sherpa): TTS do sistema com `QUEUE_FLUSH`; vinhetas não
  têm fallback de TTS — se o `MediaPlayer` falhar, pula direto pra música;
- Widgets/notificação continuam operando o player de música normalmente — é daí que
  nascem as races de "música por cima da locução" (R2 em [STATE_MACHINE.md](STATE_MACHINE.md)).
