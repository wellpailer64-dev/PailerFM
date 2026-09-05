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
(`buildRadioQueue`) pra rádio personalizada **de fonte única** — esse algoritmo assume
muitos artistas diferentes e cortaria uma rádio de artista/álbum único para poucas faixas
(`radioArtistLimit` pra 1 artista). Álbum de artista único toca na ordem de faixa
(sequência proposital, tipo álbum conceitual); álbum "various artists" (várias faixas com
artistas diferentes sob o mesmo nome de álbum — `LocalAlbum.isVariousArtists`, ver
ADR sobre capa por-faixa em DECISIONS.md) não é uma sequência intencional de verdade,
então embaralha igual rádio de artista (`shuffledRadioSession()`), com anti-repetição
contra a última sessão salva.

**Adicionar mais fontes a uma rádio personalizada (ADR-017):** botão "+" na tela da rádio
(só nas personalizadas) abre um seletor de artista/álbum e chama
`MusicLibraryRepository.addSourceToCustomRadio()`, que acrescenta o novo
`sourceId` (mesmo formato prefixado de `CustomRadioDefinition.id`) em
`extraSourceIds`. A partir da segunda fonte (`LocalRadio.hasMultipleSources = true`), a
rádio deixa de tocar em ordem de faixa/shuffle simples e cai no `buildRadioQueue` genérico
(mesmo caminho de rádio de categoria) — misturar fontes diferentes só faz sentido
embaralhado.

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
  │    ├─ zera contadores/flags do boletim, esvazia bulletinBuffer
  │    ├─ setupTextToSpeech()               (fallback; ver TTS.md)
  │    └─ carrega boletins em background:
  │         RadioBulletinRepository.loadScripts() → refillBulletinBuffer()
  │         (enche o buffer pra BULLETIN_BUFFER_TARGET=3 itens prontos, ver ADR-019)
  ├─ controller.setMediaItems + prepare      (autoPlay=false se vai tocar vinheta)
  └─ playRadioVinhetas(radioName)            (só quando startIndex == 0, entrada nova)
        ├─ playVinhetaResource(R.raw.radio_intro)
        ├─ complemento por rádio (VINHETA_BY_RADIO_KEY[normalizeRadioKey(radioName)]), se houver
        └─ finishVinhetas() → controller.play()

        ▼  (sessão rodando)
onMediaItemTransition(reason = AUTO)        [Player.Listener]
  ├─ completedRadioSongs += 1
  ├─ a cada settings.songsBetweenBulletins (padrão 3):
  │    speakNextNewsBreak()
  │      ├─ bulletinBuffer.removeFirstOrNull() → refillBulletinBuffer() (repõe já)
  │      ├─ item do buffer pronto: usa script+WAV já preparados
  │      │  (buffer vazio: monta boletim ao vivo, sem redator local, timeout 12 s)
  │      ├─ resumeAfterNews = player.isPlaying; player.pause()
  │      ├─ playPassagem() → MediaPlayer toca o WAV do boletim (cacheDir/radio_voice_<ts>.wav)
  │      │  → playPassagem() de novo → finishNewsBreak() → retoma playback
  └─ refillBulletinBuffer()                  (nudge idempotente, no-op se já cheio)
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
| `OptionalLocalLlmRadioScriptWriter` | modo Dialogue + pacote LLM instalado (`filesDir/radio_writer/model.ready`) | Usa Qwen3 1.7B GGUF via `llama.cpp` para reescrever o próximo boletim em JSON curto; qualquer falha, demora ou JSON inválido cai no fallback. |
| `FallbackRadioScriptWriter` | sempre disponível | Bate-bola entre Fran (otimista) e Nico (pessimista), com abertura citando a última música, resumo da matéria pelo Nico, provocação/contra provocação, reflexão existencialista/absurdista do Nico e chamada da próxima música com curiosidade — ver ADR-014 — ou headline curta (2 falas, só Fran). |

Modo é sempre `Dialogue` (diálogo completo, tenta redator local primeiro) — a seleção de
modo (`Off`/`Headlines`/`Dialogue`) foi removida da UI (tela "Boletins da radio"); o enum
`RadioBulletinMode` continua existindo em `RadioBulletin.kt` mas `Off`/`Headlines` não são
mais alcançáveis por preferência do usuário.

O pacote do redator local é importado pela tela de boletins. Ele fica fora do APK por
tamanho: o pacote recomendado é `dist/Pailer-Radio-Writer-Qwen3-1.7B-Q4KM-v1.zip`
(~1,03 GB), com `manifest.json` + `Qwen3-1.7B-Q4_K_M.gguf`.

### Bate-bola Fran/Nico (ADR-014)

`FallbackRadioScriptWriter.buildDialogueLines()` classifica o tema da notícia (título +
resumo, por palavra-chave — política, economia, ciência/tecnologia, saúde, cultura pop,
esporte, clima, curiosidade, mundo/conflito ou geral) e monta as falas com bancos de
texto próprios por tema para cada personagem, em vez de reações genéricas soltas.
O Nico agora resume ou explica a matéria antes de criticar, usando o resumo real do RSS
quando existe. A opinião dos dois parte de uma leitura de mundo mais forte: capitalismo
tardio, jogo imperialista, corporativismo, lobby, indústria cultural, plataformas e
mercado financeiro aparecem como bagagem cultural, não como bordão repetido em toda fala.
Extrai também um "gancho" (primeiro percentual, valor em R$ ou número grande do texto)
para referenciar algo concreto da matéria. O bate-bola em si (`buildDialogueLines`) termina
na contra-provocação do Nico — a "chamada de volta pra rádio" não faz mais parte dessa
função, ver fechamento filosófico abaixo.

### Fechamento filosófico + chamada de música (`withPhilosophicalCloser`)

Depois que o roteiro-base (fallback ou redator local) é gerado, `LocalTuneViewModel` cola
1 ou 2 falas novas via `RadioScript.withPhilosophicalCloser()` (`RadioBulletin.kt`),
aplicada **depois** da geração — igual `withLastPlayedIntro` — porque só se sabe qual é a
próxima faixa da fila em tempo de reprodução, nunca em `loadScripts()`:

1. **Nico** traz uma reflexão existencialista/absurdista. Desde 02/09/2026 (ver ADR-002,
   atualização), quando o roteiro veio do **redator local** o comentário já vem pronto
   como a 5ª fala pedida em `buildPrompt()` — gerado em cima da matéria específica, não
   sorteado — e `withPhilosophicalCloser` só reaproveita essa fala. Só sorteia do banco
   fixo (`NICO_REFLECTIONS_LIGHT`/`NICO_REFLECTIONS_DEEP`, por peso conforme
   `RadioScript.duration`) quando o roteiro veio do fallback determinístico ou o LLM não
   entregou a 5ª fala dessa vez — nesse caso continua Camus/Sartre/Nietzsche/Beckett (só
   citação literal segura) ou Kafka/Cioran (por tema, não por citação).
2. **Fran** reage e chama a próxima música, citando o artista (resolvido espiando a
   fila real do `Player`, `getMediaItemAt`) e uma curiosidade **sempre genérica de
   gênero/época** (nunca específica do artista — decisão deliberada: o redator local é
   pequeno demais pra arriscar inventar dado sobre banda pouco conhecida da biblioteca,
   mesma regra de "não invente fatos" que já vale pras notícias). Cadeia gênero → época →
   genérico em `musicTrivia()`. Sem faixa seguinte conhecida, degrada pra frase genérica
   sem citar artista.

Aplicado nos dois pontos de chamada de `withLastPlayedIntro` (pré-síntese e fallback ao
vivo) — essencial pra o áudio pré-sintetizado e o fallback de texto ficarem consistentes.
Só roda no modo `Dialogue` (não no `Headlines`).

Na hora de tocar ou preparar o boletim, `LocalTuneViewModel` injeta a última faixa ouvida
na primeira fala: "Você acaba de ouvir X, de Y, e vamos às notícias." Isso acontece só no
contexto de reprodução, porque os roteiros-base são carregados quando a rádio começa e a
música anterior só é conhecida no intervalo.

Para não travar a entrada da rádio, `loadScripts()` continua carregando roteiros-base via
fallback determinístico. O redator local entra em `refillBulletinBuffer()` (ADR-019),
preparando **até 3 boletins com antecedência** em segundo plano (um de cada vez, nunca
mais de um motor de voz carregado ao mesmo tempo) em vez de só o próximo. Cada versão
gerada fica cacheada como texto e, se a voz local estiver ligada, também como áudio, num
buffer (`bulletinBuffer`) reposto assim que um item é consumido. Assim o app evita gerar
oito notícias de uma vez, mas também não fica refém de "só 1 música de antecedência" -
qualquer soluço pontual de síntese tem folga de até 3 boletins pra se resolver antes de
faltar áudio pronto.

Regra de segurança em produção: depois que a música pausa, o app não chama mais o redator
local nem tenta sintetizar voz local pesada se o WAV não estava pronto. Se o roteiro/áudio
preparado não chegou a tempo, o boletim entra imediatamente com o roteiro-base e TTS do
Android. A voz local é ganho de qualidade quando chega antes do intervalo, não dependência
para a rádio continuar falando.

O botão de teste de boletim também usa o caminho real: busca uma notícia RSS no momento,
monta o roteiro, aplica o redator local se estiver disponível, sintetiza e toca o resultado.
Não usa mais um texto fixo de demonstração.

Diferença chave da versão antiga: o número de falas é decidido **pela duração**, não
cortado depois por `fitFor()` — antes um script fixo de 3 falas podia perder a última
inteira se estourasse o limite de palavras, quebrando a participação igual dos dois:

| Duração | Falas (base + fechamento) | Estrutura |
|---|---|---|
| Short | 4 + 2 | última música + manchete da Fran → Nico resume/explica com leitura crítica → Fran provoca sem ingenuidade → Nico contra provoca → reflexão do Nico → Fran chama a próxima música com curiosidade |
| Normal | 4 + 2 | mesma estrutura, com mais margem de palavras para resumo e comentário |
| Long | 5 + 2 | + uma fala extra do Nico contextualizando consequência/gancho antes da provocação |

`fitFor()` continua como rede de segurança (apara palavras se algum banco de texto sair
grande), mas não deve mais precisar cortar linha inteira em uso normal.

Duração → limite de palavras aplicado por `fitFor()`:

| Duração | Segundos alvo | Máx. palavras |
|---|---|---|
| Short | 20 | 155 |
| Normal | 30 | 190 |
| Long | 45 | 235 |

Cada fala vira uma linha `RadioScriptLine(speaker, text)` — o speaker define qual voz
do pacote sintetiza aquela linha (ver [TTS.md](TTS.md)). `RadioSpeaker.Female` = Fran
(voz Supertonic F2), `RadioSpeaker.Male` = Nico (voz Supertonic M1) — ver ADR-018. O
`fitFor()` acima só se aplica às falas base; as 2 falas do fechamento filosófico
(`withPhilosophicalCloser`) têm seu próprio limite de palavras e não contam nesse
orçamento.

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
- Anúncio fallback (boletim sem WAV local pronto): TTS do sistema com `QUEUE_FLUSH`; vinhetas não
  têm fallback de TTS — se o `MediaPlayer` falhar, pula direto pra música;
- Widgets/notificação continuam operando o player de música normalmente — é daí que
  nascem as races de "música por cima da locução" (R2 em [STATE_MACHINE.md](STATE_MACHINE.md)).
