# Decisões de Projeto (ADRs)

> **Regra de ouro deste projeto:** preservar o comportamento atual antes de refatorar.
> Toda mudança estrutural mantém as funcionalidades existentes e é feita incrementalmente.
> O app já funciona e está em uso diário. Nada de "clean architecture deluxe" que mate a rádio no processo.

Formato leve: Contexto → Decisão → Motivo → **Não mudar sem**. A última seção é a que
impede alguém (inclusive outra IA) de "otimizar" uma decisão que tinha motivo.

---

## ADR-001 — TTS roda em processo separado (`:radio_voice`)

- **Contexto:** modelos de voz (VITS/Kokoro/etc.) consomem centenas de MB e podem
  crashar/OOM sem avisar.
- **Decisão:** `RadioVoiceSynthesisService` com `android:process=":radio_voice"`.
- **Motivo:** isolar consumo e crashes do TTS; player e UI sobrevivem a qualquer
  explosão do modelo.
- **Não mudar sem:** avaliar impacto de memória e o que acontece com a rádio se a
  síntese falhar no processo principal. Alternativas in-process só com limite de RAM
  comprovado em device real.

## ADR-002 — Roteirista via interface, fallback determinístico primeiro

- **Contexto:** queremos diálogos melhores via LLM local no futuro, mas a rádio precisa
  funcionar sempre, offline, sem pacote nenhum.
- **Decisão:** interface `RadioScriptWriter`; `OptionalLocalLlmRadioScriptWriter` só é
  tentado quando `filesDir/radio_writer/model.ready` existe e o modo é Dialogue;
  qualquer falha cai no `FallbackRadioScriptWriter` (roteiro fixo).
- **Motivo:** comportamento previsível por padrão; LLM é enhancement opcional plugável.
- **Não mudar sem:** manter garantia de fallback. Nunca deixar a rádio dependente do
  redator local para existir.

## ADR-003 — Engine TTS criado por request (sem cache persistente entre boletins)

- **Contexto:** hoje cada request ao serviço cria um `LocalRadioVoiceEngine` novo e dá
  `release()` no fim. Consequência: modelo recarregado do disco a cada boletim (lento),
  mas zero retenção de RAM entre requests.
- **Decisão:** manter assim **até** o `RadioBulletinController` existir e os requests
  estarem serializados.
- **Motivo:** cache persistente de engine sem serialização = dois engines na RAM ao
  mesmo tempo (pior que o reload). Ordem certa: serializar primeiro, cachear depois
  ([TODO.md](TODO.md) P0).
- **Não mudar sem:** fila única no serviço + política de despejo definida + teste de
  memória em device real.

## ADR-004 — Caminho duplo de voz (sherpa principal, TTS do sistema como fallback)

- **Contexto:** sherpa exige pacote instalado (~centenas de MB); TTS do sistema existe
  em quase todo aparelho.
- **Decisão:** síntese local primeiro (timeout 12 s); se falhar/ausente,
  `TextToSpeech` pt-BR fala o texto na hora.
- **Motivo:** a rádio nunca fica muda. Custo conhecido: qualidade/timbre diferentes
  entre caminhos, e acoplamento atual ao `ttsReady` (R6/R7 em [STATE_MACHINE.md](STATE_MACHINE.md))
  que será removido.
- **Não mudar sem:** garantir que ausência total de TTS do sistema não quebre o
  caminho sherpa.

## ADR-005 — WAV escrito à mão + gaps de silêncio entre falas

- **Contexto:** o boletim tem várias falas alternando locutores; precisamos controlar
  timing sem depender de encoder externo.
- **Decisão:** concatenar amostras PCM com gap fixo de 0,18 s entre linhas e gravar
  WAV 16-bit mono manualmente (`LocalRadioVoiceEngine.writeWav`).
- **Motivo:** controle fino de ritmo da "rádio", zero dependência, arquivo simples que
  qualquer `MediaPlayer` toca.
- **Não mudar sem:** necessidade concreta de compressão (tamanho) ou multi-canal;
  aí sim trocar por encoder mantendo os gaps configuráveis.

## ADR-006 — Widgets via RemoteViews + Intents diretos no serviço

- **Contexto:** widgets precisam reagir rápido e funcionar sem abrir o app.
- **Decisão:** layouts clássicos (RemoteViews compact/large); botões disparam intents
  tratados em `MusicPlaybackService.onStartCommand`; re-render via
  `PlayerWidgetRenderer.updateAll` nos eventos do player.
- **Motivo:** evitar binder/MediaController dentro do widget (complexidade + latência);
  caminho já suportado pelo Media3.
- **Não mudar sem:** necessidade real (ex.: seek bar interativa) e estudo das versões
  novas de RemoteViews (API 31+).
- **Pegadinha (29/08/2026):** `RemoteViews` só permite inflar uma lista fechada de
  classes de View (`FrameLayout`, `LinearLayout`, `TextView`, `ImageView`,
  `ProgressBar` etc.) — um `<View>` puro usado como scrim (`widget_bg_scrim`) quebrava
  os dois widgets inteiros com `InflateException: Class not allowed to be inflated
  android.view.View`, sem nenhum log no processo do app (a inflação falha no processo
  do launcher/host, não no nosso). Sintoma no aparelho: launcher mostra "Não é possível
  carregar o widget" e a falha sobrevive a `notifyAppWidgetViewDataChanged`/reinstalar o
  app — só reaparece renderizando de novo com um layout válido. Corrigido trocando o
  `<View>` por `<FrameLayout>` (mesmo resultado visual, classe permitida). Lição: ao
  adicionar qualquer View nova nesses layouts, checar contra a lista de classes
  suportadas por `RemoteViews.addView`/`checkNotSupported` antes de testar no aparelho.

## ADR-007 — Sessão de rádio com anti-repetição por similaridade

- **Contexto:** tocar a mesma rádio todo dia repetia sempre a mesma sequência.
- **Decisão:** `radioSessionFrom()` gera múltiplas candidatas com seeds aleatórias e
  escolhe a mais distante da última sessão salva (comparação de sequência), depois
  persiste a nova ordem.
- **Motivo:** variedade perceptível sem heurística pesada; determinístico o suficiente
  para debugar (última sessão fica registrada).
- **Não mudar sem:** métrica melhor de similaridade ou preferência explícita do usuário.

## ADR-008 — Overrides de metadados fora dos arquivos de áudio

- **Contexto:** corrigir tags pela UI sem reescrever MP3/FLAC do usuário (e lidar com
  arquivos read-only).
- **Decisão:** correções e unificações ficam em SharedPreferences
  (`metadata_overrides`, com schema version) e são aplicadas na leitura da biblioteca;
  escrita real nas tags acontece só via fluxos explícitos de gravação
  (jaudiotagger / MediaStore write request).
- **Motivo:** não corromper arquivos originais; overrides reversíveis e versionáveis.
- **Não mudar sem:** estratégia de sync entre override e tag física (ex.: detectar
  arquivo alterado externamente).

## ADR-009 — Proteções anti-morte da rádio (watchdog + wake mode + bateria)

- **Contexto:** em 23/08/2026 o processo foi morto pelo sistema em estado *cached*
  (`oom_score_adj=900`) durante pressão de memória (PSI critical) num Motorola edge 40
  (Android 15). Agravante próprio: se a fala do boletim travasse (timeout + TTS legado
  falhando calado), a música ficava pausada para sempre, o Media3 rebaixava o serviço
  de foreground e o app virava alvo fácil do killer.
- **Decisão (três camadas):**
  1. **Watchdog de anúncio** (90 s, token por anúncio): se `speakingNews` ou
     `pendingRadioIntro` continuar ativo após o prazo, libera player/TTS e retoma
     o playback incondicionalmente;
  2. **`setWakeMode(C.WAKE_MODE_LOCAL)`** no ExoPlayer: impede a CPU de dormir com
     áudio tocando;
  3. **Isenção de otimização de bateria**: permissão
     `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + linha em Configurações que some quando
     concedida (`isIgnoringBatteryOptimizations`).
- **Motivo:** manter a rádio viva em uso real **sem** esperar o
  `RadioBulletinController`; validado no aparelho (`isForeground=true`,
  `startForegroundCount=1`, playback PLAYING estável).
- **Não mudar sem:** o watchdog é rede de proteção temporária — o controller da FSM
  ([STATE_MACHINE.md](STATE_MACHINE.md)) o substitui; remover a isenção de bateria só
  com evidência de que o FGS sozinho sobrevive ao killer da Motorola em uso real.
- **Atualização (25/08/2026):** o problema voltou a acontecer mesmo fora do Modo Rádio
  (reprodução normal da biblioteca), o que aponta pra causa fora do fluxo de boletim
  (fora do escopo de R1-R8). Suspeitas sem confirmação ainda: pausa por perda de audio
  focus / `ACTION_AUDIO_BECOMING_NOISY` que nao retoma sozinha, ou kill do SO mesmo com
  a isenção de bateria concedida. Adicionado log de diagnóstico
  (`MusicPlaybackService`, tag `PailerPlaybackDiag`: `isPlaying`, `playWhenReady` + motivo,
  `playbackState`, erros do player, `onDestroy`/`onTaskRemoved`) para capturar evidência
  real via `adb logcat` na próxima ocorrência antes de tentar mais uma correção às cegas.
- **Atualização (26/08/2026):** a abertura falada (e a flag `pendingRadioIntro` citada
  acima) foi removida — ver ADR-010. O watchdog agora só guarda `speakingNews`
  (boletins); a lógica de "que anúncio estava tocando" ficou mais simples.

## ADR-010 — Abertura falada da rádio removida (data/hora/clima)

- **Contexto:** `speakRadioIntro()` falava data, hora, temperatura (open-meteo) e "Você
  está na Rádio X" antes de tocar a primeira música. Em uso diário real o dono do app
  achou que atrapalhava mais do que ajudava.
- **Decisão:** removidos `speakRadioIntro`, `finishRadioIntro`, `buildRadioIntroText`,
  a flag `pendingRadioIntro` e o `WeatherRepository` (ficou sem nenhum outro uso). A
  rádio agora dá `play()` imediatamente ao entrar (`playRadioSession` com
  `autoPlay = true`), sem esperar TTS/rede. Boletins de notícia entre as músicas
  continuam normalmente — não foram tocados.
- **Motivo:** pedido direto do usuário; a narração de clima/hora não agregava e ainda
  dependia de rede (open-meteo) e do TTS legado ficar pronto (~1,8 s de espera).
- **Não mudar sem:** confirmar com o usuário — o plano é substituir por vinhetas
  gravadas (`vinhetas/` na raiz do projeto) no lugar da abertura falada, não trazer a
  narração de volta.

## ADR-011 — Vinhetas gravadas no lugar da abertura falada

- **Contexto:** ADR-010 removeu a narração de data/hora/clima mas deixou a rádio sem
  nenhuma abertura. O usuário gravou uma intro comum a todas as rádios (`radio_intro`) e
  complementos por gênero, com nomes pensados pra reaproveitar entre rádios próximas
  (ex.: um complemento só de indie/psicodélico serve pras duas rádios).
- **Decisão:** arquivos movidos para `app/src/main/res/raw/` (nomes normalizados pra
  `snake_case`, obrigatório pra resource do Android) e tocados por
  `playRadioVinhetas()`/`playVinhetaResource()` em `LocalTuneViewModel.kt`: intro comum →
  complemento da rádio (`VINHETA_BY_RADIO_KEY`, chave = nome da rádio normalizado por
  `normalizeRadioKey()`) → `controller.play()`. Reusa o mesmo `announcementPlayer` e o
  mesmo watchdog de 90 s dos boletins de notícia (campo `pendingVinheta`, paralelo ao
  `speakingNews`). Só dispara em entrada nova (`startIndex == 0`); pular pra uma faixa da
  sessão ao vivo não repete a vinheta. Rádio sem complemento mapeado toca só a intro.
- **Motivo:** manter a "personalidade de rádio FM" sem TTS nem dependência de rede;
  arquivos pequenos (~100-190 KB cada, ~1 MB total), custo de APK desprezível.
- **Não mudar sem:** se o usuário mandar vinhetas novas ou pedir pra trocar o mapeamento,
  atualizar `VINHETA_BY_RADIO_KEY` E `vinhetas/README.md` juntos — são a mesma fonte de
  verdade, não deixar um sem o outro.

## ADR-012 — Build release assinada com a chave de debug (de propósito)

- **Contexto:** o app já estava instalado no aparelho de testes (assinado com a chave de
  debug automática) desde 21/08/2026, com dados reais acumulados (favoritos, histórico,
  overrides de metadados, pacote de vozes TTS de ~45 MB importado). Ao configurar
  `signingConfigs`/`buildTypes.release` pela primeira vez, gerei uma chave de release
  dedicada (`keystore/pailer-release.jks`, prática padrão pra apps distribuídos) — mas
  isso trocaria a assinatura e exigiria desinstalar o app antes do primeiro
  `adb install` release, apagando os dados acima. Usuário perguntou por quê e pediu pra
  evitar.
- **Decisão:** `buildTypes.release.signingConfig` usa
  `signingConfigs.getByName("debug")` — a mesma chave que a build debug sempre usou.
  `keystore.properties` + `keystore/pailer-release.jks` continuam existindo, prontos e
  **sem uso**, só pro dia (se algum dia) o app for publicado de verdade.
- **Motivo:** app pessoal, nunca vai pra Play Store hoje; instalar por cima sem perder
  dados vale mais que seguir a convenção de assinatura dedicada.
- **Não mudar sem:** avisar antes — trocar pra `signingConfigs.getByName("release")`
  exige desinstalar o app do aparelho (perde dados locais) no primeiro install seguinte.
  Ver [RELEASE.md](RELEASE.md).

## ADR-013 — Pacote de voz Kokoro (Dora/Alex) + timeout de boletim maior

- **Contexto:** as vozes Piper (Dii/Faber) soavam "fracas, sem personalidade" (feedback
  direto do usuário). Kokoro (StyleTTS2, 82M parâmetros) é um motor bem maior e mais
  natural, já suportado pelo código (`LocalRadioVoiceEngine`/`OfflineTtsKokoroModelConfig`)
  mas nunca usado. Os releases oficiais do sherpa-onnx (`kokoro-multi-lang-v1_1`, o mais
  recente) **não têm vozes em português** — só o `kokoro-multi-lang-v1_0`
  (53 speakers) tem `pf_dora`/`pm_alex`/`pm_santa` (IDs 42/43/44), confirmado lendo os
  metadados do `model.onnx` diretamente (a documentação do sherpa-onnx e buscas na web
  davam informação inconsistente sobre isso).
- **Decisão:**
  1. Modelo fp32 original (326 MB) quantizado pra int8 localmente
     (`onnxruntime.quantization.quantize_dynamic`, script descartável — não versionado)
     → 114 MB, metadados (`id2speaker` etc.) reaplicados manualmente após quantizar
     (a quantização não preserva `metadata_props`). Testado localmente com o pacote
     Python `sherpa-onnx` antes de ir pro aparelho (áudio não-degenerado, RMS/pico
     normais) — trust but verify antes de gastar um ciclo de build+install.
  2. Pacote final (`voices.bin` + `tokens.txt` + `espeak-ng-data/`, sem os léxicos/dict
     de inglês/chinês — desnecessários pro caminho espeak do pt-br) embalado como
     `manifest.json` (`engine: kokoro`, `femaleSpeakerId: 42`, `maleSpeakerId: 43`,
     `speed: 0.92`) + zip, ~160 MB. Fonte fica em `voice-models/kokoro/` no workspace
     (fora do git — grande demais, reproduzível a partir do script).
  3. `BULLETIN_PREP_TIMEOUT_MS`: 120s → **160s**. Medido em campo (26/08/2026, Moto
     edge 40): diálogo de 3 falas fem→masc→fem, duração "Curta" (~300 caracteres) leva
     **~152s** com Kokoro (~9-10s de load por locutor sem cache entre requests, ver
     ADR-003, + ~2 chars/s de geração) — bem mais lento que o Piper (~60-65s pro mesmo
     diálogo). 120s cortava o boletim quase no fim (usuário viu isso acontecer:
     "ficou 120s e voltou sem resposta"); 160s deu folga de ~8s.
- **Motivo:** o usuário ouviu o teste de frase única e aprovou a voz
  ("a voz parece boa"). Kokoro fica ativado.
- **Não mudar sem:** ciente de que 160s cobre confortavelmente só a duração "Curta" —
  "Normal" (~440 caracteres) e "Longa" (~630) provavelmente ainda estouram o prep e
  caem pra voz do Android no boletim ao vivo (o caminho de emergência,
  `LOCAL_VOICE_TIMEOUT_MS` = 12s, continua baixo de propósito — subir esse deixaria a
  música pausada em silêncio por muito tempo durante o uso real, pior que cair pro
  Android). Se precisar de Normal/Longa confiável com Kokoro, a solução certa é
  serializar + cachear engine entre requests (P0 do [TODO.md](TODO.md)), não só subir
  timeout de novo.

## ADR-014 — Dois locutores masculinos (Frankie/Nicky) + bate-bola sensível ao conteúdo

- **Contexto:** dois pedidos diretos do usuário em 28/08/2026: (1) a voz feminina do
  Kokoro (`pf_dora`, ID 42) não agradou; (2) o bate-bola do boletim (`RadioBulletin.kt`)
  reagia com frases genéricas sorteadas ("Pois é, quem diria") desconectadas do conteúdo
  real da notícia, sempre com a locutora abrindo/fechando e o locutor só emplacando uma
  frase no meio — sem opinião de verdade nem participação igual. Pedido de inspiração:
  clima Sopranos/GTA IV, dois locutores com personalidade oposta (otimista vs
  pessimista) e humor ácido, se chamando pelo nome no ar; nomes escolhidos pela IA de
  propósito ("elemento surpresa").
- **Decisão (voz):** trocado o slot `femaleSpeakerId` do manifest Kokoro de 42
  (`pf_dora`) pra 44 (`pm_santa`) — terceira voz masculina do mesmo pacote, citada como
  não usada desde o ADR-013. **Não** voltou pro Piper: o Piper já tinha sido testado e
  rejeitado antes por soar "fraco, sem personalidade" (ADR-013), então reaproveitar o
  Kokoro (motor já aprovado) trocando só um ID resolve sem reabrir aquele problema.
  Nomes dos personagens: **Frankie** (otimista, slot Female) e **Nicky** (pessimista,
  slot Male) — os enums `RadioSpeaker.Female`/`Male` continuam intactos, são só nomes
  de slot herdados do pacote de voz, não implicam gênero. `speed` também caiu de 0.92
  pra 0.85 (locutores mais lentos, outro pedido do usuário), com
  `BULLETIN_PREP_TIMEOUT_MS` ajustado de 160s pra 175s na mesma proporção. Voz do ID 44
  sintetizada e validada por RMS/pico localmente (mesmo método do ADR-013) antes de
  empacotar, mas **ainda sem aprovação por ouvido humano no aparelho**.
- **Decisão (roteiro):** `FallbackRadioScriptWriter.buildDialogueLines()` reescrito:
  classifica o tema da notícia por palavra-chave (título + resumo, agora capturado do
  RSS — ver decisão de notícias abaixo) em 10 categorias, escolhe de bancos de texto
  próprios por tema pra cada personagem (opinião "inventada" mas amarrada ao tema, não
  genérica), extrai um "gancho" (percentual/valor/número do texto) quando existe, e
  monta o número de falas **pela duração** (2/4/6, sempre metade pra cada um) em vez de
  gerar um script fixo de 3 falas e cortar depois com `fitFor()` — o corte antigo podia
  apagar a fala inteira de um dos dois se o outro estourasse o orçamento de palavras,
  quebrando a participação igual. `fitFor()` continua só como rede de segurança.
- **Decisão (notícias):** `NewsBulletinRepository` trocou de 4 feeds só do g1 pra 5
  feeds (2 g1 + Super/Olhar Digital/BBC Brasil) buscados **em paralelo**
  (`coroutineScope`/`async`/`awaitAll` — sequencial custava a soma dos timeouts, hoje
  custa só o maior) e passou a capturar também `<description>`/`<summary>` do RSS (até
  220 caracteres, limpo de HTML), não só o título — é esse resumo que dá ao roteirista
  conteúdo de verdade da matéria pra comentar, não só a manchete.
- **Motivo:** reduzir dependência de uma única fonte editorial (pedido explícito:
  "vindo muita notícia do G1"), trazer o ângulo de curiosidades/novidades pedido, e
  fazer o bate-bola soar como comentário de verdade sobre o tema em vez de reação
  aleatória — sem precisar de LLM real (`OptionalLocalLlmRadioScriptWriter` continua
  não implementado, ver ADR-002), só heurística determinística mais rica.
- **Não mudar sem:** confirmar com o usuário se a voz do ID 44 (Nicky) soou bem no
  aparelho — se não, `voice-models/kokoro/kokoro-multi-lang-v1_0/` tem só esses 3
  speakers pt-BR (42/43/44), não há um quarto pra tentar sem baixar/treinar outro
  pacote. Se os bancos de texto por tema (`TOPIC_BANK` em `RadioBulletin.kt`) começarem
  a repetir demais em uso real, a correção é adicionar mais variantes por tema, não
  reverter pra reação genérica.
- **Atualização (28/08/2026):** `pm_santa` (ID 44) foi ouvido no aparelho e **também**
  rejeitado (mesma sessão, personagem "Frankie"). Kokoro pt-BR ficou então limitado a
  1 voz aprovada (`pm_alex`, ID 43, hoje o Nicky) — os outros dois speakers do pacote já
  foram testados e recusados. Continuação em ADR-015.

## ADR-015 — Bug de acentuação no texto do bate-bola + motor de voz "mixed"

- **Contexto:** com o Kokoro pt-BR esgotado (ADR-014), testados vários candidatos Piper
  pt-BR pro Frankie (Miro, Jeff, Faber, Cadu — baixados de
  `k2-fsa/sherpa-onnx/releases/tag/tts-models`). Usuário reportou "atenção" saindo com
  som de K em vez de S, e "notícia"/"aí" sem ênfase na sílaba tônica, em **todos** eles.
  Investigação (metadados do onnx, comparação byte-a-byte do `espeak-ng-data` entre
  pacotes, leitura do código-fonte do `piper-phonemize-lexicon.cc` e do
  `espeak_ng_SetVoiceByName` do espeak-ng) descartou defeito de modelo/motor. Causa real:
  todo o texto falado escrito em `RadioBulletin.kt` (bancos de fala do Frankie/Nicky) e
  em `LocalTuneViewModel.kt` (scripts dos botões de teste) foi escrito **sem nenhum
  acento** ("atencao", "noticia", "ai"). Sem o acento, o fonemizador espeak-ng (usado
  tanto pelo Kokoro quanto pelo Piper) não tem como saber onde recai a força da sílaba
  nem que "ção" é som de S — isso vale pra qualquer voz, não é característica de nenhum
  pacote específico. Um bug relacionado foi achado no classificador de tema
  (`classifyTopic`): a lista `TOPIC_KEYWORDS` também estava sem acento e comparava por
  substring direto contra o resumo/título real do RSS (que vem acentuado) — "politica"
  nunca batia com "política" porque "í" e "i" são caracteres diferentes.
- **Decisão:**
  1. Todo o texto falado em `RadioBulletin.kt` e `LocalTuneViewModel.kt` reescrito com
     acentuação correta.
  2. `classifyTopic` passou a normalizar (remover diacríticos via
     `java.text.Normalizer.Form.NFD` + regex `\p{Mn}+`) os dois lados antes de comparar,
     em vez de manter uma lista de palavras-chave acentuada à mão — mais robusto a longo
     prazo que corrigir acento por acento numa lista que só cresce.
  3. Voz final escolhida pelo usuário depois de ouvir as amostras corrigidas: **Frankie**
     = Piper `pt_BR-faber-medium` (não tinha sido testado individualmente antes — só o
     par Dii/Faber tinha sido rejeitado em conjunto no ADR-013); **Nicky** continua
     Kokoro `pm_alex` (ID 43), sem mudança.
  4. Como Frankie (vits/Piper) e Nicky (kokoro) agora são motores diferentes no mesmo
     pacote, criado um terceiro tipo de `engine` no manifest: **`"mixed"`** — cada slot
     (`voices.female`/`voices.male`) declara seu próprio `"engine"` (`"vits"` ou
     `"kokoro"`) e sua própria config, em vez do pacote inteiro rodar um único motor.
     Implementado em `RadioVoicePackageRepository.kt` (`femaleEngine`/`maleEngine`,
     `femaleKokoro`/`maleKokoro`, validação por slot) e `LocalRadioVoiceEngine.kt`
     (`perSpeakerEngine()`/`vitsFor()`/`kokoroFor()`/`speakerIdFor()` agora dependem do
     motor daquele slot especificamente quando `engine == "mixed"`). Os engines
     existentes (`vits`, `vits-dual`, `piper-dual`, `kokoro`, `supertonic`) não mudaram
     de comportamento.
  5. `speed` caiu mais uma vez, de 0.85 pra **0.78** (outro pedido do usuário, "um pouco
     mais devagar"), confirmado nas amostras que o usuário aprovou.
  6. Pacote final: `voice-models/mixed_package/` (fonte) →
     `voice-models/Pailer-Radio-Voices-FrankieFaber-NickyKokoro.zip` (importável, ~137 MB
     — a maior parte é o modelo Kokoro do Nicky; `espeak-ng-data` é compartilhado entre
     os dois slots dentro do zip, já que é byte-idêntico entre os pacotes Kokoro e Piper
     testados, evitando duplicar ~18 MB à toa).
- **Motivo:** o usuário confirmou por ouvido que a acentuação corrigida resolveu o
  problema relatado ("amei, ficou ótimo agora") e escolheu Faber entre as opções
  testadas.
- **Não mudar sem:** ao escrever qualquer string nova que vai ser falada pelo TTS
  (`RadioScriptLine`, scripts de teste), sempre usar acentuação correta — não é estilo,
  é requisito funcional de pronúncia (ver também o comentário no topo de
  `RadioBulletin.kt`). Ao adicionar mais opções de voz Piper pt-BR no futuro
  (`Miro`/`Jeff`/`Cadu`/`Edresson`/`Dii` já estão em `voice-models/`, mais opções em
  `k2-fsa/sherpa-onnx/releases/tag/tts-models`, incluindo `vits-coqui-pt-cv` — treinado
  em Common Voice, multi-falante, ainda não testado), testar sempre com texto acentuado
  desde o primeiro teste, pra não repetir esse ciclo de suspeitar do modelo errado.
- **Atualização (28/08/2026):** o pacote `mixed` (Frankie/Piper + Nicky/Kokoro) foi
  testado em boletim real no aparelho e **estourou os 175s** de
  `BULLETIN_PREP_TIMEOUT_MS` — o Kokoro sozinho já não cabia direito (ADR-013/014), e
  com dois engines carregando na mesma síntese (`LocalRadioVoiceEngine` não cacheia
  entre requests, ADR-003) o tempo total ainda foi maior. Decisão final: **abandonar o
  Kokoro** e o motor `mixed` na prática, ficando só com Piper (`vits-dual`) nos dois
  locutores. Nicky trocou de Kokoro `pm_alex` pra Piper `pt_BR-jeff-medium` (mesmo
  dataset gravado do Faber, `OHF-Voice/voice-datasets`, CC0) — testado no aparelho e
  aprovado no desempenho ("o teste foi perfeito"), mas o usuário não gostou do timbre
  do Jeff especificamente. Trocado de novo, ainda no mesmo pacote leve, pra
  **Piper `pt_BR-miro-high`** (voz que o usuário já tinha elogiado antes de o bug de
  acentuação ser encontrado — ver acima; pipeline/dataset diferente do Faber/Jeff,
  timbre mais distinto entre os dois locutores). Licença do Miro é CC-BY-NC-SA (não
  comercial) — sem problema aqui, app pessoal nunca publicado (ADR-012). Pacote final:
  `voice-models/piper_only_package/` → `Pailer-Radio-Voices-FrankieFaber-NickyMiro.zip`
  (~36 MB, contra ~137 MB do pacote `mixed` anterior). **Miro também foi rejeitado** logo
  em seguida (mesmo dia) — trocado de novo pra Piper `pt_BR-cadu-medium` (mesmo dataset
  CC0 do Faber/Jeff), zip `Pailer-Radio-Voices-FrankieFaber-NickyCadu.zip`. Medido
  localmente (PC, não aparelho): os
  dois motores carregados + 6 falas sintetizadas (diálogo "Longo" inteiro) em ~11s —
  ordens de grandeza mais rápido que o Kokoro. O motor `mixed` (código em
  `RadioVoicePackageRepository.kt`/`LocalRadioVoiceEngine.kt`) **continua no código**,
  sem uso — não removido, pode servir se um dia fizer sentido misturar motores de novo,
  mas não é mais o caminho ativo. `BULLETIN_PREP_TIMEOUT_MS` mantido em 175s por ora
  (folga generosa de sobra com Piper puro, não precisa de ajuste fino agora).

## ADR-016 — Capa por-faixa em álbuns "various artists" + consequências no shuffle e no delete de rádio

- **Contexto (29/08/2026):** usuário reportou que um álbum montado à mão (ex.: "2000's",
  faixas de vários artistas sob o mesmo nome de álbum) mostrava a **mesma capa** — a da
  primeira faixa escaneada — em todas as músicas, no álbum, na tela cheia do player, nos
  cartões de artista e na rádio criada a partir dele. Causa raiz: o MediaStore agrupa por
  `ALBUM_ID`, que é baseado só no nome do álbum; todas as faixas com o mesmo `album` tag
  caem no mesmo `ALBUM_ID`, e `content://media/external/audio/albumart/<id>` só guarda
  **uma** imagem por `ALBUM_ID`, não uma por faixa.
- **Decisão:**
  1. `LocalAlbum.isVariousArtists` (`songs.map{artist}.distinct().size > 1`) marca esses
     álbuns. Onde marcado: mosaico de 4 capas (`AlbumCoverMosaic`) no lugar da capa única
     (header do álbum, `AlbumRow`/`AlbumGridCard`/`AlbumCoverCard`); faixas dentro do
     álbum mostram thumbnail própria (`AlbumTrackRow.showArtwork`).
  2. Pra faixa individual (fora do contexto de álbum — tela cheia do player, mini player,
     "Ouvir de novo", lista de músicas, linha de rádio, cartão "ao vivo agora", avatar de
     artista), a correção é **sempre** tentar a capa **embutida no arquivo** primeiro
     (`loadEmbeddedArtwork` via `MediaMetadataRetriever.embeddedPicture`, não o MediaStore
     compartilhado), caindo pro `artworkUri` do MediaStore só se o arquivo não tiver capa
     embutida. Parâmetro `embeddedSourceUri` em `ArtworkBox`; pro estado do player,
     `PlayerUiState.artworkSourceUri` (de `MediaItem.localConfiguration?.uri`) evita ter
     que resolver a `LocalSong` de novo em `MiniPlayer`/`FullPlayer`/`LiveNowRadioCard`.
  3. `previewCovers()`/`RadioCoverMosaic` (mosaico de rádio) usavam só
     `distinctBy{albumId}` — colapsa pra 1 capa quando as faixas da rádio vêm de um único
     álbum "various artists". Complementado com `distinctBy{artist}` como segunda fonte de
     diversidade quando a primeira não enche 4 posições.
  4. **Consequência no shuffle:** `radioSessionFrom()` tocava rádio de álbum sempre na
     ordem de faixa (ADR de origem: álbum de artista único é sequência proposital, tipo
     álbum conceitual). Pra álbum "various artists" isso não faz sentido — não é uma
     sequência intencional de verdade — então passou a embaralhar com anti-repetição
     (`shuffledRadioSession()`, mesmo esquema do de rádio de artista). Álbum de artista
     único continua tocando em ordem, sem mudança.
  5. **Consequência no delete de rádio:** usuário pediu pra poder apagar qualquer rádio,
     não só as personalizadas — motivado por querer remover a rádio de perfil "Anos 2000".
     Rádios de perfil/gênero não têm definição persistida (são recalculadas da biblioteca
     toda vez), então "apagar" uma delas só tira da lista (`hideRadio()`, chave = nome
     normalizado, em `hidden_radio_keys` no mesmo SharedPreferences das definições
     personalizadas). Ação reversível via "Restaurar rádios ocultas" nas Configurações.
- **Motivo:** o usuário insistiu explicitamente que a vinheta gravada por gênero (Grunge,
  Indie, Jazz etc. — `VINHETA_BY_RADIO_KEY` em `LocalTuneViewModel.kt`, chaveada pelo
  **nome** da rádio) precisa continuar funcionando se uma rádio for apagada e recriada
  depois (projeto de vinhetas por gênero ainda em construção, aos poucos). Como
  `hideRadio()` só filtra a lista por nome — nunca toca em `VINHETA_BY_RADIO_KEY` nem na
  lógica que gera a rádio a partir da biblioteca — desocultar uma rádio de perfil/gênero a
  faz reaparecer com a vinheta de sempre, intacta.
- **Não mudar sem:** ao adicionar qualquer tela nova que mostre a capa de uma faixa
  individual, usar `embeddedSourceUri` (não só `artworkUri`) — do contrário reintroduz o
  mesmo bug em mais um lugar. Ao mexer em `hideRadio()`/`VINHETA_BY_RADIO_KEY`, manter os
  dois desacoplados (um não deve depender do outro) — é isso que garante a vinheta
  sobreviver ao apagar/recriar.

