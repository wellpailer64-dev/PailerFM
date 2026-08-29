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

- Rádios personalizadas do usuário (album/artista - ver seção própria abaixo);
- Perfis fixos **Grunge** e **Anos 2000** (se houver músicas que batem no perfil -
  `RadioProfile.yearRange` pro segundo, lê `MediaStore.Audio.Media.YEAR`);
- Rádios dinâmicas por gênero (até `MAX_HOME_RADIOS = 10`);
- Fallback **"Rádio recente"** (30 faixas mais novas, `RADIO_LIMIT = 30`).

## Rádios personalizadas (album/artista)

Usuário cria uma rádio a partir de um álbum ou artista específico (botão "Criar rádio
deste álbum/artista" nas telas de detalhe). Persistida em
`MusicLibraryRepository.CustomRadioDefinition` (SharedPreferences `metadata_overrides`,
chave `custom_radio_definitions`, JSON array) e resolvida contra a biblioteca atual a
cada `radiosFrom()` — cresce sozinha se o usuário adicionar mais faixas daquele
artista, fixa se for álbum (tracklist não muda). Aparece primeiro na lista de rádios
(`LocalRadio.isCustom = true`), com botão de excluir (ícone de lixeira) só nela — as
músicas continuam no aparelho, só a definição da rádio é removida.

**Importante:** `radioSessionFrom()` pula o algoritmo de diversidade de artista
(`buildRadioQueue`) pra rádio personalizada — esse algoritmo assume muitos artistas
diferentes e cortaria uma rádio de artista/álbum único para poucas faixas
(`radioArtistLimit` pra 1 artista). Álbum de artista único toca na ordem de faixa
(sequência proposital, tipo álbum conceitual); álbum "various artists" (várias faixas com
artistas diferentes sob o mesmo nome de álbum — `LocalAlbum.isVariousArtists`, ver
ADR sobre capa por-faixa em DECISIONS.md) não é uma sequência intencional de verdade,
então embaralha igual rádio de artista (`shuffledRadioSession()`), com anti-repetição
contra a última sessão salva.

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

`NewsBulletinRepository.loadStories()` (ver ADR-014):

- 5 feeds RSS/Atom buscados **em paralelo** (`coroutineScope` + `async`/`awaitAll` —
  sequencial custava a soma dos timeouts): g1 Mundo, g1 Ciência e saúde, Super (Abril,
  curiosidades/ciência/história), Olhar Digital (tecnologia/novidades), BBC Brasil
  (mundo, ângulo diferente do g1) — metade g1, metade fora, de propósito (o boletim
  saía repetitivo demais só com g1);
- Até 4 manchetes por feed, dedup por título, limite total de 8;
- Captura título **e resumo** (`<description>`/`<summary>`, até 220 caracteres,
  HTML/entities limpos) — o resumo alimenta o roteirista com conteúdo de verdade da
  matéria, não só a manchete;
- Timeout de rede 4,5 s por feed; feed que falhar é ignorado silenciosamente.

## Escritores de roteiro (`RadioBulletin.kt`)

Interface `RadioScriptWriter` com duas implementações:

| Escritor | Quando é usado | Comportamento |
|---|---|---|
| `OptionalLocalLlmRadioScriptWriter` | modo Dialogue + pacote LLM instalado (`filesDir/radio_writer/model.ready`) | **Ainda não implementado** — lança erro; o chamador cai no fallback via `runCatching`. Ponto de extensão futuro. |
| `FallbackRadioScriptWriter` | sempre disponível | Bate-bola entre Frankie (otimista) e Nicky (pessimista) — ver ADR-014 — ou headline curta (2 falas, só Frankie). |

Modos do usuário (`RadioBulletinMode`): `Off`, `Headlines` (só manchete), `Dialogue`
(diálogo completo, tenta redator local primeiro).

### Bate-bola Frankie/Nicky (ADR-014)

`FallbackRadioScriptWriter.buildDialogueLines()` classifica o tema da notícia (título +
resumo, por palavra-chave — política, economia, ciência/tecnologia, saúde, cultura pop,
esporte, clima, curiosidade, mundo/conflito ou geral) e monta as falas com bancos de
texto próprios por tema para cada personagem, em vez de reações genéricas soltas.
Extrai também um "gancho" (primeiro percentual, valor em R$ ou número grande do texto)
pra referenciar algo concreto da matéria.

Diferença chave da versão antiga: o número de falas é decidido **pela duração**, não
cortado depois por `fitFor()` — antes um script fixo de 3 falas podia perder a última
inteira se estourasse o limite de palavras, quebrando a participação igual dos dois:

| Duração | Falas | Estrutura |
|---|---|---|
| Short | 2 (1 cada) | Frankie parafraseia a manchete pro Nicky → Nicky reage com opinião do tema |
| Normal | 4 (2 cada) | + Frankie contra-argumenta (otimista) → fecha (Frankie ou Nicky, alterna por hash do título) |
| Long | 6 (3 cada) | + gancho/punchline do Nicky → punchline otimista do Frankie → fechamento |

`fitFor()` continua como rede de segurança (apara palavras se algum banco de texto sair
grande), mas não deve mais precisar cortar linha inteira em uso normal.

Duração → limite de palavras aplicado por `fitFor()`:

| Duração | Segundos alvo | Máx. palavras |
|---|---|---|
| Short | 20 | 55 |
| Normal | 30 | 80 |
| Long | 45 | 115 |

Cada fala vira uma linha `RadioScriptLine(speaker, text)` — o speaker define qual voz
do pacote sintetiza aquela linha (ver [TTS.md](TTS.md)). Os enums `RadioSpeaker.Female`/
`Male` são só nomes de slot herdados do pacote de voz — hoje os dois carregam vozes
masculinas (Frankie no slot Female, Nicky no slot Male).

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
