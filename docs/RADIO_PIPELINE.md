# Pipeline da Rádio

> **Regra de ouro deste projeto:** preservar o comportamento atual antes de refatorar.
> Toda mudança estrutural mantém as funcionalidades existentes e é feita incrementalmente.
> O app já funciona e está em uso diário. Nada de "clean architecture deluxe" que mate a rádio no processo.

Este documento descreve **como a rádio funciona hoje**, de ponta a ponta: o que dispara cada
fala, quem gera, onde cacheia, quem reproduz e quem libera. As races conhecidas estão
catalogadas em [STATE_MACHINE.md](STATE_MACHINE.md).

**16/09/2026 — mudança grande:** o redator local (Qwen3/llama.cpp), o redator via Gemini,
a busca de RSS, a síntese de voz local (Supertonic/sherpa-onnx), a Gemini Flash TTS e o
fallback de TTS do Android foram todos **removidos**. Todo boletim hoje vem pronto
(roteiro + áudio) do feed remoto publicado pela central de broadcast externa — ver seção
"Feed remoto de boletins aprovados" abaixo e ADR-034/ADR-035 em
[DECISIONS.md](DECISIONS.md). As seções deste documento que descreviam esse pipeline
antigo foram removidas; o que resta abaixo já reflete o app pós-remoção.

Para a central de broadcast externa (fora deste app) que escreve/sintetiza os boletins,
com nomes de arquivos e metadata estruturada, ver
[BROADCAST_METADATA.md](BROADCAST_METADATA.md) e
[BROADCAST_LOCAL_WORKBENCH.md](BROADCAST_LOCAL_WORKBENCH.md).

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
  │    ├─ zera contadores/flags do boletim (bulletinBuffer NAO e tocado - unico, 100%
  │    │  radio-agnostico, sobrevive a troca de radio, ver ADR-024)
  │    └─ refillBulletinBuffer()
  │         (no-op se ja estiver cheio - o buffer roda desde a abertura do app,
  │         independente de radio ativa; enche pra BULLETIN_BUFFER_TARGET=10 itens
  │         baixados do feed remoto, ver ADR-024/ADR-035)
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
  │      ├─ dequeueBufferedBulletinForPlayback() → pega o 1º item com WAV tocável
  │      │  (itens sem áudio são pulados e descartados)
  │      ├─ protege temporariamente o nome do WAV escolhido contra a limpeza de órfãos
  │      ├─ refillBulletinBuffer() (repõe já)
  │      ├─ buffer vazio ou sem WAV tocável → cancela a entrada, música segue (sem
  │      │  alternativa nenhuma - ver "Feed remoto" abaixo)
  │      ├─ resumeAfterNews = player.isPlaying; player.pause()
  │      ├─ playPassagem() → MediaPlayer toca o WAV do buffer (filesDir/radio_bulletins_ready/radio_core_*.wav)
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

## Feed remoto de boletins aprovados (Cloudflare) — única fonte

Cada vaga livre de `refillBulletinBuffer()` baixa o próximo boletim já aprovado na
central local de broadcast (`_broadcast-boletins-local/`, processo separado deste app) e
publicado no Cloudflare (`BroadcastFeedRepository`, ver ADR-034/ADR-035 em
[DECISIONS.md](DECISIONS.md)). Esse boletim já vem com roteiro e voz prontos da central —
o app só baixa `.wav` + roteiro, confere tamanho/hash e entra direto no `bulletinBuffer`
(`RadioScriptSource.BroadcastFeed`). Dedup contra boletins recentes via
`newsReservationKey()` (título+fonte normalizados), a mesma chave usada internamente pelo
buffer — a lógica de normalização em `BroadcastFeedRepository` precisa continuar
reproduzindo essa fórmula exatamente (ver ADR-035, bug de dedup corrigido 16/09/2026).

**Dedup tem um fallback pra quando o pool do feed é menor que o histórico (ADR-041,
18/09/2026):** `recentBulletinStoryKeys` guarda as últimas `RECENT_BULLETIN_STORY_KEY_LIMIT`
= 80 chaves tocadas; o feed publicado costuma ter uma dúzia ou duas de itens aprovados por
vez. Assim que o app cobre o pool inteiro do feed, toda chave do manifest cai dentro desse
histórico e a rodada normal (`reservedKeys`) nunca mais acha nada — sem esse fallback, o
buffer ficava vazio pra sempre e a rádio parava de anunciar boletim, em silêncio (bug real,
visto ao vivo). Se a rodada normal não devolve nada mas o manifest TINHA candidatos
válidos, `downloadNextApprovedBulletinBlocking()` tenta de novo ignorando o histórico —
preferindo repetir um boletim antigo a nunca mais tocar nenhum. Não roda em `specialOnly`
(o "especial" é aviso avulso, não faz sentido repetir sozinho).

**Esse fallback gira o pool de forma justa (ADR-045, 22/09/2026):** os candidatos são
tentados em ordem embaralhada (`candidates.shuffled()`), SEM a prioridade que o boletim
"especial" tem no caminho normal (ver ADR-037 em DECISIONS.md — `content_type ==
"especial"` fura fila de conteúdo NOVO) — sem isso, um especial que tinha acabado de
tocar (e por isso já não estava mais no `bulletinBuffer`) vencia esse sorteio de novo
toda vez que uma vaga abria, travando a rádio só nele (bug real, visto ao vivo: "só está
tocando 1 notícia repetidas vezes"). `fallbackReservedKeys` também não é mais só
`currentBulletinBufferKeys()` — é `currentFallbackBulletinReservedKeys()`, que soma os
últimos `BULLETIN_FALLBACK_AVOID_RECENT_COUNT` (3) itens de `recentBulletinStoryKeys`,
travando a repetição imediata do que acabou de tocar sem impedir repetir algo mais antigo
quando o pool for mesmo pequeno.

**Sem alternativa quando o feed está genuinamente vazio ou fora do ar:** se o manifest não
tem NENHUM candidato aprovado/não-vencido (nem pro fallback acima), ou o WAV baixado não é
tocável, ou a rede está fora do ar, a vaga do buffer fica vazia e, na hora do intervalo,
`speakNextNewsBreak()` simplesmente cancela a entrada e a música segue — sem RSS, sem
redator local/Gemini, sem síntese de voz local/Gemini, sem TTS do Android (tudo isso foi
removido em 16/09/2026; ver ADR de remoção em DECISIONS.md). Qualquer falha em
`BroadcastFeedRepository` é capturada e tratada como "sem boletim novo".

`RadioScript`/`RadioScriptLine`/`RadioSpeaker`/`RadioScriptSource` (em `RadioBulletin.kt`)
continuam existindo como os tipos de dados do roteiro — só não há mais nenhum código
neste app que ESCREVA um `RadioScript` do zero; `BroadcastFeedRepository` monta o objeto a
partir do JSON publicado pela central.

## Arquivos temporários e limpeza

- WAVs persistentes do buffer (baixados do feed remoto): `filesDir/radio_bulletins_ready/
  broadcast_<id>.wav`;
- Quando um boletim sai do buffer para tocar, o nome do arquivo fica em
  `protectedBulletinPlaybackFileName` até o `MediaPlayer` concluir; isso impede que
  `saveCoreBufferManifest()` trate o WAV recém-selecionado como órfão só porque ele já
  saiu da fila.
- WAV de boletim automático é deletado no `onCompletion`. Em erro do `MediaPlayer`, o app
  tenta devolver o item para a frente do buffer se o arquivo ainda for tocável, para não
  perder um download já feito.
- Limpeza de órfãos (`saveCoreBufferManifest()`) roda a cada mudança do buffer, com
  `ORPHAN_CLEANUP_GRACE_MS` (1 min, reduzido de 5 min em 16/09/2026 — só precisa cobrir
  um download em andamento, não mais uma síntese local de vários minutos).

## Quem manda no áudio durante um anúncio

- Música: pausada explicitamente antes da fala (`player.pause()`), retomada depois —
  no caso da vinheta de entrada, a música nem começa (`autoPlay=false`) até ela acabar;
- Vinhetas gravadas e o áudio do boletim dividem o mesmo `MediaPlayer` dedicado
  (`announcementPlayer`) e o mesmo watchdog de 90 s — um por vez, release do anterior
  antes do novo;
- Anúncio automático: `MediaPlayer` precisa tocar o WAV do buffer. Se o WAV não estiver
  tocável, a entrada é cancelada e a música volta — sem alternativa nenhuma (TTS do
  Android removido 16/09/2026, ver seção "Feed remoto" acima);
- Widgets/notificação continuam operando o player de música normalmente — é daí que
  nascem as races de "música por cima da locução" (R2 em [STATE_MACHINE.md](STATE_MACHINE.md)).
