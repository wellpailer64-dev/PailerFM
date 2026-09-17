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
- **Atualização (01/09/2026):** o redator local foi ligado com Qwen3 1.7B Q4_K_M em
  GGUF, executado por `llama.cpp` via JNI (`pailer_llama`). O modelo não entra no APK:
  fica em pacote importável (`manifest.json` + `.gguf`) e é copiado para
  `filesDir/radio_writer`. Para não gerar oito matérias ao entrar na rádio, `loadScripts`
  continua criando roteiros-base determinísticos e o LLM só reescreve o próximo boletim
  durante `prepareUpcomingBulletin()`. Se o modelo demorar, falhar ou devolver JSON ruim,
  o fallback já existente continua tocando.
- **Atualização (01/09/2026, falha em campo):** se o roteiro/áudio preparado não estiver
  pronto quando chega a hora do boletim, a rádio não chama mais LLM nem síntese local
  depois de pausar a música. Ela usa imediatamente o roteiro-base com TTS do Android.
  O teste de boletim também deixou de usar texto fixo e passou a buscar uma notícia RSS
  real no momento do teste.
- **Atualização (02/09/2026, diagnóstico "modelo não carrega"):** o redator local sempre
  caía no fallback em campo. Log nativo do llama.cpp não chegava ao logcat (stderr não é
  redirecionado por padrão em app Android) — adicionado `llama_log_set()` encaminhando pra
  `__android_log_print` (tag `PailerLlama`) em `pailer_llama_jni.cpp` pra diagnosticar.
  Três causas encontradas, todas no mesmo teste em device real (Motorola Edge 40,
  Dimensity 1200, 3 threads):
  1. `LOCAL_WRITER_NATIVE_TIMEOUT_MS` (20 s) abortava o `llama_decode` via
     `abort_callback` a poucos instantes do fim — o decode real leva ~19-27 s neste
     aparelho pra um prompt de ~300 tokens. Subiu pra 45 s (nativo) / 55 s (wrapper
     Kotlin `LOCAL_WRITER_TIMEOUT_MS`), com folga generosa porque a geração roda em
     background durante a música (`prepareUpcomingBulletin()`), não bloqueia a entrada.
  2. Qwen3 é um modelo "híbrido" que pensa por padrão (`<think>...</think>`) mesmo com
     ChatML cru sem ferramenta de template - o raciocínio consumia o orçamento de tokens
     inteiro antes de chegar no JSON. Corrigido com `/no_think` no turno do usuário +
     bloco `<think>\n\n</think>\n\n` já vazio pré-preenchido no turno do assistente
     (`buildPrompt()`).
  3. `maxTokens` estava fixo em no máximo 96 no código (Kotlin e JNI), abaixo do que o
     manifest do pacote já pedia (260) e insuficiente pra 4 falas em JSON - cortava a
     resposta no meio de uma string. Subiu o teto pra 260 nos dois lados. Também
     pré-preenchido o `[` de abertura do array no prompt (trava o formato sem precisar
     mudar o parser, que já lida bem com JSON sem colchete de abertura).
  Com os três ajustes, teste real gerou e sintetizou boletim completo via LLM local
  (`roteiro=LocalLlm`) em teste em campo. Variância normal do modelo (roteiro que não
  bate no formato de 4 falas) ainda cai no fallback determinístico, como já era o
  comportamento esperado - não é bug, é o design do ADR funcionando.
- **Não mudar sem (atualização):** manter o `llama_log_set()` — sem ele, qualquer futura
  falha do redator local volta a ser uma mensagem genérica sem pista nenhuma do que
  aconteceu de fato dentro do llama.cpp.
- **Atualização (02/09/2026, eco de instrução + acentuação + crash nativo):** usuário
  ouviu um boletim real e reportou que o Frankie/Nicky literalmente falaram a instrução
  do roteiro ("provocação esperançosa, sem ingenuidade", "contra provocação... volta pra
  rádio") em vez de gerar a fala de verdade - o Qwen3 1.7B copiou a descrição da
  instrução como se fosse texto pra falar. Três correções em `buildPrompt()`/
  `parseGeneratedLines()` (`RadioBulletin.kt`):
  1. Prompt reescrito com um exemplo few-shot completo (matéria fictícia de trânsito)
     mostrando o estilo esperado, mais instrução explícita "as descrições abaixo dizem O
     QUE cada fala deve fazer, não são texto pra repetir". Modelos pequenos seguem
     exemplo concreto muito melhor que instrução abstrata.
  2. Reforçada a exigência de acentuação/pontuação corretas no prompt (mesmo motivo do
     ADR-015, agora pro texto que o LLM gera em vez de string fixa no código) e
     adicionada uma checagem de segurança em runtime (`hasAccentuation()`): se o roteiro
     gerado não tiver nenhum caractere acentuado, é sinal de que saiu sem acento e cai no
     fallback - texto de português corrido desse tamanho quase sempre tem acento, então
     zero acentos é sinal forte de problema.
  3. **Bug mais sério achado no processo**: o prompt maior (por causa do exemplo
     few-shot) ultrapassou um cap fixo de 512 tokens que `pailer_llama_jni.cpp` usava pro
     `n_batch` do llama.cpp. Como `decode()` manda o prompt inteiro de uma vez via
     `llama_batch_get_one()`, um `n_batch` menor que o prompt viola um invariante interno
     do llama.cpp (`GGML_ASSERT(n_tokens_all <= cparams.n_batch)`) e derruba o **processo
     inteiro** com `SIGABRT` - não é um erro tratável em Kotlin, mata o app (incluindo a
     música tocando) sem aviso. Corrigido trocando o cap fixo de 512 por `min(n_prompt,
     2048)` (mesmo teto usado em `n_ctx`), já que `n_batch` precisa sempre caber o prompt
     inteiro nesse fluxo de decode em lote único.
  Timeouts subiram de novo (45s→60s nativo, 55s→70s Kotlin) porque o prompt maior aumenta
  o tempo de prefill. Testado em campo: gerou boletim de 4 falas com acentuação correta e
  sem eco de instrução, ex.: "A executiva do Bank of America foi morta a facadas em Times
  Square, Nova York. Policiais atiraram contra ela, sem motivo, diz a polícia." Log do
  texto gerado adicionado (`Log.d(TAG_RADIO_WRITER, "redator local texto: ...")`) pra
  inspeção futura sem precisar reconstruir com log temporário de novo.
- **Risco conhecido, não corrigido:** o redator local roda no processo principal do app
  (mesmo `Dispatchers.Default` do `LocalTuneViewModel`), não isolado como o TTS
  (`:radio_voice`, ver ADR-001). O bug do `n_batch` acima mostra que o llama.cpp pode
  matar o processo inteiro com `SIGABRT` num assert interno - a mesma classe de risco que
  motivou isolar o TTS em processo separado se aplica aqui, só que ainda não foi feito
  pro redator. Avaliar mover `LocalLlamaTextGenerator`/`OptionalLocalLlmRadioScriptWriter`
  pro processo `:radio_voice` (ou um processo próprio) antes de confiar no redator local
  como algo mais que "enhancement opcional que não pode derrubar a rádio".
- **Atualização (02/09/2026, reflexão do Nico deixou de ser banco fixo):** usuário reportou
  que a reflexão existencialista/absurdista do Nico (`NICO_REFLECTIONS_LIGHT`/`_DEEP` em
  `RadioBulletin.kt`, ~5 frases cada) soava repetitiva/genérica - "queria algo efêmero, um
  comentário com conhecimento baseado nessa filosofia, e não frases prontas". Como o banco
  fixo é sempre uma lista curta sorteada, não tem como ficar "efêmero" sem gerar de
  verdade - só o redator local (LLM) consegue isso. `buildPrompt()` agora pede **5 falas**
  em vez de 4: a 5ª (Male/Nico) é o comentário existencialista/absurdista, gerado em cima
  da matéria específica (Camus/Sartre/Nietzsche/Kafka/Beckett/Cioran como inspiração, nome
  do pensador só se a atribuição for certa) - exemplo few-shot atualizado com uma 5ª linha
  pro estilo esperado (mesma lição do ADR anterior: modelo pequeno precisa de exemplo
  concreto). `withPhilosophicalCloser()` usa essa 5ª fala como reflexão quando
  `source == LocalLlm` e ela existe; só cai pro banco fixo quando o roteiro veio do
  fallback determinístico ou o LLM não emplacou a 5ª fala dessa vez - o banco fixo continua
  existindo só como rede de segurança, nunca mais como caminho principal quando o redator
  local está instalado e funcionando.
  - **Ainda não testado em campo** (só compilado) - falta confirmar se o Qwen3 1.7B segue
    bem a instrução da 5ª fala na prática, e se o prompt maior (mais uma linha de exemplo +
    mais instrução) precisa de mais tempo/tokens do que os timeouts atuais permitem.
  - Isso só tem efeito com o pacote do redator local instalado (`filesDir/radio_writer/
    model.ready`) - sem ele, a rádio continua no fallback determinístico e a reflexão
    continua vindo do banco fixo, sem solução "efêmera" possível sem LLM.

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
- **Atualização (01/09/2026):** o diálogo deixou de ser "manchete + crítica genérica".
  A primeira fala agora pode receber, em tempo de reprodução, a última música ouvida
  ("Você acaba de ouvir X, de Y, e vamos às notícias."); o Nicky passa a resumir ou
  explicar a matéria usando o resumo real do RSS antes de fazer a leitura pessimista; o
  Frankie provoca pelo lado otimista; o Nicky contra provoca com gancho ou consequência;
  e o boletim fecha chamando a música da rádio de volta. Mesmo a duração Short usa essa
  estrutura mínima de 5 falas, aceitando um boletim um pouco maior para soar mais
  inteligente e menos automático.
- **Atualização (01/09/2026, tom editorial):** os dois locutores ganharam uma leitura de
  mundo mais forte. Eles entendem capitalismo tardio, imperialismo, corporativismo,
  indústria cultural, lobby, plataformas e captura de mercado, mas essa bagagem aparece
  como forma de observar a notícia, não como bordão obrigatório. O Frankie continua
  sendo o contraponto menos cínico, mas deixou de soar ingênuo; o Nicky critica com mais
  análise material e menos reclamação solta.

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

## ADR-017 — Adicionar mais artista/álbum a uma rádio personalizada já existente

- **Contexto:** rádio personalizada (ADR de "Rádios personalizadas" em
  [RADIO_PIPELINE.md](RADIO_PIPELINE.md)) nascia de **uma** fonte só (um álbum, um
  artista ou uma categoria) — sem jeito de misturar mais material na mesma rádio depois
  de criada, só criar outra do zero. Usuário pediu um botão "+" na tela da rádio pra
  adicionar mais um artista ou álbum a ela.
- **Decisão:**
  1. `CustomRadioDefinition` ganhou `extraSourceIds: List<String>` (mesmo formato
     prefixado de `id` — `"album:<id>"`/`"artist:<chave>"`/`"genre:<chave>"`), persistido
     como array JSON extra. Campo ausente no JSON salvo (definições de antes dessa
     mudança) vira lista vazia — sem migração, retrocompatível de graça.
  2. `MusicLibraryRepository.addSourceToCustomRadio(customId, sourceId)` acrescenta uma
     fonte extra (idempotente - repetir a mesma fonte não duplica). `customRadiosFrom()`
     casa a fonte primária **e** todas as extras contra a biblioteca, união distinta por
     id de faixa.
  3. `LocalRadio.hasMultipleSources` (true quando há 2+ fontes) muda o comportamento de
     `radioSessionFrom()`: com fonte única continua exatamente como antes (álbum de
     artista único toca em ordem de faixa, artista único ou álbum "various artists"
     embaralha simples); a partir de 2 fontes cai sempre no `buildRadioQueue` genérico
     (mesmo algoritmo de diversidade das rádios de categoria) - misturar material de
     fontes diferentes só faz sentido embaralhado, nunca "ordem de álbum".
  4. UI: botão "+" (`Icons.Filled.Add`) do lado do "Entrar"/"Sair" e da lixeira em
     `RadioDetailScreen`, só quando `radio.isCustom` — rádio de perfil/categoria
     automática (Grunge, Anos 2000, "Rádio recente") não tem definição persistida pra
     estender, então não ganhou o botão. Abre `AddSourceToRadioDialog` (novo, sem picker
     genérico reaproveitável no app ainda): abas Artistas/Álbuns + busca, reaproveitando
     `RowItem`/`ArtworkBox`/`MetadataTextField` já existentes. Ao escolher, a tela troca
     `selectedRadio` pela rádio já recomputada (mesmo padrão de
     `saveAlbumMetadataEdit`/`saveArtistMetadataEdits` — chamada síncrona na thread
     principal, sem `Dispatchers.Default`) em vez de esperar o próximo
     `rebuildLibraryContent()` assíncrono.
- **Motivo:** usuário queria misturar material relacionado (ex.: dois artistas parecidos)
  numa rádio só sem perder o que já tinha montado, e sem duplicar rádios parecidas na
  lista.
- **Bug de crash achado no teste real:** a primeira versão envolvia a chamada de
  `addArtistToRadio`/`addAlbumToRadio` em `Dispatchers.Default` (pra não travar a UI
  recomputando `radiosFrom()` na thread principal), mas essas funções chamam
  `showToast()` internamente, que faz `Toast.makeText(...).show()` sem guarda de thread -
  `NullPointerException: Can't toast on a thread that has not called Looper.prepare()`,
  **derrubando o app inteiro** (`FATAL EXCEPTION: main`, processo morto). Corrigido
  chamando direto na thread principal, igual todo outro fluxo "editar e recomputar" já
  existente (`onSaveMetadata`, `onCreateRadio`) - nenhum deles usa `Dispatchers.Default`
  nesse tipo de chamada, e o motivo é exatamente esse.
- **Não mudar sem:** qualquer função de ViewModel que chama `showToast()` internamente
  (a maioria das que fazem "editar e devolver o objeto atualizado") só pode ser chamada
  direto da thread principal - nunca envolver em `Dispatchers.Default`/`Dispatchers.IO`
  no call site. Se o custo de recomputar `radiosFrom()` virar um problema real de
  performance percebida, mover o `Toast` pra fora da função (callback separado) antes de
  mexer no threading, não o contrário.

## ADR-018 — Motor de voz único: Supertonic 3 (Piper e Kokoro removidos)

- **Contexto:** usuário pediu pra testar alternativas mais realistas de voz (VITS
  genérico via sherpa-onnx, Supertonic, OmniVoice, Pocket TTS) além do Piper/Kokoro já
  documentados (ADR-013/014/015). Testado fora do app, em Python (`sherpa-onnx` PyPI,
  mesma versão `1.13.6` do `.aar` vendido em `app/libs/`), com o mesmo texto acentuado
  usado nos testes anteriores e comparações de dupla (homem+mulher conversando).
- **Candidatos testados e rejeitados:**
  - `vits-coqui-pt-cv` (Common Voice pt-BR, single-speaker): qualidade rejeitada de
    ouvido ("terrível") antes mesmo de chegar a testar profundamente.
  - OmniVoice (k2-fsa, zero-shot, 600+ idiomas): descartado sem nem baixar - não tem
    export oficial pro sherpa-onnx/Android (issue aberta e sem resposta no repo oficial),
    roda em PyTorch+GPU, sem variante CPU/mobile.
  - Pocket TTS Portuguese 24L (Kyutai, `pip install pocket-tts`): testado de verdade
    (biblioteca oficial, voz `rafael`). Mais lento que tempo real neste PC (RTF≈1,46,
    contra ≈0,28 do Supertonic) rodando em PyTorch com só 2 threads - sinal ruim pro
    aparelho. E o bundle `portuguese_24l` da Kyutai usa formato de arquivo diferente do
    único pacote Pocket TTS que o sherpa-onnx publica oficialmente (inglês, formato
    antigo) - não dá pra simplesmente declarar no manifest, precisaria de motor novo.
- **Decisão:** adotar **Supertonic 3 int8** (`sherpa-onnx-supertonic-3-tts-int8-2026-05-11`,
  release oficial do k2-fsa/sherpa-onnx, ~145 MB) como **único** motor de voz do app.
  Piper (`vits`/`vits-dual`/`piper`/`piper-dual`) e Kokoro (`kokoro`) removidos de
  `RadioVoicePackageRepository.kt` e `LocalRadioVoiceEngine.kt` - inclusive o modo
  `mixed` (ADR-015) e o hack de `ç→ss`/siglas do espeak-ng (`speakable()`), que eram
  específicos do fonemizador do Piper e não fazem sentido pro Supertonic (que já lê "ç"
  nativamente sem tratamento). `RadioVoicePackageConfig` perdeu `engine`/`vits`/`kokoro`/
  slots "mixed" - agora é sempre um `SupertonicConfig` só, com `femaleSpeakerId`/
  `maleSpeakerId` selecionando o `sid` (0-9) do mesmo `voice.bin`.
  - Locutores: **Fran** (antes "Frankie", trocou de gênero) = voz `F2` (sid 1, "alegre,
    jovem"); **Nico** = voz `M1` (sid 5, "animado, confiante"). Escolhidos numa
    comparação as cegas com 4 duplas (M2+F2, M5+F3, M3+F4, M1+F5) - o usuário pediu essa
    combinação especificamente depois de ouvir a dupla 4 (M1+F5) e gostar mais do M1 com
    o F2 do que com o F5.
  - Mapa `sid`→voz confirmado batendo pitch médio dos áudios de teste contra a ordem
    alfabética de montagem do `voice.bin` (script `generate_voices_bin.py` do próprio
    sherpa-onnx): `F1..F5 = sid 0..4`, `M1..M5 = sid 5..9`.
  - `speed` do manifest não é mais herdado do valor do Piper (`0,78`) - Supertonic
    recomenda oficialmente `0,9-1,5`; pacote atual usa `1,0`. `numSteps` (qualidade x
    velocidade do denoising, default oficial 5) subiu pra `10` no manifest.
  - Pacote fonte: `voice-models/supertonic-3-int8/` (modelo + `manifest.json`, é
    literalmente a raiz do zip); importável: `voice-models/Pailer-Radio-Voices-
    Supertonic3-Fran-Nico.zip` (~124 MB). Scripts de teste em
    `voice-models/supertonic-3-int8-scripts/`.
- **Bug sério achado durante a limpeza:** o código do Supertonic existia desde antes
  (`SupertonicConfig`, branch em `loadEngine`) mas **nunca setava `lang`/`numSteps`** na
  chamada real (`GenerationConfig` só tinha `speed`/`sid`/`silenceScale`) - `extra["lang"]`
  ausente cai no default `"en"` do C++ (`offline-tts-supertonic-impl.cc`), então a
  primeira vez que esse motor rodasse de verdade teria sintetizado em modo inglês pra
  texto em português, sem nenhum erro visível. Corrigido junto com a limpeza:
  `GenerationConfig(numSteps = config.numSteps, extra = mapOf("lang" to config.lang))`.
- **Limpeza de disco:** removidos ~2,7 GB de artefatos Piper/Kokoro do workspace
  (`voice-models/kokoro/` sozinho era 1,6 GB) - tudo reconstruível a partir de fontes
  públicas (receita de quantização do Kokoro já documentada no histórico deste arquivo,
  ADR-013), então apagado direto em vez de arquivado.
- **Pendências conhecidas, não resolvidas nesta ADR:**
  1. **Hiato/acentuação:** Supertonic é *character-level* (sem fonemizador/regras por
     idioma - só NFKD, ver `offline-tts-supertonic-unicode-processor.cc`), então não tem
     parâmetro que force acentuação. A palavra "tardio" saiu sem separar o hiato
     (tar-diô em vez de tar-DI-o); forçar a grafia pra "tardío" no texto corrigiu.
     Enquanto não há uma lista de palavras problemáticas, isso é responsabilidade de
     quem escreve o texto do boletim (`RadioBulletin.kt`/`FallbackRadioScriptWriter`),
     não do motor - vale revisar se aparecerem outras palavras de hiato mal pronunciadas.
  2. **Fragmentos curtos instáveis:** sentenças isoladas com ≤4 palavras (ex.: "Viu?",
     "Bora pra próxima notícia.") saem atropeladas/distorcidas quando sintetizadas
     sozinhas - o motor precisa de contexto textual mínimo pra estabilizar a duração.
     `LocalRadioVoiceEngine.kt` **ainda faz uma chamada só por linha do script**, sem
     quebrar em sentenças nem aplicar essa regra de merge - só foi validado em Python
     (`voice-models/supertonic-3-int8-scripts/run_test_supertonic_m1f2_v3.py`). Se o
     roteirista (`FallbackRadioScriptWriter`/redator local) gerar falas muito curtas,
     replicar essa lógica de split+merge+pausa no motor antes de considerar resolvido.
  3. **Música de fundo:** testada e aprovada fora do app (ffmpeg, `-25dB`, fade in/out,
     bed `vinhetas/ES_Monday Moonwalk - Guustavv.mp3`) mas **ainda não implementada**
     no pipeline real - plano é misturar em `LocalRadioVoiceEngine.synthesize()` antes do
     `writeWav()`, com um bed pré-convertido pra WAV no sample rate do motor (evita
     decodificar MP3 em runtime).

## ADR-019 — Passagens entre música e boletim, modo do boletim fixo, buffer de 3 boletins

- **Contexto:** três pedidos do usuário na mesma sessão, todos sobre deixar o pipeline do
  boletim mais robusto/redondo depois da troca pro Supertonic (ADR-018): (1) uma
  transição curta em volta do boletim em vez de corte seco música→voz→música; (2) tirar
  a escolha de "modo" do boletim, já que sempre vai ser bate-bola; (3) manter um buffer
  de boletins prontos em vez de preparar só 1 com 1 música de antecedência.
- **Decisão — passagens:** `passagem.mp3`/`passagem 2.mp3` (soltos por vídeo o usuário em
  `vinhetas/`) viraram `res/raw/passagem_1.mp3`/`passagem_2.mp3`. `LocalTuneViewModel.
  playPassagem()` alterna entre as duas (`nextPassagemIndex`, incrementa a cada uso, não
  reseta) e reaproveita `playVinhetaResource()` (mesmo `MediaPlayer.create` de recurso,
  sem gap). Entra em dois pontos: início de `speakNextNewsBreak()` (envolve os dois
  caminhos - áudio do buffer e fallback TTS Android, porque entra antes da bifurcação) e
  início de `finishNewsBreak()`.
  - **Cuidado de robustez:** `finishNewsBreak()` só zerava `speakingNews` de imediato; como
    agora ela dispara a passagem final antes de liberar a música, isso desarmava o
    watchdog (ADR-009) bem na janela em que a passagem podia travar. Adicionada guarda de
    reentrância (`newsBreakEnding`) e `speakingNews` só vira `false` depois da passagem
    tocar - o watchdog continua cobrindo essa janela.
- **Decisão — modo fixo:** removida a seção "Modo" (Desativado/Só manchetes/Conversa dos
  locutores) da tela "Boletins da rádio" e `setRadioBulletinMode()`.
  `loadRadioBulletinUiState()` agora **ignora** qualquer valor salvo em
  `KEY_RADIO_BULLETIN_MODE` e força sempre `RadioBulletinMode.Dialogue` - inclusive pra
  quem já tinha "Desativado" salvo de uma sessão anterior, sem precisar de UI pra
  corrigir. O enum `RadioBulletinMode` continua existindo (`Off`/`Headlines` viram código
  morto, não removido agora) porque `RadioBulletinRepository.loadScripts()` ainda faz
  `when` sobre ele.
- **Decisão — buffer de 3:** trocado `preparedBulletinScript`/`preparedBulletinFile`/
  `preparedBulletinIndex` (slot único, só 1 boletim preparado com 1 música de
  antecedência) por `bulletinBuffer: ArrayDeque<PreparedBulletin>` (`script` + `file?`),
  mantido em `BULLETIN_BUFFER_TARGET = 3` via `refillBulletinBuffer()`:
  1. Prepara **um de cada vez** (nunca dois engines de voz carregados juntos, mesma
     cautela de TTS.md/ADR-003), em loop dentro de uma única coroutine até encher.
  2. Disparada em três pontos: fim da carga inicial de `newsBulletins` em
     `startRadioNewsMode()`, logo após consumir um item em `speakNextNewsBreak()` (repõe
     assim que gasta), e depois de `reloadNewsBulletinsIfNeeded()` recuperar feeds que
     tinham falhado. O antigo gatilho "1 música antes do boletim" (`onMediaItemTransition`,
     checkpoint `interval - 1`) foi removido - o buffer fica cheio continuamente em vez de
     just-in-time.
  3. **Previsão de contexto (intro "você acaba de ouvir" / fechamento "próxima música")
     pra itens 2 e 3 do buffer:** como esses boletins são preparados bem antes de saber de
     verdade qual música vai tocar quando chegarem a vez, `refillBulletinBuffer()` calcula
     `songsUntilFire` (quantas músicas faltam até esse item específico do buffer disparar,
     a partir de `completedRadioSongs % songsBetweenBulletins` + posição no buffer) e
     espia a fila fixa da sessão (`player.getMediaItemAt(currentMediaItemIndex +
     songsUntilFire - 1)` pro "último tocado", `+ songsUntilFire` pro "próximo") -
     extensão do mesmo truque de 1-música-de-antecedência que `prepareUpcomingBulletin()`
     já fazia (ADR anterior), só que agora precisa alcançar 2-3 músicas à frente.
  4. **Risco aceito:** se o usuário pular música manualmente entre o preparo e a hora de
     tocar, a previsão fica desatualizada e a Fran pode citar o artista errado na chamada
     de música - degrada pra frase genérica sem citar artista quando o índice previsto cai
     fora da fila (`buildFranCloser`), nunca quebra o boletim. Não verificado contra a
     faixa real no momento de tocar (manteria a mesma tolerância a risco que o sistema de
     1-música-de-antecedência já tinha).
  5. **Buffer vazio (sessão recém-começada, feed ainda carregando, ou consumo mais rápido
     que o preparo):** `speakNextNewsBreak()` cai pro mesmo comportamento de emergência de
     antes - monta o boletim ao vivo direto do `baseBulletin` (sem `enhanceScript`/redator
     local, lento demais pra essa hora), só com `withLastPlayedIntro`/
     `withPhilosophicalCloser` usando o contexto real de agora (sem previsão, sem risco).
- **Não mudar sem:** ler `refillBulletinBuffer()` inteira antes de mexer no timing de
  boletim - a previsão de índice de fila (item 3 acima) é a parte mais frágil, fácil de
  quebrar com off-by-one se `songsBetweenBulletins` ou a lógica de `completedRadioSongs`
  mudar de forma. Ainda não testado num aparelho real com sessão longa (várias trocas de
  música) - só compilado e revisado.
- **Atualização (02/09/2026, buffer não reagia à chavinha da voz local):** confirmado em
  teste real - usuário ligou a voz local no meio de uma rádio já tocando e os 3 boletins
  seguintes continuaram saindo com a voz do Android. Causa: `setRadioVoiceEnabled()` só
  atualizava a preferência salva; `refillBulletinBuffer()` só reconsulta
  `radioVoiceState.value.isEnabled` quando prepara um item **novo**, então os 3 itens já
  prontos no buffer (preparados com o valor antigo) ficavam intocados até serem consumidos
  um a um. Corrigido chamando `clearBulletinBuffer()` + `refillBulletinBuffer()` direto em
  `setRadioVoiceEnabled()` - qualquer mudança na chavinha descarta o buffer e prepara tudo
  de novo do zero com o valor atual, então o próximo boletim (não o 4º) já reflete a troca.
- **Atualização (02/09/2026, ajuste de performance + música de fundo implementada):**
  medido em campo (Motorola Edge 40): `numThreads=2` → 410s pra sintetizar um boletim de
  6 falas; subir pra `numThreads=4` **piorou** pra 496s, com variância enorme entre falas
  (42-144s cada) - sinal de throttling térmico, não falta de paralelismo (RAM não é
  gargalo: app usa ~1,44GB PSS num aparelho com ~4GB livres). Ajustado pra `numThreads=3`
  e `numSteps` de 10 pra **5** (default do próprio Supertonic) como meio-termo
  velocidade/qualidade - ainda sem medição de campo desses dois juntos.
  - **Música de fundo (ADR-018, pendência 3) implementada de verdade**: pacote de voz
    ganhou campo opcional `backgroundMusic` (PCM16 mono **sem cabeçalho WAV**, no mesmo
    sample rate do motor - hoje 44100Hz, sem reamostragem) + `backgroundMusicVolume`
    (linear, 0,05623 ≈ -25dB). `LocalRadioVoiceEngine.mixBackgroundMusic()` soma o bed por
    cima da voz já sintetizada (fade in 1,5s / fade out 3s, repete em loop se o boletim for
    mais longo que o bed) antes de escrever o WAV final. Sem `backgroundMusic` no
    manifest, comportamento idêntico a antes (nenhum custo). `backgroundMusic` é uma
    **lista** (aceita string única por compatibilidade) - com mais de um arquivo, um é
    sorteado por boletim via índice num companion object (`nextBackgroundMusicIndex`,
    sobrevive entre requests dentro do mesmo processo `:radio_voice`). Beds atuais:
    `voice-models/supertonic-3-int8/bed1.pcm` (`ES_Save It for a Rainy Day - Margareta`)
    e `bed2.pcm` (`ES_Devil Disguised - Torii Wolf`), convertidos via
    `ffmpeg -ar 44100 -ac 1 -f s16le` - o bed original (`Monday Moonwalk`) foi trocado
    pelo usuário por esses dois.
  - **Teste de boletim (`testRadioBulletin()`) não tinha passagem nem música**: passagem
    porque só existia em `speakNextNewsBreak()`/`finishNewsBreak()` (fluxo da rádio ao
    vivo), nunca no botão de teste - corrigido com `playTestAudioWithPassagem()`
    (envolve `playPassagem()` antes/depois, igual o boletim real). Música de fundo
    simplesmente não existia ainda em lugar nenhum - resolvido pela implementação acima,
    que vale pros dois caminhos (rádio ao vivo e teste) porque mora dentro do
    `LocalRadioVoiceEngine.synthesize()` compartilhado.
- **Atualização (02/09/2026, timeout do teste de boletim + progresso ao vivo):** usuário
  reportou que o teste de boletim (`testRadioBulletin()`) às vezes esbarrava no timeout
  antigo de 90s, e depois que 180s **também não bastou** pra uma notícia real (RSS +
  redator local + síntese de todas as falas) - `LOCAL_VOICE_BULLETIN_TEST_TIMEOUT_MS`
  subiu pra **300s**. Esse teste roda isolado (não usa `armAnnouncementWatchdog`), então
  não interage com o timeout de 90s do watchdog geral da rádio ao vivo
  (`ANNOUNCEMENT_WATCHDOG_TIMEOUT_MS`), que continua o mesmo - a folga aqui é de graça.
  Junto, pedido do usuário de mostrar progresso real durante o teste ("pra eu saber que
  não travou"): `loadLiveTestScript`/`enhanceScript` (`RadioBulletin.kt`) e
  `requestLocalVoiceSynthesis` (`LocalTuneViewModel.kt`) ganharam um parâmetro
  `onProgress: (String) -> Unit`, e `RadioVoiceSynthesisService` ganhou um novo código de
  resultado `RESULT_PROGRESS` (nunca resolve a coroutine que espera o resultado final, só
  `RESULT_OK`/`RESULT_FAILED` fazem isso) enviado uma vez por fala sintetizada -
  `LocalRadioVoiceEngine.synthesize()` agora aceita um callback `onLineDone(index, total)`
  pra isso. `testRadioBulletin()` liga tudo: "Buscando notícia" → "Escrevendo com o
  redator local..." (só se instalado) → "Sintetizando vozes: fala N de M..." Mensagens
  reais, não simuladas - nenhuma etapa fake só pra parecer progresso.
- **Atualização (02-03/09/2026, ajuste fino de numSteps + fragmentos curtos corrigidos em
  produção):** depois de confirmar em campo que `numSteps=5`/`numThreads=3` reduziu bem
  o tempo de síntese, `numSteps` subiu pra **6** (ainda bem abaixo do 10 original, sobra
  de margem pra um pouco mais de qualidade sem voltar ao custo de antes).
  - **Pendência 2 da ADR-018 corrigida em produção**: `LocalRadioVoiceEngine.synthesize()`
    agora quebra cada fala em sentenças (`splitIntoSentences`), funde de volta as com
    ≤4 palavras na vizinha (`mergeShortSentences`, mesmo algoritmo validado em Python -
    `run_test_supertonic_m1f2_v3.py`) e sintetiza cada sentença separada com uma pausa
    pequena (`INTRA_LINE_GAP_S=0,15s`) entre elas - a pausa maior entre falas de
    personagens diferentes continua vindo do gap de 0,18s já existente. Faz por fala
    (`synthesizeLine()`), então o resto do pipeline (progresso por fala, gap entre
    personagens) não mudou.
  - **Verificação da qualidade do redator local**: rodado um teste real em campo pra
    conferir pontuação/acentuação. O texto que o Qwen3 gerou saiu correto onde chegou a
    gerar (acentos e pontuação certos, ex.: "...uma embarcação que, segundo o governo
    americano, era usada como estação de abastecimento..."), mas **esse teste específico
    falhou estruturalmente**: o modelo devolveu só 1 objeto JSON solto (sem colchetes de
    array), `parseGeneratedLines` não conseguiu reconstruir o array e caiu no
    `JSONException` → fallback determinístico, exatamente como o design já previa (ver
    ADR-002, "variância normal do modelo... não é bug"). Não é uma regressão introduzida
    pela 5ª fala (reflexão) - já era um comportamento conhecido, só não tinha sido pego
    num teste ao vivo com log até agora.

## ADR-020 — Diagnóstico de desempenho do redator local (Qwen3 4B) + persistência do buffer + limpeza de threads

- **Contexto:** depois da troca de motor do redator local pro Qwen3 4B (2,4x mais
  parâmetros que o 1.7B, ADR-002), o usuário reportou geração muito lenta e um teste real
  que falhou, mesmo depois de subir `threads` de 2 pra 5 no manifest do pacote. Pediu
  acompanhamento em tempo real (logs no celular) pra diagnosticar. Sessão inteira (dia
  03/09/2026, ~5h) girou em torno disso, com vários achados encadeados.
- **Instrumentação adicionada primeiro** (pré-requisito pra qualquer diagnóstico real):
  `pailer_llama_jni.cpp` passou a logar `perf:` por fase - tempo de carga do modelo,
  tokens do prompt, prefill (tempo+tok/s), progresso do decode a cada token (tokens
  gerados/teto, tok/s, trecho do texto parcial a cada 3s) e motivo de parada
  (`json_closed`/`eog`/`timeout`/`decode_error`/`predict_cap`). Callback `LlamaProgressListener`
  (antes `fun interface` só com `onProgress(current,max)`, virou interface com
  `onPhase(phase,elapsedMs)` + `onProgress(current,max,elapsedMs)`) leva isso pra Kotlin,
  que agora expõe fase real ("Lendo a matéria...", "Escrevendo... NN% (Xs)") no
  `RadioBulletinBufferStatusCard`, substituindo a mensagem estática de antes.
- **Achado 1 — teto de threads mudo:** `pailer_llama_jni.cpp` travava threads em
  `min(threads, 4)` **sem log nenhum avisando** - o `threads=5` do manifest nunca teve
  efeito real no decode. Corrigido pra acompanhar o range liberado no Kotlin
  (`RadioWriterPackageRepository.coerceIn(1,8)`), com log `threads pedidos=X usados=Y`
  pra nunca mais isso passar despercebido.
- **Achado 2 — teto de tokens mudo:** mesmo padrão, `predict` travava em `min(max_tokens,
  260)` mesmo com o manifest pedindo até 420 (depois 380) - risco de truncar o roteiro
  antes do JSON fechar. Corrigido pra `min(max_tokens, 420)`.
- **Achado 3 — causa raiz real da lentidão era concorrência, não threads:** o botão
  antigo "Testar notícia real" (`testRadioBulletin()`/`loadLiveTestScript()`) chamava o
  redator local **sem passar pelo `llmGenerationMutex`** que já serializa o preparo de
  fundo (Nível 1 `prewarmCoreBuffer`/Nível 2 `refillBulletinBuffer`). Rodando o teste
  manual enquanto o preparo automático também gerava, as duas chamadas nativas
  disputavam CPU/threads ao mesmo tempo - dado real capturado em campo (duas cargas de
  modelo por Qwen3 4B ~9s uma da outra, mesmo processo). **Decisão:** removido o botão de
  teste manual inteiro (`testRadioBulletin`, `loadLiveTestScript`,
  `LOCAL_VOICE_BULLETIN_TEST_TIMEOUT_MS`) - o card do buffer (`RadioBulletinBufferStatusCard`,
  botão "Reproduzir boletim pronto"/bolinhas por posição) virou o único caminho de teste,
  porque o preparo automático que ele expõe já passa pelo mutex certo. Sem essa
  concorrência, 5 threads sozinho já resolveu: da rodada seguinte em diante, nenhuma
  geração estourou timeout.
- **Achado 4 — mais threads não ajuda nesse aparelho, pode piorar bastante:** com a
  concorrência eliminada, testado threads=8 (núcleos totais do Dimensity 1200, `adb
  shell nproc`) tanto no redator quanto na síntese de voz:
  - Redator: prefill não mudou (191-213s em 5, 6 ou 8 threads - mesma faixa), mas o
    **decode colapsou** de ~2,0-2,6 tok/s (5 threads) pra 0,3-1,3 tok/s (8 threads,
    variando bastante e caindo ao longo da geração - sinal de throttling térmico, não
    falta de paralelismo). Duas rodadas reais com 8 threads **estouraram o timeout
    interno de 340s** (`motivo=decode_error` porque o abort por timeout pegou o
    `llama_decode` no meio da chamada, não entre tokens - o motivo deveria ter sido
    "timeout"; ver pendência abaixo), a primeira falha real de timeout do dia inteiro.
  - Voz (Supertonic): uma única fala de 159 caracteres levou 136s com 8 threads, contra
    a média de ~68-83s/fala já registrada no teste de campo antigo (2 threads=410s/6
    falas, 4 threads=496s/6 falas, ver ADR anterior sobre threads da voz). Confirma o
    mesmo padrão "mais threads = mais calor = pior" já suspeitado, agora de forma mais
    extrema.
  - **Decisão final:** redator voltou pra **threads=5** (manifest do pacote
    `radio-writer-models/qwen3-4b-q4km/manifest.json`, reempacotado e reimportado no
    aparelho - único valor com dado real bom depois da correção da concorrência). Voz
    voltou pra **numThreads=2** (`LocalRadioVoiceEngine.kt`) - o único valor da própria
    história do projeto com medição real boa; o "3" que estava em produção antes desta
    sessão nunca foi medido de verdade, era só um meio-termo escolhido sem dado. Teto
    máximo no código (Kotlin `coerceIn`/nativo `min`) ficou em 8 como limite de segurança,
    não como recomendação - não subir de novo sem medir com log por fase primeiro.
- **Achado 5 — prefill pesa mais que o decode:** com o prompt do redator em ~1150-1190
  tokens (o exemplo few-shot grande do ADR-002/pendência de "eco da instrução"), o
  prefill (~190-210s) consistentemente pesa mais no tempo total que o decode
  (~90-130s, gerando de ~200 a ~280 tokens). Quem quiser reduzir tempo de verdade no
  futuro, encolher o prompt tem mais efeito que ajustar threads.
- **Bug de crash achado implementando persistência:** `LocalTuneViewModel.init{}` tentou
  ler `radioBulletinBufferState` (Compose `mutableStateOf`) de dentro de
  `viewModelScope.launch(Dispatchers.Default)` logo na abertura do app -
  `IllegalStateException: Reading a state that was created after the snapshot was taken`,
  **crash em 100% das aberturas** (visto em campo, revertido rápido). Lição: nunca ler/
  escrever Compose State de fora do dispatcher padrão do `viewModelScope`
  (`Main.immediate`) logo na construção do ViewModel, mesmo que pareça inofensivo - ver
  também `[[feedback_pailer_fm_viewmodel_mainthread]]` (mesma classe de bug do
  `showToast()`, agora pra leitura de `State` em vez de UI direta).
- **Novo: persistência do buffer do Nível 1 em disco.** Antes, `coreBuffer` (núcleo
  pré-aquecido) e os `.wav` da síntese ficavam só em memória/`cacheDir` - qualquer
  reinício do processo (crash, `adb install -r`, Android matando por memória) perdia
  tudo, mesmo boletins já prontos. Agora:
  - Áudio do núcleo vai pra `filesDir/radio_bulletins_ready/` (não `cacheDir`) -
    `LocalRadioVoiceEngine.CORE_BUFFER_DIR_NAME`, único nome compartilhado com o
    ViewModel. `filesDir` é privado do app e não é limpo automaticamente pelo Android
    (só em "Limpar dados", bem mais deliberado que "Limpar cache").
  - `LocalTuneViewModel.saveCoreBufferManifest()`/`loadCoreBufferManifest()` gravam/leem
    um `manifest.json` (JSON simples, sem lib de serialização) nessa mesma pasta,
    espelhando o `coreBuffer` inteiro (script + falas + nomes dos `.wav`). Salva depois
    de toda mudança (item novo, item consumido); carrega uma vez no `init` do ViewModel,
    validando que cada `.wav` referenciado ainda existe em disco antes de restaurar.
  - Varredura de órfão: toda vez que salva, apaga do disco qualquer `.wav` na pasta que
    não esteja mais referenciado por nenhum item do `coreBuffer` atual (item consumido/
    substituído).
  - **Só o Nível 1 é persistido**, de propósito - o Nível 2 (`bulletinBuffer`) é
    específico da rádio ativa e sempre limpo em `startRadioNewsMode()`, não sobreviveria
    a uma nova sessão mesmo se fosse salvo.
- **Novo: prévia com fala de abertura + feedback claro sem áudio.** O botão de
  reproduzir prévia tocava só o "miolo" (falas 2-5) - o usuário relatou que a prévia
  "não tinha pé nem cabeça" porque faltava a fala 1 (a Fran apresentando o assunto da
  notícia). Como o Nível 1 gera a fala 1 **sem** referência a faixa real (prompt já
  trata "faixa desconhecida" como "emenda direto pra notícia"), ela já sai genérica e
  utilizável fora de contexto de rádio - `prewarmCoreBuffer()` agora sintetiza essa fala
  sozinha também (`PreparedNewsCore.introFile`, reaproveitando `requestCoreSynthesis`
  com uma lista de 1 linha, sem caminho novo no serviço de voz) e a prévia toca
  intro→núcleo em sequência. Também corrigido bug onde tocar a prévia num item sem
  áudio (ex.: roteiro caiu pro fallback determinístico, que nunca tem `coreFile`) não
  fazia **nada visível** - `readyCount` contava "texto pronto" mas o botão parecia
  habilitado; separado em `hasPlayableAudio` (áudio de verdade) pro botão, e mensagem
  clara ("Esse boletim não tem áudio pronto...") em vez de silêncio.
- **Novo: corte de frase mais inteligente.** `limitWords()` (usado em `RadioScriptLine.text`
  entre outros) cortava sempre no limite exato de palavras, deixando frases penduradas
  tipo "...pede que alguém a dê a porta de uma cela, como." quando a fala saía mais longa
  que o normal. Agora tenta achar a última frase completa (`.`/`!`/`?`) que ainda cabe no
  limite antes de cortar; só cai no corte bruto por palavra se nem a primeira frase
  inteira couber (comportamento idêntico ao anterior pros casos sem pontuação, ex.:
  título/nome de faixa).
- **Novo: cooldown de 3 minutos entre boletins consecutivos do Nível 1**
  (`COOLDOWN_BETWEEN_BULLETINS_MS` em `LocalTuneViewModel`), com status regressivo a
  cada 15s. Só espera se ainda falta preparar mais item (não trava 3min à toa depois do
  último). Motivação: throttling térmico visto se acumulando em sessões de geração
  seguida (prefill caindo de 7,3 pra 5,6 tok/s ao longo de rodadas consecutivas).
- **Pendências não resolvidas nesta sessão:**
  1. **`stop_reason="decode_error"` mascara timeout real** - quando o abort por deadline
     expira **dentro** de uma chamada `llama_decode()` (não entre tokens, onde o
     `expired()` já é checado explicitamente), `llama_decode` retorna erro e o motivo
     registrado é "decode_error" em vez de "timeout" - visto em campo com
     `total=340024ms`/`340004ms`, batendo exatamente no timeout interno. Log continua
     útil (dá pra inferir pelo `total_ms`), mas vale corrigir a categorização certa.
  2. **Job duplicado em reset rápido consecutivo:** `pauseBulletinPreparation`/reset
     cancela `corePrepJob` e zera a referência, mas cancelamento de coroutine é
     cooperativo - se o job antigo estiver bloqueado numa chamada nativa síncrona (JNI),
     `corePrepJob?.isActive` já volta `false` assim que `.cancel()` é chamado (mesmo com
     o corpo ainda rodando), então um reset logo em seguida inicia um job novo **de
     verdade em paralelo** com o antigo ainda terminando. `llmGenerationMutex`/
     `voiceSynthesisMutex` evitam que os dois rodem o motor nativo ao mesmo tempo (sem
     risco de crash/corrupção), mas cada job tem seu próprio cooldown e contagem de
     `coreBuffer.size` - visto em campo pulando o cooldown de 3min (só ~63s) e
     restaurando manifest "0/0" depois de um reinício nessa janela. Precisa de
     `job.cancelAndJoin()` (ou equivalente) esperando o job antigo terminar de verdade
     antes de iniciar um novo, não só verificar `isActive`.
  3. **Vazamento raro de token não-português:** uma geração produziu "警报" (caracteres
     chineses) no meio de uma fala em português - não travou nada (`hasAccentuation()`
     ainda passou), mas seria pronunciado errado pelo sintetizador. Sem solução
     aplicada; se virar frequente, considerar checagem de script Han/Latin na validação
     de `parseGeneratedLines`, mesma família de rede de segurança do
     `hasAccentuation()`.

## ADR-021 — Bed do boletim sem direitos autorais, volume do boletim, buffer de 5 e personalidade de Fran/Nico

- **Contexto:** quatro pedidos do usuário na mesma sessão (04/09/2026): (1) as duas
  músicas de fundo do boletim eram do Epidemic Sound (direitos autorais reais, não só
  hipotético) e precisavam sair; (2) o boletim tocava visivelmente mais baixo que a
  música normal, obrigando o usuário a reajustar o volume toda vez; (3) buffer de
  boletins prontos pequeno demais pra cobrir a variedade de assunto que ele queria; (4)
  Fran e Nico soavam sempre com o mesmo registro sério/cínico, sem humor nem
  personalidade própria.
- **Decisão — bed sem direitos autorais:** `ES_Save It for a Rainy Day - Margareta.mp3`/
  `ES_Devil Disguised - Torii Wolf.mp3` (Epidemic Sound, ver ADR-019) apagados de
  `vinhetas/`. Substituídos por `Concrete Tunnel.mp3`/`Concrete Tunnel 2.mp3` (autorais,
  geradas pelo usuário), convertidas com o mesmo comando de sempre
  (`ffmpeg -ar 44100 -ac 1 -f s16le`) pra `voice-models/supertonic-3-int8/bed1.pcm`/
  `bed2.pcm`. `manifest.json` não mudou (já referenciava só os nomes de arquivo).
  `voice-models/Pailer-Radio-Voices-Supertonic3-Fran-Nico.zip` reempacotado com os PCMs
  novos (via `zipfile` do Python, só substituindo as 2 entradas - `zip`/`7z` não
  disponíveis no ambiente) - falta o usuário reimportar esse zip no app (fluxo manual
  de sempre, Configurações > pacote de voz) pra valer no aparelho.
- **Decisão — volume do boletim:** `LocalRadioVoiceEngine` não tinha NENHUM ganho na
  voz sintetizada (só o bed de fundo tinha `backgroundMusicVolume`) - o pico de
  amplitude que sai do Supertonic é bem mais baixo que uma faixa mixada/masterizada.
  Adicionado `normalizeVoiceLevel()`, chamado no fim de `synthesizeLine()` (ponto único
  por onde toda fala passa, incluindo `spliceEdges`): normaliza por PICO (não ganho
  fixo) até `VOICE_TARGET_PEAK = 0.95f`, com teto `VOICE_MAX_GAIN = 4f` (12dB) pra não
  amplificar demais um trecho quase mudo por erro de síntese. Normalizar por linha
  (não no áudio final já com bed) significa que o "núcleo" pré-aquecido
  (`synthesizeCore`, sem contexto de faixa ainda) já sai no nível certo, e
  `spliceEdges` não precisa recalcular nada ao colar as pontas.
- **Decisão — buffer de 5:** `BULLETIN_BUFFER_TARGET` (ADR-019) subiu de 3 pra 5 em
  `LocalTuneViewModel.kt`. Nenhuma outra mudança de lógica - `refillBulletinBuffer()`
  já era genérico o bastante pra qualquer tamanho de alvo.
- **Decisão — feeds diversificados:** `NewsBulletinRepository.FEEDS` trocou o feed
  genérico `super.abril.com.br/feed/` (todas as editorias misturadas) por 6 feeds mais
  específicos, cobrindo os temas pedidos - fatos históricos, bizarros, científicos,
  curiosidades de mundo e do Brasil - sem inventar fato nenhum (continuam sendo notícia
  real de RSS, só de fonte/editoria diferente):
  `g1.globo.com/rss/g1/brasil`, `g1.globo.com/rss/g1/planeta-bizarro`,
  `super.abril.com.br/historia/feed/`, `super.abril.com.br/mundo-estranho/feed/`,
  mantendo `g1 Mundo`/`g1 Ciência e saúde`/`Olhar Digital`/`BBC Brasil` de antes (8
  feeds no total). Cada URL foi testada manualmente (`curl`) antes de entrar na lista -
  `aventurasnahistoria.uol.com.br` (não resolve DNS), `megacurioso.com.br/feed`
  (SPA React, não RSS de verdade) e `g1.globo.com/rss/g1/curiosidades` (canal existe mas
  devolve 0 itens) foram descartados por não funcionarem de verdade, não só por
  suposição. `NEWS_LIMIT` subiu de 8 pra 16 (2 por feed) porque com exatamente 8 feeds
  e limite 8, o `interleave()` (round-robin) parava na 1ª rodada e nunca dava uma 2ª
  chance pra nenhum feed.
- **Decisão — personalidade de Fran/Nico:** `buildSystemInstructions()` (compartilhada
  entre redator local Qwen3 e Gemini, ver ADR-002/ADR-020) ganhou um parágrafo de
  personalidade antes da estrutura de 6 falas: Fran tem senso de humor, faz alívio
  cômico, traz informação útil/relevante e levanta o astral; Nico continua cético/
  sério/político (mantém o conhecimento de capitalismo/imperialismo/lobby da ADR-014)
  mas ganhou humor ácido/seco que "cutuca a ferida" do assunto (nunca da Fran) e uma
  atitude de "porra-louca"/motoqueiro de jaqueta de couro que já viveu bastante - via
  tom, não citação literal repetida. As falas 2-4 e 6 da estrutura ganharam permissão
  explícita pra humor/sarcasmo (antes só falavam em "leitura crítica"/"otimismo
  realista"/"provocação"). O exemplo few-shot (matéria fictícia de trânsito) foi
  reescrito pra DEMONSTRAR esse tom novo, não só descrevê-lo -
  [[feedback_small_llm_fewshot]] (memória) confirma que modelo pequeno precisa de
  exemplo concreto, descrição abstrata sozinha não bastava mesmo antes.
  - **Fora de escopo, deliberado:** os bancos fixos de fallback
    (`TOPIC_BANK`/`NICO_DETAILS`/etc, usados só quando LLM local E Gemini falham os
    dois) continuam no tom sério/cético antigo, sem o humor novo - ver comentário
    acima de `TOPIC_BANK` no código. Esse caminho é raro o bastante (rede de segurança)
    pra não justificar reescrever ~15 categorias x otimista/pessimista agora.
- **Atualização (04/09/2026, cooldown térmico pulado quando escreve via Gemini):**
  usuário observou que o cooldown de 3min entre boletins (`COOLDOWN_BETWEEN_BULLETINS_MS`,
  ADR-020) foi criado pensando no esforço do redator LOCAL (Qwen3) + síntese de voz
  somados, mas hoje boa parte da escrita sai pelo Gemini (nuvem) - achou que o cooldown
  não fazia mais sentido. Verificado antes de mexer: a síntese de voz (Supertonic)
  continua **sempre** local mesmo com Gemini (decisão de privacidade, ver
  `enhanceScript`/ADR-020 - "a síntese de voz continua 100% local... o áudio final nunca
  sai do aparelho") e foi UMA DAS DUAS causas originais do throttling medido (2t=410s/
  4t=496s) - cortar o cooldown sempre arriscaria voltar ao throttling só com a síntese,
  mesmo sem o redator local rodando. Perguntado ao usuário com as opções (remover
  sempre / remover só com Gemini / só reduzir tempo / manter) - escolheu **remover só
  quando o Gemini escreveu essa matéria especificamente**, mantendo o cooldown normal
  quando cai pro redator local Qwen3 (sem chave configurada, ou Gemini falhou nessa
  hora). Implementado com um callback novo `enhanceScript(..., onWriterUsed: (Boolean)
  -> Unit)` em vez de campo novo em `RadioScript`/`RadioScriptSource` - só
  `prewarmCoreBuffer()` (`LocalTuneViewModel.kt`) precisa distinguir Gemini de local,
  nenhum outro consumidor faz isso por design (ver comentário em
  `RemoteGeminiRadioScriptWriter.writeContextual` sobre reusar
  `RadioScriptSource.LocalLlm` pros dois). `usedGemini` guarda o resultado do callback e
  vira a condição extra (`&& !usedGemini`) no `if` que entra no laço de espera do
  cooldown.

## ADR-022 — Gemini Flash TTS experimental, multi-chave do Gemini, e correções reais no buffer de boletins/sincronização de biblioteca

- **Contexto:** sessão longa (10/09/2026) com vários pedidos do usuário, do maior
  (experimentar um motor de voz na nuvem) a bugs reais achados testando ao vivo no
  aparelho (buffer de boletins desperdiçando trabalho pronto, sincronizar biblioteca não
  pegando álbum novo).
- **Decisão — Gemini Flash TTS (experimental):** novo motor de síntese de voz pra Fran/
  Nico via `generateContent` do Gemini (`responseModalities: ["AUDIO"]`,
  `speechConfig.voiceConfig.prebuiltVoiceConfig`), coexistindo com o motor local
  (Supertonic, ADR-018) sem substituí-lo — liga/desliga em Configurações > Boletins >
  "Voz dos boletins" (`RadioBulletinSettings.ttsProvider`, `BulletinTtsProvider` em
  `RadioBulletinTts.kt`). Implementado como motor novo e isolado
  (`GeminiFlashTtsEngine.kt`), não dentro do `RadioVoiceSynthesisService`/processo
  `:radio_voice` — é chamada de rede, não código nativo instável, cancelamento
  cooperativo padrão do Kotlin já basta, sem precisar do aparato de isolamento de
  processo do motor local. Contrato de saída idêntico ao `LocalRadioVoiceEngine.
  synthesize()/synthesizeCore()` (mesmo `File?` de `.wav`, mesmas pastas cacheDir/
  filesDir) — o resto do pipeline (buffer, manifest, limpeza de órfãos) não sabe nem
  precisa saber qual motor gerou o áudio. Qualquer falha (sem chave, timeout, HTTP
  4xx/5xx, resposta sem áudio) cai pro motor local automaticamente, boletim nunca fica
  sem áudio por causa do experimento.
  - **Achados testando ao vivo (não só lidos na doc):** 20s de timeout por fala não
    bastava — os 2 modelos "preview" (`gemini-3.1-flash-tts-preview`,
    `gemini-2.5-flash-preview-tts`) às vezes demoram mais que isso pra responder, mesmo
    padrão já visto em `RemoteGeminiRadioScriptWriter.GEMINI_TIMEOUT_MS` (120s, ADR-020)
    pra escrita. Subido pra 60s (`GEMINI_TTS_TIMEOUT_MS`). `withTimeoutOrNull` embrulhando
    uma chamada bloqueante (`HttpURLConnection`, sem suspend point no meio) não cancela
    ela de verdade — só descarta o resultado se ele voltar depois do prazo — por isso o
    timeout precisa cobrir o pior caso real. Cota gratuita esgota rápido com chamada por
    fala (HTTP 429 visto em campo dentro de minutos de teste) — motivou a decisão
    seguinte.
  - **Voz do Nico:** trocada de Puck (tom "upbeat", não combinava) pra Algenib — voz
    masculina documentada com textura "gravelly"/grave entre as pré-prontas do Gemini
    TTS, mais perto do cético/durão que o personagem já tem (ADR-014/021). Instruções de
    estilo dos dois locutores (`GeminiTtsVoices` em `RadioBulletinTts.kt`) também
    suavizadas — pedido do usuário depois de ouvir o teste: a interpretação padrão
    soava "forçada" na entonação.
- **Decisão — multi-chave do Gemini (até 5, em cadeia):** motivado pelo HTTP 429 visto
  acima. `GeminiApiKeySettings` (renomeada de `GeminiWriterSettings`,
  `RadioBulletin.kt`) guarda até `MAX_KEYS = 5` chaves indexadas, com migração automática
  da chave única salva antes dessa mudança (slot 0, `init` da classe). Tanto
  `RemoteGeminiRadioScriptWriter.generateWithRetry` (escrita) quanto
  `GeminiFlashTtsEngine.generateLineWithRetry` (voz) testam as chaves configuradas EM
  ORDEM, 1 tentativa por chave — a rotação de chave é o retry agora, a MESMA chave não é
  tentada 2x seguidas (um 429 de cota não se resolve batendo de novo na mesma chave). Só
  desiste de vez (cai pro redator/motor local) depois de esgotar todas as chaves
  configuradas. Campo de chave saiu do formulário inline dentro da tela de Boletins e
  virou página própria ("Chaves do Gemini", `GeminiApiKeysSettingsPanel` em
  `LocalTuneApp.kt`) — pedido explícito do usuário, um slot por chave com salvar/remover
  independentes, nunca mostra a chave de volta depois de salva.
- **Decisão — remoção do OpenRouter:** pedido explícito do usuário ("pode tirar a do
  openrouter, nem vamos usar") — `OpenRouterWriterSettings`/
  `RemoteOpenRouterRadioScriptWriter` (ADR-021, 05/09/2026) removidos inteiros de
  `RadioBulletin.kt`, junto da UI/callbacks correspondentes. O cenário original que
  motivou o OpenRouter (Gemini sobrecarregado, 503 em sequência) agora é coberto pela
  rotação de chaves acima, sem precisar de um segundo provedor.
- **Decisão — buffer de boletins não desperdiça mais trabalho pronto:** bug real achado
  testando ao vivo: sair/trocar de rádio antes do 1º boletim tocar parecia "devolver" o
  núcleo pro buffer genérico (`clearBulletinBuffer`, ADR-020), mas a ordem das operações
  estava errada — apagava `item.file` **antes** de devolver o núcleo, deixando o núcleo
  devolvido com um caminho pra um arquivo que a própria função acabara de apagar. O
  roteiro "sobrevivia" (dava a impressão de estar funcionando) mas o áudio sintetizado —
  a parte cara de verdade — sempre sumia, forçando resíntese do zero na próxima vez que
  esse núcleo fosse usado. Corrigido: nada é apagado, todo boletim pronto (com ou sem
  núcleo de origem) volta pro buffer genérico intacto. Dois bugs secundários corrigidos
  junto: (1) `refillBulletinBuffer()` só promovia 1 núcleo pronto de volta pro buffer da
  rádio por chamada (throttle de 06/09/2026 que fazia sentido quando devolver ainda
  desperdiçava — deixou de fazer sentido depois do fix acima) — agora esvazia o estoque
  pronto de uma vez, só o caminho "gerar do zero" (caro, sem núcleo disponível) continua
  limitado a 1 por chamada; (2) o loop que devolve vários núcleos em lote usava
  `coreBuffer.addFirst` varrendo do mais antigo pro mais novo, o que invertia a ordem
  relativa entre eles — corrigido varrendo `.asReversed()`, garantindo que o buffer
  sempre toca do boletim mais antigo pro mais recente. `saveCoreBufferManifest()`
  também passou a persistir o buffer da rádio ativa (Nível 2), não só o genérico (Nível
  1) — antes, matar o processo (reinstalar o app, Android liberando memória) perdia
  qualquer boletim já promovido pro buffer da rádio, mesmo com o núcleo genérico
  sobrevivendo normalmente.
- **Decisão — sincronizar biblioteca pega álbum novo sem reiniciar o aparelho:**
  `MusicLibraryRepository.loadSongs()` só consultava o índice que o MediaStore já tinha
  — copiar arquivo por fora do app (USB, gerenciador de arquivos) nunca disparava o
  scanner sozinho no Android 11+. Confirmado testando via `adb` antes de mexer no código:
  `scan_volume` (mesmo método que o boot usa, alcançado passando a string crua pro
  `ContentResolver.call()` público — o campo `MediaStore.SCAN_VOLUME_CALL` é `@hide`) só
  insere uma linha "esqueleto" por arquivo novo, com `DURATION`/`IS_MUSIC` nulos
  indefinidamente — não extrai metadado sozinho. `MediaScannerConnection.scanFile()` por
  caminho é quem faz a extração de verdade. `loadSongs()` agora dispara `scan_volume`
  primeiro (garante que todo arquivo novo vira linha, mesmo um nunca visto), depois
  `scanFile()` em cada linha ainda pendente antes de consultar a biblioteca de verdade.
- **Também nessa sessão (menores):** alça "NEWS" da Home ganhou texto vertical legível
  (o `rotate()` sozinho cortava a palavra porque o layout media o texto antes de
  rotacionar) e desceu pra perto da navbar inferior; bolinhas do card de boletins
  passaram a usar verde (pronto) / vermelho piscando (escrevendo agora) em vez da mesma
  cor pras duas; aba de Categorias parou de esconder gênero com poucas faixas
  (`dynamicGenreRadios` tinha um mínimo de 8 músicas por categoria, pensado pra "rádio"
  de verdade, que vazava pra Categorias sem necessidade — `allGenreRadios` agora chama
  com `minSize = 1`); `LocalAlbum.key` parou de incluir o resumo de artistas agregados
  (instável — mudava sozinho em álbuns tipo "WhatsApp Audio" toda vez que chegava
  mensagem de um remetente novo, fazendo favoritar/ocultar "esquecer" silenciosamente).

## ADR-023 — Letras das músicas (tela do player, offline-first, fora dos arquivos de áudio)

- **Contexto:** o usuário quer ver a letra da música tocando, arrastando pro lado na
  área da capa (capa ⇄ letra), com destaque da linha atual quando a letra tiver
  marcação de tempo.
- **Decisão:**
  1. **UI:** `HorizontalPager` de 2 páginas dentro do `FullPlayer` (`ui/LocalTuneApp.kt`),
     travado na MESMA caixa `fillMaxWidth().aspectRatio(1f)` que a capa ocupava — página
     0 = capa (chamada idêntica de `ArtworkBox`), página 1 = `LyricsPage`, com bolinhas
     (`PagerDots`) abaixo. Um menu de 3 pontos no topo é o atalho ("Colar/Editar letra",
     "Buscar letra online", "Remover letra"). **Só no modo não-rádio** — rádio mantém
     `ArtworkBox` puro, sem pager nem bolinhas.
  2. **Fontes, em prioridade:** letra colada/editada pelo usuário → tag embutida no
     arquivo (`FieldKey.LYRICS` via jaudiotagger, só LEITURA, cópia temp no `cacheDir`
     igual `writeTagsToAudioFile`) → busca online no **LRCLIB** (`lrclib.net`, público,
     sem chave de API, `HttpURLConnection` + `org.json`, mesmo padrão de
     `NewsBulletinRepository`) — a rede só entra **sob demanda** (botão), nunca sozinha
     (ADR-002/004: offline-first).
  3. **Armazenamento:** `filesDir/lyrics/<songId>.lrc` (texto verbatim, LRC ou puro) +
     `filesDir/lyrics/index.json` (`{ source, synced, updatedAt, sig, provider }`) —
     mesmo padrão de `filesDir/artist_photos`. Keyed por `songId` (MediaStore `_ID`,
     como favoritos/histórico). Letra achada na tag é cacheada no `.lrc` pra abrir
     rápido depois e entrar no backup.
  4. **A letra NUNCA é gravada de volta nos arquivos de áudio do usuário** (ADR-008,
     decisão explícita do usuário) — mora só no app + backup.
  5. **Backup:** `BackupRepository` copia a pasta `lyrics/` em base64 igual
     `artist_photos`; `BACKUP_SCHEMA_VERSION` 2 → 3 (chave aditiva, sem `.clear()`).
  6. **Parser LRC** (`data/LrcParser.kt`): função pura, sem imports Android, com teste
     unitário (`app/src/test/.../LrcParserTest.kt`, `junit:junit:4.13.2` — primeiro
     teste automatizado do projeto, alvo do P2 de `docs/TODO.md`). Destaque sincronizado
     usa `player.positionMs`, que já pulsa pelo loop de polling do `LocalTuneViewModel`
     — sem timer novo.
- **Motivo:** feature pedida; encaixa nos padrões existentes sem dependência nova
  (`HorizontalPager` já vem na BOM 2024.06.00; `jaudiotagger` já é dep) e sem tocar a
  máquina de estados do boletim, o loop de polling ou `updatePlayerState`.
- **Não mudar sem:** o carregamento da letra é disparado da UI via
  `LaunchedEffect(player.songId)`, **nunca** do `init{}` nem de `updatePlayerState`
  (ADR-020: ler/escrever Compose `State` fora da Main na construção do VM derruba o app).
  Se um dia a letra for gravável na tag, é ação manual explícita com confirmação
  (ADR-008), reusando o fluxo de `MediaStore.createWriteRequest` que o tag writer já tem.
- **Atualização (15/09/2026, busca automática ao arrastar + 2º provedor):** o botão
  "Buscar online" saiu da UI. Arrastar até a página da letra já é o pedido implícito de
  vê-la, então um `LaunchedEffect(pagerState.currentPage, ...)` em `FullPlayer` dispara
  `onFetchLyrics()` sozinho assim que a página 1 abre com `lyrics.lyrics.isEmpty` (e
  `lyrics.message == null`, pra não repetir a tentativa depois de um miss já registrado
  na mesma música). O botão "Colar letra" só aparece se a busca falhar. O menu de 3
  pontos mantém "Buscar letra online" como re-tentativa manual (ex.: depois de editar a
  letra ou trocar de faixa sem sair da página). `LyricsRepository.fetchOnline` agora
  tenta dois provedores em cascata: **LRCLIB** primeiro (como antes, pode vir
  sincronizada) e, se não achar, **lyrics.ovh** (`api.lyrics.ovh`, público, sem chave,
  mesmo padrão `HttpURLConnection`/`org.json` — mas só devolve texto puro, nunca LRC
  sincronizado). Novo `LyricsSource.LYRICS_OVH` no enum (`data/Lyrics.kt`) pra rotular a
  fonte na UI, igual `LRCLIB`.
- **Não mudar sem (atualização):** a rede ainda só entra quando a UI pede (agora via
  gesto, não mais só por botão) — o auto-fetch continua condicionado a
  `lyrics.lyrics.isEmpty`, nunca sobrescreve letra já carregada (tag/manual/cache) só por
  reabrir a página. `lyrics.message` funciona como trava de "já tentei e falhou nesta
  música" — se o auto-retry for reativado no futuro, tem que continuar respeitando essa
  trava ou vira busca em loop a cada troca de página.

## ADR-024 — Buffer de boletins unificado (fim dos "2 níveis")

- **Contexto:** usuário reportou ao vivo, na mesma sessão, dois sintomas do buffer de
  boletins: (1) saiu de uma rádio e entrou em outra, o contador caiu de ~20 prontos pra
  6 e o app "começou a gerar mais" — parecia perda de trabalho; (2) minutos depois, o
  contador mostrou **"19/10 prontos"** — mais boletins prontos que o próprio alvo
  declarado na tela, com a cota gratuita do Gemini TTS (429, `generativelanguage.
  googleapis.com/generate_content_free_tier_requests`, limite 10/chave) se esgotando
  bem mais rápido do que deveria.
- **Causa raiz (achada lendo o código, confirmada em log real no aparelho):** o buffer
  era **dois buffers separados** desde ADR-019/022 — `coreBuffer` ("Nível 1", agnóstico
  de rádio, roda desde a abertura do app) e `bulletinBuffer` ("Nível 2", específico da
  rádio ativa, promovido a partir do Nível 1 e limpo a cada `startRadioNewsMode`). Os
  dois guardavam literalmente o mesmo tipo de item (roteiro + áudio 100% prontos pra
  tocar — o boletim é radio-agnóstico desde 09/09/2026, nunca cita música/rádio), mas
  precisavam ficar sincronizados via promoção/devolução a cada troca de rádio. Essa
  duplicação causava os dois sintomas:
  1. **Perda aparente:** o contador da tela é só o Nível 2, que sempre reinicia do zero
     ao entrar numa rádio (mesmo devolvendo os itens pro Nível 1 intacto por baixo) — a
     UI "piscava" pra um número menor mesmo sem nada ter sido descartado de verdade.
     Pior ainda: havia um caminho real de perda — se a troca de rádio acontecesse
     **enquanto** um item estava sendo gerado (não ainda no buffer), o código antigo
     apagava o áudio recém-sintetizado (`file?.delete()`) em vez de devolvê-lo, porque
     na hora em que a geração terminava a rádio ativa já não batia mais com a que
     disparou o pedido.
  2. **Estoque duplicado ("19/10"):** `prewarmCoreBuffer()` (Nível 1) só conferia o
     próprio tamanho contra `BULLETIN_BUFFER_TARGET`, sem saber quanto o Nível 2 já
     tinha — cada promoção do Nível 1 pro Nível 2 abria uma vaga que o Nível 1 reenchia
     do zero, mesmo com o Nível 2 já cheio. Os dois níveis enchiam até o alvo **cada um
     por conta própria**, dando um estoque real de até 20 em vez de 10, gastando cota do
     Gemini em dobro do necessário.
- **Decisão — buffer único:** eliminados `coreBuffer`, `PreparedNewsCore`, `corePrepJob`,
  `prewarmCoreBuffer()`, `clearCoreBuffer()`, `syncCoreBufferStatus()`. Só resta
  `bulletinBuffer: ArrayDeque<PreparedBulletin>` (roteiro + áudio já prontos,
  `scriptFromGemini`/`voiceFromGemini` pra bolinha verde/azul), preenchido por um único
  `refillBulletinBuffer()` que:
  1. Roda **desde a abertura do app**, independente de rádio ativa ou não (era só o
     Nível 1 que fazia isso antes).
  2. **Nunca é tocado por troca/saída de rádio** — `startRadioNewsMode()`/
     `stopRadioNewsMode()` pararam de chamar `clearBulletinBuffer()`. O buffer é 100%
     independente de qual rádio está ativa; entrar/sair/trocar de rádio não cancela o
     job em andamento nem descarta nada já pronto.
  3. Quando o job termina de gerar um item e descobre que a rádio mudou nesse meio
     tempo, o item entra no buffer normalmente (antes: comparava com a rádio que disparou
     o pedido e apagava se não batesse) — o boletim nunca teve nada de específico da
     rádio pra começo de conversa.
  4. `clearBulletinBuffer()` continua existindo, mas só apaga de verdade — usada
     exclusivamente pelo botão manual "Resetar" (`resetBulletinBuffer()`). Trocar de
     rádio não é mais motivo pra apagar nada.
  - **Cap único:** `refillBulletinBuffer()` só produz enquanto `bulletinBuffer.size <
    BULLETIN_BUFFER_TARGET` — 10 é o estoque real agora, não 10 por nível.
  - **Persistência:** `saveCoreBufferManifest()`/`loadCoreBufferManifest()` (nomes
    mantidos de propósito — só a pasta/chave JSON em disco, `coreBufferDir`/
    `"coreFile"`, pra não perder o manifest já salvo por versões antigas) agora
    serializam o buffer único direto, sem união de dois formatos.
  - **Achado ao vivo migrando:** a sintetização dentro do `refillBulletinBuffer()`
    unificado estava chamando o motor **efêmero** (`synthesizeLocalVoiceSafely`,
    grava em `cacheDir`) em vez do **persistente** (`synthesizeCoreVoiceSafely`, grava
    em `filesDir`) que o Nível 1 sempre usou — como agora TODO item do buffer precisa
    sobreviver a reinício de processo (mesmo manifest/pasta), trocado pro persistente;
    senão o áudio sumiria do disco no primeiro kill do processo mesmo aparecendo pronto
    na tela.
- **Verificado no aparelho:** manifesto salvo pela versão antiga (com o estoque
  duplicado, 19 itens) restaurou os 19 íntegros após o update — nenhum boletim já pago
  (LLM + voz) foi descartado na migração; o buffer simplesmente parou de crescer acima
  de 10 e vai drenar sozinho tocando normalmente até estabilizar.
- **Motivo:** pedido explícito do usuário depois de entender a causa raiz - "não tem
  motivo pra esses 2 níveis existirem, já que decidimos que o boletim é montado 100% no
  buffer antes de tocar". Simplificação, não feature nova: menos estado pra sincronizar
  é menos classe de bug pra essa mesma dupla (Nível 1 x Nível 2) continuar gerando.
- **Não mudar sem:** se algum dia o boletim deixar de ser 100% radio-agnóstico (citar
  música/rádio de novo na fala), esse "buffer único, independente de rádio" deixa de
  fazer sentido e a separação por rádio ativa (o antigo Nível 2) precisaria voltar.

## ADR-025 — Boletim verde não cai para TTS Android

- **Contexto:** em 11/09/2026, o usuário validou que os 10 boletins do buffer tocavam
  corretamente pelo botão "Reproduzir boletim pronto", com voz Gemini sintetizada e
  bolinhas verdes na UI. Mesmo assim, na reprodução real da rádio, ao chegar o intervalo
  automático, o app falava o mesmo texto pelo TTS Android. Isso desperdiçava exatamente
  o trabalho caro do Gemini: roteiro bom + WAV já sintetizado.
- **Causa raiz (confirmada em logcat no aparelho):** `speakNextNewsBreak()` removia o
  item de `bulletinBuffer` e chamava `saveCoreBufferManifest()` antes de o `MediaPlayer`
  abrir o arquivo. A limpeza de órfãos dentro de `saveCoreBufferManifest()` monta a lista
  de arquivos referenciados a partir dos itens que ainda estão no buffer. Como o boletim
  escolhido já tinha saído da fila, seu `.wav` deixava de estar referenciado por alguns
  instantes. Se esse arquivo já tivesse passado da margem `ORPHAN_CLEANUP_GRACE_MS`, o
  sweep apagava justamente o áudio selecionado para tocar. O teste manual funcionava
  porque ele só espiava o item do buffer; o bug aparecia na janela estreita do consumo
  automático.
- **Decisão:** o item escolhido para tocar agora fica protegido por
  `protectedBulletinPlaybackFileName` desde antes do manifest ser salvo até o fim da
  reprodução. `saveCoreBufferManifest()` considera esse nome como referência válida além
  dos itens ainda presentes em `bulletinBuffer`, impedindo que o sweep de órfãos apague o
  WAV em trânsito.
- **Também corrigido no mesmo fluxo:**
  1. `dequeueBufferedBulletinForPlayback()` procura o primeiro item com WAV tocável e
     pula entradas sem áudio/quebradas na frente da fila.
  2. Com a voz dos boletins ligada, falta de WAV tocável cancela a entrada em vez de cair
     para TTS Android. Boletim preparado com voz não pode degradar silenciosamente para a
     voz do sistema.
  3. `playAnnouncementFile()` só apaga o WAV em conclusão bem-sucedida. Em erro do
     `MediaPlayer`, chama `restoreBufferedBulletinToFront()` para devolver o boletim à
     fila se o arquivo ainda existir e for válido.
  4. O botão "Reproduzir boletim pronto", quando chamado no índice 0, pula um item sem
     áudio na frente e toca o primeiro WAV válido disponível, mantendo o teste coerente
     com o consumo real.
- **Verificado no aparelho:** build debug instalada e rádio monitorada via `adb logcat`.
  Na virada da terceira música, o app registrou:
  `boletim: consumindo do buffer (audio=true, arquivo=radio_core_gemini_1789113322478.wav, bytes=1861050, ...)`,
  depois `boletim: tocando audio do buffer arquivo=... bytes=1861050` e, no fim,
  `announcement file completed bytes=1861050`. Não apareceu chamada de
  `boletim: falando via TTS Android` nesse intervalo.
- **Não mudar sem:** preservar a garantia de que bolinha verde significa "áudio Gemini
  pronto e será usado". Qualquer fallback para TTS Android nesse caminho precisa ser
  uma decisão explícita de produto, não um efeito colateral de limpeza, erro silencioso
  ou arquivo transitório fora do manifest.

## ADR-026 — Cast pra TV (Google Cast + DLNA/UPnP), um único botão

- **Contexto:** pedido do usuário (12/09/2026) de um botão de "transmitir pra TV" igual
  ao que ele viu em outro app de música. Investigação mostrou que a TV dele (LG webOS)
  **não fala o protocolo Google Cast** — outro app instalado (Symfonik) só conseguiu
  conectar nela via **UPnP/DLNA**, um protocolo bem mais antigo e completamente
  diferente (SSDP multicast pra descoberta, SOAP/AVTransport pra controle). Decisão do
  usuário: implementar os dois, sob um único ícone (não dois botões separados).
- **Arquitetura escolhida — espelhar, não reescrever a fila:** `controller` (ExoPlayer/
  MediaController local) continua tocando NORMALMENTE (só com `volume = 0f`) enquanto
  uma sessão remota está conectada — ele nunca pausa e continua sendo a única fonte de
  verdade de fila/posição/próxima-anterior/rádio/boletim, exatamente como sem Cast. Um
  `RemotePlaybackBridge` (interface comum a `CastPlaybackBridge` e `DlnaPlaybackBridge`)
  só ESPELHA: a cada `onEvents` do player local, `mirrorToRemoteIfNeeded()` (
  `LocalTuneViewModel.kt`) detecta troca de faixa ou play/pause e repassa pro destino
  remoto. Motivo: a lógica de fila/rádio/boletim já é complexa e tem um bug histórico
  não resolvido (ver ADR-009, "player para sozinho depois de algumas músicas") — reescrever
  esse caminho pra depender de Cast/DLNA arriscava reintroduzir ou mascarar esse bug.
  Essa escolha também descartou usar `androidx.media3:media3-cast` (`CastPlayer`), que
  modela Cast como um `Player`/timeline único e não encaixa no modelo de 2 canais
  (música + boletim) já existente.
- **Servidor HTTP local (`cast/CastMediaHttpServer.kt`, NanoHTTPD):** tanto o receptor
  Cast quanto qualquer renderizador DLNA só sabem tocar mídia de uma URL HTTP — nunca um
  `content://` do próprio celular. O servidor serve os arquivos de música (`content://`
  do MediaStore) com suporte a `Range`, numa porta efêmera, com um token aleatório por
  sessão na própria URL (ofuscação por sessão, não autenticação de verdade). Roda no
  processo do app (mesmo processo do `MusicPlaybackService`, não precisou de IPC).
- **Três bugs de rede encontrados só testando no aparelho de verdade** (nenhum aparecia
  em teoria/compilação — todos silenciosos, sem exceção nenhuma até serem investigados):
  1. **Tema não-AppCompat quebra o `MediaRouteButton`:** `MediaRouterThemeHelper` calcula
     contraste usando o atributo `colorPrimary` do **AppCompat** (não `android:colorPrimary`)
     — como `AppTheme` (`res/values/styles.xml`) herdava de `android:style/Theme.Material.
     NoActionBar` (tema puro, sem AppCompat), esse atributo nunca existia e resolvia pra
     `0` (transparente), estourando `IllegalArgumentException` já na construção do botão.
     Depois disso, o próprio diálogo de seleção de dispositivo (`MediaRouteChooserDialog`)
     é um `AppCompatDialog` e falha do mesmo jeito ("You need to use a Theme.AppCompat
     theme") se o tema do app não descender de `Theme.AppCompat`. Resolvido trocando o
     `parent` de `AppTheme` pra `Theme.AppCompat.NoActionBar` (zero mudança visual — a UI é
     toda Compose, com seu próprio `MaterialTheme`) e adicionando `colorPrimary`/
     `android:colorBackground` explícitos. `MainActivity` também precisou virar
     `FragmentActivity` (em vez de `ComponentActivity` puro) porque esses diálogos exigem
     suporte a `Fragment`.
  2. **Wi-Fi + dados móveis ativos ao mesmo tempo:** tanto a URL do servidor HTTP local
     quanto a descoberta SSDP (`SsdpDiscovery.kt`) precisam sair especificamente pela
     interface Wi-Fi, nunca pela rede de dados móveis — mas o Android não roteia
     automaticamente por Wi-Fi só porque ela está conectada, se dados móveis também
     estiverem ativos. Sem vincular explicitamente o socket à rede Wi-Fi
     (`Network.bindSocket`, ver `LocalWifiAddress.findWifiNetwork`/
     `findWifiNetworkInterface`), a descoberta simplesmente não achava nada, **sem
     nenhum erro** — parecia funcionar (nenhuma exceção) mas nunca recebia resposta.
  3. **Cleartext HTTP bloqueado por padrão:** UPnP (descrição do dispositivo e comandos
     SOAP/AVTransport) é inteiramente HTTP puro, nunca HTTPS. A partir do Android 9,
     `usesCleartextTraffic` é `false` por padrão e qualquer chamada assim falha com
     `CLEARTEXT communication not permitted`. Resolvido com
     `android:usesCleartextTraffic="true"` no `<application>` (não afeta o resto do app,
     que já falava HTTPS com APIs externas). Isso NÃO afeta o servidor HTTP local em si
     (ele é o lado que RECEBE conexões — a restrição é só sobre chamadas HTTP feitas
     pelo próprio app).
- **Descoberta SSDP (`dlna/SsdpDiscovery.kt`):** `MulticastSocket` vinculado à rede
  Wi-Fi e à interface de rede Wi-Fi, mas com porta local temporária, não a porta 1900.
  A porta 1900 é a porta multicast de anúncio SSDP; controle SSDP normalmente envia
  `M-SEARCH` de uma porta efêmera e recebe respostas unicast nessa mesma porta. Em
  12/09/2026, teste direto na LAN achou a TV LG webOS (`192.168.0.42`,
  `[LG] webOS TV UP7550PSF`) quando a busca saiu por uma porta temporária, inclusive
  com `ST: urn:schemas-upnp-org:service:AVTransport:1`; a versão anterior do app usava
  `MulticastSocket(1900)` e a TV específica não aparecia. A busca envia alvos
  específicos (`AVTransport`/`MediaRenderer`) primeiro, depois `upnp:rootdevice` e
  `ssdp:all` como fallback, sempre filtrando por AVTransport depois, lendo a descrição
  XML de cada dispositivo. M-SEARCH é reenviado a cada ~1.2s durante uma janela de 6s
  (UDP não garante entrega). A busca do XML de descrição de cada dispositivo
  (`fetchDeviceDescription`, com `HttpURLConnection` + timeout de 2s explícito)
  acontece **depois** do loop de recebimento, nunca durante — uma primeira versão sem
  esse timeout travava o recebimento de pacotes seguintes por muito mais que o tempo
  pretendido quando um dispositivo respondia com uma `LOCATION` lenta/inalcançável
  (aconteceu com o roteador da casa, que respondeu com um endereço IPv6 que o celular
  não roteava de verdade).
- **UI unificada (`HeaderCastButton`/`RemoteDeviceSheet` em `LocalTuneApp.kt`):** um único
  ícone (pedido explícito do usuário — "um só ícone, as duas funções lá dentro") abre
  uma bottom sheet que lista rotas Cast (via `androidx.mediarouter.media.MediaRouter`
  direto, não o `MediaRouteButton` padrão — que só mostra Cast, não dá pra combinar com
  DLNA) e dispositivos DLNA (SSDP) juntos. `MediaRouter.selectRoute()` numa rota Cast
  dispara a sessão normalmente, capturada pelo `SessionManagerListener` já existente.
- **Diagnóstico da LG webOS:** a TV foi localizada na LAN via SSDP fora do app em
  12/09/2026: `LOCATION: http://192.168.0.42:1149/`, `SERVER: Linux/i686 UPnP/1,0
  DLNADOC/1.50 LGE WebOS TV/Version 0.9`, com `MediaRenderer`, `AVTransport` e
  `RenderingControl`. O XML de descrição expôs o `controlURL`
  `/AVTransport/a9628769-d594-a83d-011a-4c761f3d1d1b/control.xml`, que encaixa no
  parser atual. Se voltar a falhar no aparelho, o próximo diagnóstico deve comparar
  no `adb logcat` se as respostas de `192.168.0.42` chegam ao app; não partir da
  premissa de que a TV não responde SSDP.
- **Reprodução na LG webOS:** no mesmo teste real, a TV recusou a primeira tentativa
  de `SetAVTransportURI` com `701 Transition not available` quando o app tentava
  carregar a faixa sem parar o renderizador antes. A sequência que funcionou foi:
  `Stop`, `SetAVTransportURI`, depois `Play`; `Seek` antes do `Play` falhou com
  `711 Illegal Seek Target`, então DLNA começa a faixa do início por enquanto. A TV
  também espera conseguir buscar capa via HTTP, não `content://`; por isso o servidor
  local agora registra e serve album art em `/media/{sessao}/artwork/{token}`, e o
  DIDL-Lite inclui `upnp:album` e `upnp:albumArtURI`. Validação ao vivo: a LG fez
  `HEAD` no MP3, `GET` no JPEG da capa, `GET` no MP3, e respondeu `Play OK: HTTP 200`.
- **Não mudar sem:** manter o `controller` local como única fonte de verdade (nunca
  fazer `CastPlaybackBridge`/`DlnaPlaybackBridge` decidir fila/próxima-faixa/rádio por
  conta própria) — é essa escolha que mantém o Cast/DLNA sem risco pro bug do ADR-009.
  Se essa TV LG específica (ou outra) precisar ser diagnosticada de verdade, comece por
  uma captura de pacotes SSDP na mesma rede em vez de tentar mais variações de código às
  cegas.

## ADR-027 — Vinhetas de despedida, volume da passagem 3, e timer "você ainda está aí?"

- **Contexto:** três pedidos do usuário no mesmo dia (15/09/2026): (1) duas vinhetas
  novas gravadas (`tchauzinho`/`até mais`) para tocar ao sair de uma rádio, simétrico à
  intro que já existia (ver ADR-011); (2) `passagem_3.mp3` tocando visivelmente mais
  baixo que `passagem_1`/`passagem_2` (medido: -20.8dB de volume médio contra -13.8dB e
  -9.4dB — a fonte em si era mais baixa, não um problema no código); (3) a rádio fica
  ligada a noite toda quando o usuário dorme com ela tocando, gastando boletim/síntese
  de voz sem ninguém escutando.
- **Vinhetas de despedida:** arquivos movidos para `res/raw/vinheta_tchauzinho.mp3` e
  `res/raw/vinheta_ate_mais.mp3` (fonte: `tchauzinho_1.mp3`/`até mais edited.mp3` em
  `vinhetas/`). `stopRadio()` (botão "Sair da rádio") agora pausa a música, toca uma das
  duas por cima via `playExitVinheta()` (alterna em sequência, `EXIT_VINHETA_RESOURCES`/
  `nextExitVinhetaIndex`, mesmo padrão de `PASSAGEM_RESOURCES`) e só então
  para/esvazia a fila de verdade, no callback de conclusão. Não dispara no caminho
  "trocar para playlist normal enquanto uma rádio tocava em segundo plano"
  (`playSongs` com `keepRadioNews = false`) — só no botão explícito, pra não interromper
  uma música nova que já começou a tocar.
- **Volume da passagem 3:** re-processada com `ffmpeg` (compressor leve +
  ganho + limiter, não só um `volume=Xdb` cru — a fonte tinha muito mais dinâmica que
  as outras duas e um ganho simples ia ou continuar baixa ou cortar os picos) trazendo
  o volume médio de -20.8dB para -14.5dB, próximo de `passagem_1` (-13.8dB). Arquivo
  trocado em `res/raw/passagem_3.mp3` E em `vinhetas/passagem 3.mp3` (fonte). Nenhuma
  mudança em `PASSAGEM_VOLUME` — é o asset que estava desbalanceado, não o multiplicador
  aplicado em `playPassagem()`, que continua igual pras três.
- **Timer de inatividade:** `armSleepTimer()` inicia (e `confirmStillListening()`
  reinicia) uma contagem de `SLEEP_TIMER_IDLE_MS` (1h30) a cada entrada numa rádio
  (`startRadioNewsMode`). Ao vencer, `triggerSleepCheck()` liga `sleepCheckPending`
  (novo campo em `PlayerUiState`, mesmo padrão de `isRadioMuted`) — a UI
  (`LocalTuneApp.kt`) mostra um `AlertDialog` sem `onDismissRequest` (só sai com o botão
  "Sim", não com toque fora) perguntando "Você ainda está aí?". Sem resposta em
  `SLEEP_TIMER_RESPONSE_MS` (1min), chama `stopRadio()` sozinho (mesma vinheta de
  despedida acima). `cancelSleepTimer()` roda dentro de `stopRadioNewsMode()` — cobre
  tanto a saída manual quanto a troca para playlist normal, sem duplicar a chamada nos
  dois lugares que já chamavam `stopRadioNewsMode()`.
- **Não mudar sem:** se o usuário mandar vinhetas de despedida novas, atualizar
  `EXIT_VINHETA_RESOURCES` E `vinhetas/README.md` juntos (mesma regra do ADR-011). Se a
  passagem 3 for regravada, remedir o volume antes de decidir se ainda precisa do
  mesmo processamento (`ffmpeg -af volumedetect`) — o processamento atual foi calibrado
  pro áudio específico enviado nesse dia, não é um valor universal.

## ADR-028 — Heurística de afinidade da rádio (aprender com o uso, sem ML)

- **Contexto:** usuário perguntou se dava pra fazer o player "aprender" com o uso de
  forma realmente inteligente. Decisão conjunta: um modelo de ML de verdade não
  compensa pra um único usuário (sem volume de dados pra treinar algo que não seja
  overfit no histórico recente) — foi escolhida uma heurística simples por cima do
  algoritmo de diversidade que já existe (`buildRadioQueue`, ver comentário logo acima
  dele), não uma reescrita.
- **Sinais usados (só dois, de propósito — poucos e explicáveis):** (1) favoritar uma
  faixa (`favoritePrefs`/`KEY_FAVORITE_SONGS`, sinal explícito que já existia); (2) a
  faixa **terminar de tocar sozinha** numa sessão de rádio (`recordRadioPlayThrough()`
  em `MusicLibraryRepository.kt`, chamado por
  `LocalTuneViewModel.playerListener.onMediaItemTransition` quando `reason ==
  MEDIA_ITEM_TRANSITION_REASON_AUTO` e a rádio está ativa — pular manualmente ou
  deslikar não soma). Sem sinal negativo implícito novo: deslike (ADR existente) já
  cobre "nunca mais tocar essa faixa"; não tentamos inferir "não gostou" de skip, porque
  skip fica desativado durante a rádio (`skipNext`/`skipPrevious` só funcionam fora do
  modo rádio).
- **Como o viés entra na fila:** `RadioCandidate` ganhou um campo `affinityBias` (negativo
  = mais provável de ser escolhido), calculado uma vez por `buildRadioQueue()`
  (`radioAffinityBias()`: plays cumulativos, com teto de 10, `AFFINITY_PLAY_WEIGHT` cada
  + `AFFINITY_FAVORITE_BONUS` se favoritada) e somado dentro das fórmulas de pontuação
  que já existiam (`radioArtistScore`/`pickSongForArtist`). Faixa sem histórico fica em
  viés 0 (nem penalizada nem favorecida) — senão música nova adicionada nunca teria
  chance de aparecer. As constantes foram calibradas pra ficar BEM abaixo das
  penalidades de anti-repetição (200_000/50_000/12_000...) — o viés só desempata entre
  candidatos que a diversidade por artista/álbum/gênero já deixaria passar, nunca força
  repetir artista/álbum recente só porque a faixa é favorita.
- **Onde ainda não se aplica:** só no fluxo genérico (`buildRadioQueue`, usado por rádios
  de gênero, "Rádio recente" e rádios personalizadas de categoria) — `shuffledRadioSession`
  (álbum various-artists) e `albumDiverseRadioSession` (rádio de artista único) ainda não
  usam o viés. Extensão natural se o usuário pedir depois.
- **Por que não vira lista de "top favoritas" repetitiva:** o viés é só um desempate
  pequeno, não uma reordenação por rank puro — a escolha ainda sai de um pool aleatório
  (`randomFromTop`, `RADIO_ARTIST_CHOICE_POOL`/`RADIO_SONG_CHOICE_POOL`) e o teto de 10
  plays evita crescimento sem limite (uma faixa tocada 200 vezes não vale mais que uma
  tocada 10 vezes).
- **Não mudar sem:** se adicionar um sinal negativo implícito de verdade (ex.: detectar
  skip manual fora da rádio, ou pausar no meio), ele deve reduzir o viés (não excluir a
  faixa da sessão) — exclusão total é papel do deslike explícito, que já existe e não
  deve ser duplicado por heurística implícita.
- **Backup:** a nova `SharedPreferences` (`radio_affinity`) ficou de fora do
  `BACKED_UP_PREFS_NAMES` (`BackupRepository.kt`) na primeira versão desse ADR - usuário
  perguntou e corrigido em 15/09/2026, adicionada à lista junto de `favorites`/
  `playback_history` (mesma categoria: acumulado de uso real do usuário, não cache
  técnico). Se aparecer outra `SharedPreferences` nova de dado do usuário no futuro,
  checar essa lista também - não é automático.

## ADR-029 — Rádio padrão "Surprise Me"

- **Contexto:** com a heurística de afinidade do ADR-028 em produção, usuário pediu uma
  rádio padrão específica ("me surpreenda") que escolhe música pra ele baseada no gosto
  dele, em vez de ficar implícita dentro das rádios de gênero.
- **Decisão:** `radiosFrom()` (`MusicLibraryRepository.kt`) ganhou uma
  `LocalRadio` fixa (`SURPRISE_RADIO_NAME = "Surprise Me"`) com `songs = ` a biblioteca
  **inteira** (não filtrada por gênero/década como as outras) — o pool grande é
  proposital, é o que faz o viés de afinidade ter algo de fato pra escolher entre. Ela
  passa pelo MESMO `buildRadioQueue()`/diversidade por artista de sempre, não é um motor
  novo; a única diferença real é `radioSessionFrom()` detectar o nome (via
  `normalizeLookupKey`) e passar `affinityWeight = SURPRISE_AFFINITY_WEIGHT` (4.0) em vez
  de 1.0 nas outras rádios — o mesmo `radioAffinityBias()` do ADR-028, só escalado. No
  teto de plays + favorito, o viés escalado chega perto/passa da penalidade de "álbum
  recente" (12_000), o suficiente pra render notavelmente mais "pra você" que uma rádio
  comum — mas ainda bem abaixo de "repetir o artista tocado por último" (200_000), que
  continua intocável.
- **Capa do card:** em vez de `previewCovers(songs, seed)` aleatório como as outras
  rádios padrão, ordena por `radioAffinityBias()` e usa as 60 faixas de maior afinidade
  como pool de capas — reforça visualmente "baseado no seu gosto" mesmo antes de entrar.
  Sem histórico ainda (conta zerada, nada favoritado), cai de volta pro comportamento de
  sempre (ordem estável, sem viés real).
- **Por que não é um motor separado:** cogitado e descartado — reescrever a lógica de
  fila especificamente pra essa rádio arriscava duplicar toda a diversidade por
  artista/álbum/gênero que `buildRadioQueue()` já resolve bem (ver ADR-009 sobre o custo
  de tocar nesse caminho). Parametrizar o peso existente foi a mudança mínima que ainda
  entrega a diferença pedida.
- **Não mudar sem:** se o peso 4.0 parecer fraco ou forte demais na prática, ajustar
  `SURPRISE_AFFINITY_WEIGHT` sozinho é seguro — ele só multiplica `affinityBias`, não
  muda a fórmula em si nem afeta as outras rádios (que continuam em 1.0, hardcoded no
  `else` de `radioSessionFrom`).

## ADR-030 — Compartilhar álbum como .zip (preserva a pasta no destinatário)

- **Contexto:** `shareSongs()` (`LocalTuneApp.kt`) já existia e manda as faixas de um
  álbum via `ACTION_SEND_MULTIPLE` (várias faixas soltas, tocáveis na hora por quem
  recebe) — escolha deliberada anterior, documentada no comentário da própria função,
  pra evitar o custo de gerar/copiar um zip e depender do destinatário saber
  descompactar. Usuário apontou o problema real disso: o WhatsApp (e apps parecidos) do
  destinatário não recria pasta de álbum nenhuma, só solta os arquivos recebidos juntos
  na pasta de mídia dele, misturados com tudo mais — quem recebe não fica com "o álbum"
  organizado.
- **Decisão:** em vez de substituir `shareSongs()`, o botão de compartilhar em
  `AlbumDetailScreen` virou um menu (`AlbumShareMenu`, mesmo padrão de
  `LyricsOverflowMenu`) com as duas opções lado a lado — os dois trade-offs são reais e
  nenhum vence o outro em todo caso, então a escolha fica com quem está compartilhando
  na hora, não travada num dos dois.
- **Como o zip é gerado (`buildAlbumZip`):** roda em `Dispatchers.IO` (zipar arquivos
  grandes na main thread travaria a UI), escreve em `cacheDir/shared_zips/` (limpo antes
  de cada zip novo — só 1 por vez, não acumula lixo no cache), com todas as faixas
  dentro de UMA entrada de pasta nomeada com o título do álbum (`"$album/$faixa"`) — é
  isso que faz o destinatário ganhar a pasta certinha ao extrair. Nome real do arquivo
  (com extensão) vem de `MediaStore.Audio.Media.DISPLAY_NAME` via `ContentResolver`,
  não inventado a partir do título (`LocalSong` não guarda extensão).
- **FileProvider novo:** um `File` de `cacheDir` não pode ser exposto direto por
  `content://` pra outro app — precisou de `androidx.core.content.FileProvider`
  (`AndroidManifest.xml`) + `res/xml/file_paths.xml`, expondo SÓ a subpasta
  `shared_zips/`, nada mais do app. Primeiro uso de `FileProvider` no projeto.
- **Não mudar sem:** se o álbum for muito grande (muitas faixas em FLAC, por exemplo),
  `buildAlbumZip` não tem limite de tamanho nem barra de progresso — só um toast se
  falhar. Se isso incomodar na prática, adicionar feedback de progresso é a extensão
  natural, não trocar a abordagem.

## ADR-031 — Play/pause do card "Continuar ouvindo", label transiente e gesto na Biblioteca

- **Contexto:** três pedidos de UI do usuário na mesma sessão (15/09/2026):
  1. Na Home, o botão de play desenhado no centro do disco (`HomePlaybackArtwork`) não
     tinha clique próprio — o clique caía no `clickable` do card inteiro, que abre o
     player em tela cheia em vez de retomar a música pausada ali mesmo.
  2. O rótulo acima do disco ("Continuar ouvindo" / "Tocando agora") era estático;
     pedido era "Tocando agora" entrar com animação, ficar 3s e recolher/sumir, mantendo
     "Continuar ouvindo" fixo enquanto pausado.
  3. Na Biblioteca, a troca entre as 4 sessões (Artistas/Álbuns/Músicas/Categorias) só
     acontecia pelos botões do topo — pedido era arrastar pro lado também funcionar,
     leve, com animação se vier de graça.
- **Decisão:**
  1. **Play/pause do disco:** `HomePlaybackArtwork` ganhou parâmetro `onPlayPauseClick`,
     aplicado só no `Box` do ícone de play (`Modifier.clickable`) — como o `clickable`
     filho consome o toque antes do pai (comportamento padrão do Compose, mesmo
     mecanismo de um botão de excluir dentro de uma linha clicável), o resto do card
     continua abrindo o player normalmente. `ContinueListeningCard`/
     `ContinueListeningLandscapeCard` só repassam o callback. Na chamada
     (`HomeScreen`/`LocalTuneApp`), o callback é `onTogglePlayPause` (→
     `viewModel::togglePlayPause`) quando a mídia tocando é a mesma do card
     (`isCurrentMedia`), ou o mesmo `onContinue` de sempre quando é uma música parada
     ainda não carregada no player (aí "tocar" e "continuar" são a mesma ação).
  2. **Label transiente:** `PlaybackStatusTitle` (novo composable) substitui a chamada
     direta a `SectionTitle`. Um `LaunchedEffect(isPlaying)` mantém `visible = true`
     sempre que `isPlaying == false` (texto fixo) e, quando vira `true`, agenda
     `delay(3_000)` antes de `visible = false`. `AnimatedVisibility` com
     `fadeIn() + expandVertically()` / `fadeOut() + shrinkVertically()` faz a entrada e o
     recolhimento.
  3. **Gesto na Biblioteca:** a sessão ativa (`Column`/`when` fixo) virou
     `HorizontalPager(pageCount = { LibrarySection.entries.size })` dentro de
     `LibraryShell` (`@OptIn(ExperimentalFoundationApi::class)`, mesmo padrão já usado
     no pager capa⇄letra do ADR-023). Sincronia nos dois sentidos com os botões do topo:
     `LaunchedEffect(pagerState.currentPage)` atualiza `librarySection`;
     `LaunchedEffect(librarySection)` chama `animateScrollToPage` quando o valor muda por
     fora (clique no botão) e diverge da página atual do pager.
- **Motivo:** os três pedidos são polimento de UI que reaproveita padrões já existentes
  no projeto (`clickable` aninhado, `AnimatedVisibility`, `HorizontalPager` já usado no
  player) — nenhum exigiu estado novo no `ViewModel` nem mudança de arquitetura.
- **Não mudar sem:** o `onPlayPauseClick` só existe no `Box` do ícone (64.dp, centralizado)
  — se o ícone for redesenhado pra ocupar mais área do card, o `clickable` precisa
  acompanhar, senão o card inteiro volta a "roubar" o toque do botão de play. O
  auto-scroll do pager da Biblioteca compara `pagerState.currentPage != librarySection.ordinal`
  antes de chamar `animateScrollToPage` — tirar essa checagem faz o pager brigar consigo
  mesmo (`LaunchedEffect` dos dois lados reagindo em loop) toda vez que o usuário arrasta.

## ADR-032 — Build release local passa a assinar com a chave de release dedicada (supera ADR-012)

- **Contexto:** o app é enviado pra um amigo instalar (não só o próprio aparelho do
  usuário). ADR-012 fixou a build release local assinando com a chave de debug de
  propósito, pra nunca precisar desinstalar no APARELHO DE TESTE do usuário. Só que o
  CI (`.github/workflows/build-apk.yml`) assina os builds de push/tag na `master` com a
  chave de release DEDICADA (`keystore/pailer-release.jks`, via secret
  `RELEASE_KEYSTORE_BASE64`) e publica um GitHub Release automático a cada merge. Ou
  seja, `dist/PailerFM.apk` (copiado de um build local) e o APK do GitHub Release nunca
  tiveram a mesma assinatura — se o amigo instalou uma vez de um lado e depois recebe um
  APK do outro lado, o Android recusa "atualizar por cima" (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`,
  identidades de assinatura diferentes) e exige desinstalar, perdendo os dados locais dele.
  Usuário perguntou por que isso acontecia (15/09/2026) e, depois de confirmar que o
  backup (ADR relacionado: ver `BackupRepository`) já cobre favoritos/histórico/overrides/
  fotos/letras e que o prompt de restauração no primeiro uso (ver abaixo) funciona de
  ponta a ponta, pediu pra unificar as chaves em vez de manter as duas.
- **Decisão:** `app/build.gradle.kts`, `buildTypes.release.signingConfig` passa a usar
  `signingConfigs.getByName("release")` (chave dedicada, criada a partir de
  `keystore.properties` local OU das env vars de CI) sempre que uma das duas fontes
  existir — só cai pra `signingConfigs.getByName("debug")` quando NENHUMA delas está
  configurada (ex.: CI rodando um `pull_request`, ou alguém clonando o repo sem
  `keystore.properties` nem secrets). Como `keystore.properties` +
  `../keystore/pailer-release.jks` já existiam prontos e sem uso (ADR-012), a mudança é
  só essa condição — nenhum arquivo de chave novo precisou ser criado.
- **Efeito colateral aceito (avisado antes, ver ADR-012):** no primeiro `adb install`
  release depois dessa mudança, tanto o aparelho de teste do usuário quanto o do amigo
  precisam desinstalar o app antes de instalar por cima (a assinatura mudou de debug pra
  release dedicada) — perde favoritos/histórico/overrides/pacote de voz TTS locais UMA
  VEZ. Depois disso, toda build futura (local OU CI) sai com a MESMA assinatura pra
  sempre, resolvendo o problema de vez.
- **Rede de segurança pro efeito colateral:** `LocalTuneViewModel.maybeOfferFreshRestore()` +
  `BackupRepository.hasOfferedFreshRestorePrompt()`/`markFreshRestorePromptOffered()` —
  na primeira abertura com a biblioteca carregada e ZERO histórico/favoritos/perfil (sinal
  de instalação nova), a Home mostra um diálogo "Já tem um backup?" com botão "Restaurar
  backup" (abre o mesmo seletor de arquivo do fluxo manual em Configurações) antes do
  usuário nem perceber que perdeu algo. Só oferece 1 vez por instalação (nunca mais
  depois, pra não incomodar quem realmente começou do zero). Testado ao vivo 15/09/2026:
  desinstalar + reinstalar mostrou o diálogo sozinho, e restaurar de um backup real
  trouxe de volta favoritos/histórico corretamente.
- **Motivo:** distribuir pra terceiros (não só o próprio aparelho de teste) exige uma
  assinatura estável — ter DUAS chaves diferentes pro "mesmo" APK é uma armadilha
  recorrente (aconteceu de novo), e agora que o backup cobre a recuperação, o custo do
  ADR-012 (nunca desinstalar) deixou de valer mais que o problema que ele causava.
- **Não mudar sem avisar antes:** voltar a usar `signingConfigs.getByName("debug")`
  incondicionalmente désfaz a unificação e faz a mesma armadilha (dist/PailerFM.apk vs.
  GitHub Release com assinaturas diferentes) acontecer de novo na próxima vez que os dois
  caminhos forem usados pro mesmo destinatário.

## ADR-033 — Auto-atualização in-app (ATIVADA 17/09/2026 — repo virou público)

- **Contexto:** usuário pediu (15/09/2026) pra quem já tem o app instalado receber um popup
  de "nova versão disponível" e atualizar direto pelo app (baixa + abre o instalador
  sozinho), em vez de precisar mandar o APK manualmente pelo WhatsApp toda vez que uma
  build nova sai no GitHub.
- **Decisão (implementação):**
  - `app/build.gradle.kts`: `buildConfigField("String", "RELEASE_TAG", ...)` lido de
    `System.getenv("APP_RELEASE_TAG")` (ou `"local-dev"` localmente) - a tag da release do
    GitHub que gerou ESSE build especificamente.
  - `.github/workflows/build-apk.yml`: o passo "Calcula tag da build" foi movido pra ANTES
    do `assembleRelease` (antes rodava só depois, só pro nome do Release) e agora tanto
    alimenta `APP_RELEASE_TAG` do build quanto o `tag_name` do Release publicado depois -
    as duas sempre a MESMA string, senão a comparação no app nunca bateria.
  - `UpdateCheckRepository` (novo, `data/`): `fetchLatestRelease()` lê
    `GET /repos/.../releases?per_page=1` (a LISTAGEM, não `/releases/latest` - ver achado
    abaixo), `isNewerThanCurrent()` compara por partes numéricas (não string crua, pra
    "v2026.09.2-9" não perder de "v2026.09.10-1" por ordem lexicográfica), e
    `downloadApk()` baixa em streaming com progresso pra `cacheDir/app_update/`.
  - `LocalTuneViewModel`: `UpdateUiState` + `checkForUpdate()` (1x na abertura do app,
    silencioso em qualquer falha - nunca deve travar/incomodar o uso normal),
    `downloadUpdate()`, `dismissUpdatePrompt()`.
  - `LocalTuneApp.kt`: `UpdateAvailableDialog` (popup com progresso de download) +
    `installApkUpdate()` (abre `ACTION_VIEW` com o APK baixado via FileProvider assim que o
    download termina - sem passar por navegador nenhum).
  - `AndroidManifest.xml`: `REQUEST_INSTALL_PACKAGES` (senão o Android bloqueia o intent de
    instalar) + nova entrada em `res/xml/file_paths.xml` (`app_update/`) pro FileProvider já
    existente (mesmo do zip de álbum, ADR-030) expor o APK baixado.
- **ACHADOS AO VIVO 15/09/2026 (2 bugs reais, corrigidos antes de travar em qualquer coisa):**
  1. `GET /releases/latest` devolve 404 sempre nesse repo - esse endpoint IGNORA
     prereleases (doc do GitHub: "the most recent non-prerelease, non-draft release"), e
     TODA release publicada pelo push automático na master sai `prerelease: true` (só uma
     tag manual `v*` sairia "de verdade" - ver ADR de baixo do build-apk.yml). Trocado pra
     `GET /releases` (listagem, mais recente primeiro) + pega o item `[0]`.
  2. Mesmo corrigido o endpoint, a checagem continua retornando nada: **o repositório
     `wellpailer64-dev/PailerFM` está PRIVADO** (confirmado via `gh repo view`). A API do
     GitHub devolve 404 (não 403, de propósito - não revela nem que o repo existe) pra
     qualquer leitura anônima em repo privado, prereleases ou não. Sem autenticação não dá
     pra checar releases; e colocar um token no app é inseguro (qualquer um extrai de um
     APK distribuído).
- **Decisão tomada 17/09/2026:** usuário confirmou com o parceiro e pediu pra tornar o
  repositório público. Antes de mexer, conferido que nunca vazou nada sensível pro
  histórico do git (`git log --all` por nome de arquivo e por conteúdo - sem
  `keystore.properties`, `*.jks`, token ou senha em commit nenhum; os três sempre
  estiveram no `.gitignore`). Repo trocado pra público via `gh repo edit --visibility
  public --accept-visibility-change-consequences`. Checagem anônima confirmada
  funcionando de ponta a ponta: `curl` sem token em
  `GET /repos/wellpailer64-dev/PailerFM/releases?per_page=1` devolve o release mais
  recente com o asset do APK.
- **Achado no caminho: CI estava quebrado há dias, sem nenhum Release novo saindo.**
  Investigando por que a checagem não tinha nada mais recente que oferecer (última
  release era de 12/09/2026, cinco dias parada, mesmo com pushes normais acontecendo),
  descoberto que `Build APK` falhava rápido (10-27s) em TODO push desde pelo menos
  16/09/2026 - `android-actions/setup-android@v3` tenta instalar o pacote legado
  `tools` via sdkmanager, que a Google removeu do repositório do SDK
  (`Warning: Failed to find package 'tools'`). Corrigido em 3 rodadas (achar o
  `sdkmanager` real do runner deu 2 tentativas erradas antes de um diagnóstico
  completo confirmar o caminho - ver histórico de commits em `.github/workflows/
  build-apk.yml` do mesmo dia). Build de teste rodou verde em 19m46s e publicou
  `v2026.09.17-39` - primeira release nova em 5 dias.
- **Otimização no mesmo commit: removido `ndk;26.1.10909125`/`cmake;3.22.1` do
  `sdkmanager --install`.** O app não compila mais nada nativo via CMake/NDK desde a
  remoção da síntese de voz local (ADR-035, 16/09/2026) - sem `externalNativeBuild`
  em `app/build.gradle.kts`, sem `.so`/`jniLibs`/`CMakeLists.txt` no projeto. Baixar
  ~1GB de NDK+CMake em toda rodada de CI sem nenhum uso real era o principal motivo do
  build ficar lento - e ficaria lento em TODO run, não só no primeiro (GitHub Actions
  roda numa VM nova sempre; só `actions/cache` explícito sobrevive entre runs, e esse
  cache é só das dependências Gradle/Kotlin, não do SDK).
- **Não mudar sem avisar antes:** repositório público expõe todo o histórico git, não
  só o código atual - qualquer um pode clonar/dar fork a partir de agora. Se decidirem
  voltar a privado no futuro, cópias já feitas por terceiros nesse meio-tempo não são
  revogadas (GitHub não força-apaga forks quando a origem vira privada de novo).

## ADR-034 — Feed remoto de boletins aprovados (Cloudflare) vira fonte prioritária do buffer

- **Contexto:** a central local de broadcast (`_broadcast-boletins-local/`, ver
  [CONTROL_PANEL.md](../_broadcast-boletins-local/CONTROL_PANEL.md)) já produz boletins,
  passa por revisão humana e, ao aprovar, publica um feed estático
  (`manifest.json` + áudio `.wav` + roteiro `.json`) num Cloudflare Worker
  (`pailer-fm-boletins.well-pailer64.workers.dev`) via botão `Publicar Cloudflare` /
  Wrangler (16/09/2026). Até aqui, esse feed não tinha nenhum consumidor: o app
  continuava gerando todo boletim sozinho (RSS → redator → síntese local/Gemini).
  Pedido do usuário: fechar o circuito - o que é aprovado na central deve chegar tocável
  no app sem passar de novo por redator/síntese.
- **Decisão:** novo `BroadcastFeedRepository` (`data/BroadcastFeedRepository.kt`) que lê o
  `manifest.json` publicado e, para o primeiro item `status == "approved"` ainda não
  reservado no buffer local (mesma chave de dedup de `currentReservedBulletinStoryKeys()`,
  normalizada por `normalizedRemoteKey()` - acento/pontuação não contam como boletim
  diferente), baixa o `.wav` e o roteiro (`script.path`) para `coreBufferDir` (a mesma
  pasta persistente do buffer atual, sobrevive a reinício do app). Confere o áudio baixado
  por tamanho (`bytes`) e, se o manifest trouxer, por `sha256`, antes de aceitar o arquivo
  como usável; sem roteiro publicado, cai numa única fala de fallback ("Boletim aprovado
  da Pailer FM: <título>"). Novo `RadioScriptSource.BroadcastFeed` (`RadioBulletin.kt`)
  marca a origem para instrumentação/depuração futura.
- **Prioridade no buffer:** em `refillBulletinBuffer()` (`LocalTuneViewModel.kt`), cada
  vaga livre tenta primeiro `broadcastFeedRepository.downloadNextApprovedBulletin()` antes
  de gastar RSS/redator/síntese local. Um flag local por rodada
  (`remoteFeedExhaustedThisPass`) evita bater na rede de novo assim que o feed volta vazio
  nessa passagem - as vagas restantes seguem direto pro caminho local (RSS → redator →
  síntese), sem esperar timeout de rede a cada item. Item aprovado entra no buffer como
  `PreparedBulletin` normal (`scriptFromGemini = false`, `voiceFromGemini = false` - o
  trabalho de redação/voz já foi feito na central, não na nuvem) e participa do
  `dequeueBufferedBulletinForPlayback()` e da limpeza de órfãos igual qualquer outro item.
- **Falha segura:** qualquer erro de rede/parse em `downloadNextApprovedBulletin()` é
  capturado (`runCatching` + log `PailerBroadcastFeed`) e devolve `null` - o app nunca
  trava nem mostra erro pro usuário final, só segue pro preparo local normal. Isso também
  cobre o caso de feed remoto indisponível quando RSS também falhou: antes, buffer vazio +
  RSS vazio desistia da rodada; agora só desiste depois de tentar o feed remoto também.
- **Não mudar sem avisar antes:** o feed é só leitura (GET, sem token/autenticação) - a
  central publica, o app só consome. Se a URL do Worker mudar (novo domínio/projeto
  Cloudflare), atualizar `BASE_URL`/`MANIFEST_URL` em `BroadcastFeedRepository.kt`.

## ADR-035 — Dois bugs reais do feed remoto corrigidos ao vivo + remoção total do redator/voz local

- **Contexto:** no dia seguinte à ADR-034 (16/09/2026), o usuário testou o feed remoto no
  aparelho de verdade (celular plugado, `adb logcat` acompanhando). Dois bugs apareceram
  na prática, ambos corrigidos na hora. Logo depois, o usuário decidiu que TUDO relacionado
  a gerar boletim dentro do app (redator local/Gemini, voz local/Gemini, RSS, TTS Android)
  devia sair - a central de broadcast externa já cobre esse trabalho por completo agora.
- **Bug 1 - dedup de `reservationKey` quebrada em `BroadcastFeedRepository.kt`:** a chave
  era montada normalizando a string `"fonte|título"` INTEIRA de uma vez
  (`normalizedRemoteKey()`, que troca todo caractere não-alfanumérico por espaço) - o `|`
  separador virava espaço junto com os outros, produzindo uma chave sem delimitador
  nenhum entre as duas partes. `RadioScript.newsReservationKey()` (LocalTuneViewModel.kt)
  normaliza fonte e título SEPARADAMENTE e só depois junta com `|` literal - os dois
  formatos nunca batiam, então a comparação `reservationKey in reservedKeys` nunca dava
  match e o mesmo boletim aprovado era baixado repetidas vezes (visto ao vivo: o buffer
  inteiro de 10 vagas encheu com 10 cópias do mesmo `id`). Corrigido normalizando fonte e
  título separadamente antes de juntar com `|`, reproduzindo exatamente a fórmula do
  ViewModel.
- **Bug 2 - hang de rede não respeitava timeout algum:** `downloadNextApprovedBulletin()`
  usa `HttpURLConnection` puro com `connectTimeout`/`readTimeout` de 12s, e o chamador
  ainda envolvia a chamada inteira num `withTimeoutOrNull(REMOTE_FEED_TIMEOUT_MS)` (15s) -
  mesmo assim, uma rede com DNS/handshake lento travou a chamada por mais de 5 minutos ao
  vivo, congelando a rodada INTEIRA do buffer (nem item remoto nem qualquer fallback
  avançava). Causa raiz: `HttpURLConnection.connect()`/`getInputStream()` são chamadas
  bloqueantes de Java puro sem ponto de suspensão - cancelar a coroutine
  (`withTimeoutOrNull`) não interrompe essa thread sozinha, ela só retorna quando a
  chamada de rede devolver o controle de verdade. Corrigido com um watchdog manual:
  `BroadcastFeedRepository` guarda a `HttpURLConnection` ativa num campo `@Volatile`, e
  registra `coroutineContext.job.invokeOnCompletion { if (cause is CancellationException)
  activeConnection?.disconnect() }` no início de `downloadNextApprovedBulletin()` - quando
  o `withTimeoutOrNull` do chamador cancela a coroutine, o handler força um
  `disconnect()` de verdade na conexão presa, que aí sim lança `IOException` e libera a
  thread. Sem isso, `withTimeoutOrNull` sozinho é enganoso pra qualquer chamada de rede
  bloqueante deste projeto (mesmo padrão usado em `NewsBulletinRepository`/Gemini, agora
  removidos - ver abaixo).
- **Decisão (mudança grande, mesmo dia):** pedido explícito do usuário - "não teremos mais
  essas configurações internas da rádio de redator local, de pacote de vozes,
  acompanhamento de buffer... porque agora essa tarefa vai ser deixada toda pra fora".
  Confirmado via pergunta direta: (1) sem boletim aprovado no feed, a vaga fica vazia -
  **nenhum fallback local, nunca** (antes: RSS→redator→síntese ou TTS Android); (2) tela
  "Boletins da rádio" some inteira das Configurações, sem ficar nem um card de status.
- **O que foi removido (arquivos inteiros):**
  - Redator: `RadioWriterPackageRepository.kt` (pacote do LLM local + `LocalLlamaTextGenerator`
    + JNI), `app/src/main/cpp/pailer_llama_jni.cpp` + `CMakeLists.txt`, blocos
    `externalNativeBuild`/`ndk.abiFilters` do alvo nativo em `app/build.gradle.kts` (o
    `ndk.abiFilters` do app em si fica, ainda restringe ABI do APK).
  - RSS: `NewsBulletinRepository.kt` (só tinha esse consumidor - `ArtistNewsRepository.kt`
    é feature separada de notícia de artista, não depende dele).
  - Voz: `LocalRadioVoiceEngine.kt`, `RadioVoiceSynthesisService.kt` (+ entrada
    `<service>` no `AndroidManifest.xml`, processo `:radio_voice`), `RadioVoicePackageRepository.kt`,
    `GeminiFlashTtsEngine.kt`, `RadioBulletinTts.kt` (`BulletinTtsProvider`/`GeminiTtsModel`/
    `GeminiTtsVoices`), dependência do AAR `sherpa-onnx-static-link-onnxruntime-1.13.6` (arquivo
    apagado + linha do `build.gradle.kts`).
  - `RadioBulletin.kt` ficou só com os data classes compartilhados (`RadioBulletinSettings`
    sem `preferLocalWriter`/`cloudWriterEnabled`/`ttsProvider`/`ttsModel`, `NewsStory`,
    `RadioScript`/`RadioScriptLine`/`RadioSpeaker`, `RadioScriptSource`) - toda a
    maquinaria de escrita (`RadioScriptWriter`, `RadioBulletinRepository`,
    `RemoteGeminiRadioScriptWriter`, `OptionalLocalLlmRadioScriptWriter`,
    `FallbackRadioScriptWriter`, prompt builders, parsers, bancos de texto por tema,
    `withPhilosophicalCloser()`) e `GeminiApiKeySettings` (último consumidor era a TTS)
    foram removidos.
  - `docs/TTS.md` apagado (documentava só a máquina removida).
  - Tela "Boletins da rádio" inteira em `LocalTuneApp.kt`: `RadioBulletinSettingsPanel`,
    `RadioBulletinBufferStatusCard`, `GeminiApiKeysSettingsPanel`, `RadioBulletinChoiceRow`,
    entradas `SettingsPage.RadioBulletins`/`.GeminiApiKeys`, o item de nav "Boletins" em
    "Radio Settings".
- **`refillBulletinBuffer()` simplificado (`LocalTuneViewModel.kt`):** cada vaga livre
  tenta `broadcastFeedRepository.downloadNextApprovedBulletin()`; `null` faz a rodada
  inteira desistir (`break`) sem tentar mais nada. `speakNextNewsBreak()` idem: sem áudio
  tocável no buffer, cancela a entrada incondicionalmente (removida a checagem
  `radioVoiceState.value.isEnabled` que só fazia sentido quando existia alternativa de
  TTS Android para "voz desligada").
- **Achado no meio do caminho - guard esquecido:** `speakNextNewsBreak()` começava com
  `if (!ttsReady || speakingNews) return` - `ttsReady` só virava `true` depois do callback
  assíncrono de `setupTextToSpeech()` (TextToSpeech do Android). Removendo TTS sem tirar
  esse guard, boletim NUNCA mais tocaria (guard sempre falso pra sempre) mesmo com WAV
  pronto no buffer - pego e corrigido antes de compilar, guard virou só `if (speakingNews)`.
- **O que ficou de propósito, mesmo sem UI pra acionar:** `pauseBulletinPreparation()`/
  `resumeBulletinPreparation()`/`resetBulletinBuffer()`/`fixFallbackBulletins()`/
  `playReadyBufferedBulletin()` continuam declaradas no ViewModel (não removidas) - só
  perderam todo caller de UI. Ficam como API interna morta, não removidas por segurança/
  tempo (remover exigiria reabrir `refillBulletinBuffer()` de novo, risco desnecessário no
  fim de uma sessão já grande). `RadioScriptSource.LocalLlm`/`.Fallback` também continuam
  no enum (só `BroadcastFeed` é alcançável na prática hoje) - simplificar pra um valor só
  fica pra uma limpeza futura, exige tocar na deserialização do manifest em disco.
- **Verificação:** cada estágio (buffer/playback → redator/RSS → voz/tela) compilou e foi
  instalado no aparelho de verdade entre um estágio e o próximo (mesmo fluxo de
  `adb install -r` + `logcat` desta sessão) - nenhum estágio quebrou o build na primeira
  tentativa depois do estágio 1.

## ADR-036 — Prazo de validade real dos boletins (painel + Cloudflare + app) e depuração grande da central local

- **Contexto (17/09/2026):** sessão inteira de depuração/evolução na central local
  (`_broadcast-boletins-local/`, fora deste app - ver
  [BROADCAST_METADATA.md](BROADCAST_METADATA.md)), não só nesse app Android. Como o app
  hoje **não tem mais fallback nenhum** (ADR-035 - sem boletim aprovado no feed, a vaga
  fica vazia), a qualidade/frescor do que a central produz passou a ser a única fonte de
  boletim que existe, sem rede de segurança. Resumo do que foi corrigido/adicionado do
  lado da central (todo o trabalho pesado é Python, fora deste repo Android):
  1. **Card preso pra sempre na síntese**: bug de chave errada gravava a revisão humana
     (Eliminar/Refazer) sob o `job_id` interno em vez do `id` estável do inventário -
     card nunca saía da tela. Corrigido na UI (`ui/app.js`) e desprendido manualmente o
     caso já travado.
  2. **GPU disputada entre redator e síntese de voz**: Ollama mantinha o Qwen3 14B
     carregado na VRAM (8GB, RTX 3050) por minutos após cada redação, brigando com a
     OmniVoice pela mesma GPU. Corrigido com `keep_alive: "0"` na chamada ao Ollama -
     libera a GPU assim que a redação termina, antes da síntese começar.
  3. **Fonte "g1 Ciência e Saúde" sempre falhava**: servidor manda a resposta em gzip
     mesmo sem pedir; o parser de RSS não descomprimia antes de tentar ler XML.
     Corrigido (`gzip.decompress`), e adicionado suporte gzip em todas as buscas de RSS.
  4. **13 fontes novas adicionadas** (de 5 pra 18 portais: Ciência Hoje, Pesquisa FAPESP,
     Galileu, Aventuras na História, Mental Floss, Smithsonian, JSTOR Daily, Aeon,
     Nautilus, ScienceAlert, Live Science, The Conversation Brasil, Oddity Central) - 3
     candidatos ficaram de fora (Mega Curioso: domínio morto, redireciona pro Estadão;
     National Geographic Brasil: sem RSS público; Atlas Obscura: bloqueia bot com 403).
  5. **Política de uso por fonte (direito autoral)**: nenhum portal RSS é banco de texto
     - a raspagem de corpo integral do artigo foi **desligada por padrão pra todas as
     fontes** (`SOURCE_POLICY`/`ai_ingestion_allowed`). O redator recebe só
     fonte+título+resumo do próprio RSS. Em compensação, a mesma página que já era
     baixada passou a ser vasculhada só por **links de fonte primária** (domínios
     `.gov`/`.edu`/`nature.com`/`arxiv.org`/instituições de pesquisa BR) e **autoria**
     (meta tags), sem guardar o texto do artigo - essa proveniência (fonte, licença,
     `primary_sources[]`, `authors[]`) fica gravada por boletim e aparece em
     `source_record.provenance` dentro do `manifest.json` publicado.
  6. **3 bugs reais de categorização** achados testando com dados reais: nome da fonte
     ("Olhar Digital" continha "digital", forçava tudo pra tecnologia; "g1 Mundo" continha
     "mundo", forçava tudo pra geopolítica); a palavra "jogo"/"jogos" batendo em
     expressões sem nada a ver com videogame ("jogo de poder", "jogos de hoje" = futebol);
     e "espaço" (espaço físico da sala) sendo confundido com espaço sideral.
  7. **Classificação `temporal`/`evergreen` nunca funcionou de verdade**: uma linha
     forçava todo boletim pra `temporal` incondicionalmente, e a lista de sinais tinha
     lixo de teste esquecido (`"cyberpunk"`, `"redator14b"`). Reescrita com sinais reais
     de urgência ("hoje", "resultado", "anuncia"...) e de atemporalidade ("curiosidade",
     "mito", "origem de"...), com a categoria como desempate.
  8. **Prazo de validade com apagar de verdade** (decisão explícita do usuário - notícia
     vencida não tem valor de guardar): `purge_expired_bulletins()` roda a cada rodada do
     pipeline e apaga roteiro/áudio/metadata/proveniência de boletins vencidos, tanto os
     ainda ativos quanto os já publicados só em `distribuicao-app/public/`. Janelas: 24h
     pra categorias "aconteceu agora" (geopolítica/tecnologia/saúde/geral), 72h pras
     demais categorias temporais, 60 dias de prateleira pra atemporal (não é infinito).
     O relógio conta a partir da publicação original da notícia (capturada do RSS), não
     de quando a central produziu. Cada item do `manifest.json` ganhou o campo
     `expires_at` (ISO8601 UTC) pra qualquer consumidor (painel, Cloudflare, este app)
     aplicar a mesma regra.
  9. **Site do Cloudflare reorganizado**: cards separados em duas seções empilhadas
     (⏱ Temporal / ♾ Atemporal), cada uma com subseções por categoria, cards menores e
     selo de prazo (`vence em Xh`/`vencido`/`prateleira Xd`) direto no card.
- **Decisão (lado deste app):** `BroadcastFeedRepository.kt` passou a checar
  `item.optString("expires_at")` antes de baixar qualquer boletim do manifest - item
  vencido é pulado (mesmo `for` que já ignora `status != "approved"`), sem baixar áudio à
  toa nem tocar conteúdo desatualizado. Parse via `java.time.Instant.parse()` (nativo
  desde API 26, `minSdk` deste projeto - sem desugaring extra); string vazia ou formato
  inesperado é tratado como **não vencido** (fail-open) - prefere baixar um item sem data
  a esconder o feed inteiro por um formato de data que mudou sem avisar.
- **Motivo:** fechar o mesmo circuito do ADR-034/035 (central produz, app só consome) pro
  eixo de frescor de conteúdo - sem isso, um boletim de notícia velha podia ficar dias
  tocável no app só porque já tinha sido baixado antes de vencer.
- **Verificação:** `./gradlew :app:compileDebugKotlin` rodou limpo (só warnings
  pré-existentes de outro arquivo, nada relacionado a esta mudança). O lado Python foi
  testado à parte, com um boletim falso criado/vencido/apagado de propósito antes de
  rodar contra os dados reais (ver commit da central local).
- **Não mudar sem avisar antes:** o `expires_at` é calculado inteiramente do lado da
  central (Python) - este app só lê e compara contra `Instant.now()`, nunca recalcula
  prazo nenhum. Se o formato do campo mudar (hoje é sempre `AAAA-MM-DDTHH:mm:ssZ` UTC),
  atualizar o parser aqui junto.
- **Pendência conhecida, não implementada nesta sessão:** um boletim que o app **já
  baixou** pro buffer local (`coreBufferDir`) e ainda não tocou não é reavaliado contra
  `expires_at` depois do download - só boletins ainda não baixados são pulados. Fechar
  esse ciclo (buffer local também descarta sozinho o que já venceu) exige entender o
  ciclo de vida do buffer em `LocalTuneViewModel.kt`
  (`refillBulletinBuffer`/`saveCoreBufferManifest`/limpeza de órfãos) antes de mexer -
  não investigado a fundo ainda, fica pra uma próxima sessão. Ver item correspondente em
  [TODO.md](TODO.md).

## ADR-037 — IMPLEMENTADO (17/09/2026) — Boletim "especial" por pedido direto, com prioridade de produção e reprodução

- **Status:** implementado dos dois lados (painel Python e este app) em 17/09/2026, na
  mesma sessão em que foi desenhado. Ver
  [CONTROL_PANEL.md](../../_broadcast-boletins-local/CONTROL_PANEL.md) (seção
  "Implementado — Recados / Publis") pro lado do painel.
- **Contexto (17/09/2026):** ideia do usuário, surgida em conversa. Registrado como ADR
  (mesmo padrão do ADR-033 - "decisão de direção primeiro, implementação documentada
  depois") pra não perder o desenho entre sessões.
- **Objetivo:** gerar um boletim sem depender de nenhuma manchete de RSS. O usuário digita
  um pedido livre (um assunto, um recado, uma "publi") e isso vira o prompt pro Qwen3 14B
  escrever o bate-bola Fran/Nico sobre aquele assunto específico - o mesmo redator de hoje,
  só que a matéria de entrada é o pedido do usuário, não uma notícia captada.
- **Fluxo pretendido (lado painel, fora deste app):** campo de pedido livre na UI do
  painel → novo caminho de redação que pula seleção/enriquecimento de notícia e monta o
  prompt direto do pedido → roteiro nasce com `content_type: "especial"` (hoje o sistema só
  tem `"temporal"`/`"evergreen"`) → furando fila de produção (próximo a ser sintetizado,
  na frente da reserva normal) → aparece na nova coluna "📣 Recados / Publis" do site
  publicado (já existe no ar, vazia - ver commit de hoje em
  `distribution_index_html()`/`broadcast_core.py`).
- **Decisão (lado deste app):** um boletim `especial` recém-baixado do feed remoto **fura a
  fila do buffer local** - se o app já tiver vários boletins baixados esperando a vez, o
  especial vira o próximo a tocar, não entra no fim da lista.
- **Implementação (lado app):**
  1. `RadioScript` (`RadioBulletin.kt`) ganhou `isSpecial: Boolean = false`.
  2. `BroadcastFeedRepository.downloadNextApprovedBulletinBlocking()` lê `content_type` de
     cada item do manifest e ordena os candidatos (`sortedByDescending`, estável) pra
     tentar baixar um `especial` ANTES de qualquer outro item, mesmo que ele não seja o
     primeiro do array — sem isso, um especial só seria baixado quando chegasse a vez dele
     na ordem que o painel Python intercala por categoria (que não sabe de prioridade
     nenhuma). Lógica por-item extraída pra `tryDownloadApprovedItem()` (era um único loop
     grande antes) pra reaproveitar sem duplicar as ~30 linhas de validação/download.
  3. `LocalTuneViewModel.refillBulletinBuffer()`: item com `isSpecial == true` entra com
     `bulletinBuffer.addFirst()` em vez de `addLast()`. `dequeueBufferedBulletinForPlayback()`
     continua um `removeFirst()` simples, sem nenhuma mudança — a prioridade toda vem da
     ORDEM DE INSERÇÃO, não de lógica extra no consumo.
  4. `saveCoreBufferManifest()`/`loadCoreBufferManifest()`: campo `"special"` persistido no
     JSON do manifest local (`optBoolean("special", false)` na leitura — manifest salvo
     antes desse campo existir volta como não-especial, mesmo padrão de
     `scriptFromGemini`/`voiceFromGemini`).
  5. Verificado: `./gradlew :app:compileDebugKotlin` limpo (sem warning novo nos arquivos
     tocados), `assembleRelease` instalado por cima do app já no aparelho de teste via
     `adb install -r` (sem perder dados - mesma chave de release dedicada, ver
     [RELEASE.md](RELEASE.md)) e o app abriu sem crash (`adb logcat` sem `FATAL`).
  6. **Vagas reservadas (mesmo dia, pedido do usuário logo depois de testar):** o app
     mantém o buffer sempre cheio (`BULLETIN_BUFFER_TARGET = 10`), então um buffer já
     cheio de itens normais só buscaria um especial recém-aprovado depois de esvaziar
     tudo até abrir vaga de verdade - podia demorar bastante tocando rádio. Corrigido com
     `BULLETIN_BUFFER_SPECIAL_RESERVED_SLOTS = 3`: `refillBulletinBuffer()` para de
     aceitar item NORMAL assim que o buffer já tem `10 - 3 = 7` normais, e só busca
     especial pras 3 vagas que sobraram (`downloadNextApprovedBulletin(specialOnly=true)`,
     novo parâmetro em `BroadcastFeedRepository`, filtra em vez de só ordenar). Efeito
     colateral aceito de propósito: o buffer "normal" efetivo cai pra 7 (não mais 10)
     sempre que não há especial pendente - é a troca certa pra abrir espaço rápido pra
     um especial sem esperar o buffer inteiro drenar.
- **Terceira correção, mesmo dia (usuário recusou depender do botão manual "Resetar"):**
  `checkForSpecialOnAppOpen()`, chamado uma vez no `init` do `LocalTuneViewModel`,
  SEQUENCIAL e ANTES de `refillBulletinBuffer()` (mesma corrotina - evita baixar o
  mesmo especial duas vezes em paralelo). Ignora `BULLETIN_BUFFER_TARGET` de propósito:
  mesmo com o buffer já cheio (`refillBulletinBuffer()` sozinho seria no-op aqui, nem
  chega a consultar o feed), este método ainda baixa e insere o especial via
  `addFirst()`. Pode deixar `bulletinBuffer` com `TARGET + 1` item temporariamente -
  aceito de propósito, nunca descarta um item normal já baixado; a fila assenta de
  volta no teto sozinha assim que esse especial tocar. No-op rápido (sem rede) se já
  existe um especial no buffer, pra nunca empilhar mais de um furando fila ao mesmo
  tempo. `BroadcastFeedRepository.downloadNextApprovedBulletin()`/
  `tryDownloadApprovedItem()` ganharam log de diagnóstico (`logSkipReason`) explicando
  por que um candidato foi pulado em modo `specialOnly` (vencido, já reservado etc.) -
  útil pra depurar em campo sem acesso ao storage privado do app (build release não é
  debuggable, `adb shell run-as` não funciona).
- **Confirmado ao vivo 17/09/2026:** o item já estava no buffer via o mecanismo de vagas
  reservadas da segunda correção (o usuário usou o app entre um build e outro, tocou
  música suficiente pra abrir as vagas reservadas, e o refill normal já tinha pego o
  especial e posto na frente) - `checkForSpecialOnAppOpen()` rodou e confirmou
  "já tem especial no buffer, pulando" em vez de precisar baixar de novo. Como a única
  forma de um item sair da frente do buffer é tocar (`removeFirst()`), ele estar
  presente confirma que ainda está na posição 0, não tocado - exatamente o resultado
  que o usuário pediu, sem apertar nenhum botão.
- **Limitação conhecida que continua (não resolvida, não é bug):** nenhuma das
  correções força "abrir vaga" DESCARTANDO um item normal já baixado - só tolera
  `TARGET + 1` temporariamente (`checkForSpecialOnAppOpen`) ou reserva 3 vagas de
  antemão (segunda correção). Um buffer que já estava cheio de itens normais ANTES de
  qualquer uma das três correções (build antiga, nunca atualizada) continua precisando
  de uma abertura de vaga natural (tocar algo) ou do botão "Resetar" a primeira vez -
  dali em diante o sistema se autorregula sozinho em toda abertura do app. Furar fila =
  tocar antes do que ainda não foi baixado, não = interromper o que já está pronto.
- **Motivo:** usuário quer um canal de "recado direto" (avisos, publis, pedidos pontuais)
  que fure a programação normal, diferente de notícia temporal ou curiosidade atemporal -
  daí a cor vermelha e a posição no topo da UI (sinalização visual de "isso é prioritário").
- **Decisões de design (resolvidas 17/09/2026, ver CONTROL_PANEL.md pro detalhe do lado
  painel):**
  1. `"especial"` é um `content_type` novo, paralelo a temporal/evergreen (não uma flag
     por cima de outro tipo).
  2. Tem prazo de validade: 1 semana (`EXPIRY_SPECIAL_HOURS` em `broadcast_core.py`),
     depois disso some do feed pelo mesmo mecanismo de expurgo dos demais.
  3. Passa por revisão humana: fica em "redação" esperando aprovação manual (botão
     "Sintetizar") em vez de entrar sozinho no funil como o resto do pipeline.
  4. Sem limite de quantos especiais ficam pendentes - não apareceu necessidade na prática
     ainda; revisitar se virar problema real.
- **Não mudar sem:** manter a garantia de que um manifest sem nenhum item `especial` se
  comporta **exatamente** como antes desta ADR (zero mudança de comportamento pra quem
  nunca usar a feature) - `isSpecial` sempre `false` por default em toda leitura/escrita.

## ADR-038 — Home: margem do card do disco + legenda sempre tenta sincronia + rótulo de letra manual

- **Contexto (17/09/2026):** três pedidos do usuário testando o app depois da ADR-037,
  primeira leva de mudanças usando a auto-atualização in-app recém-ativada (ADR-033) em
  vez de instalar via ADB.
- **1. Card do disco colado no topo:** `HomeScreen`, o `LazyColumn` principal (card do
  disco/radio ao vivo é o primeiro item) não tinha `contentPadding` nenhum no topo (só
  `bottom = 18.dp`) - o card ficava colado direto na status bar. Corrigido com
  `top = 12.dp`, sem mexer em mais nada da estrutura (sem header novo).
- **2. Legenda (letra sincronizada sobre o disco) nunca acompanhava a música em várias
  faixas:** `HomeScreen`/`FullPlayer` só disparavam busca online de letra automaticamente
  quando NÃO havia letra nenhuma (`lyrics.lyrics.isEmpty`). Letra incorporada na tag do
  arquivo (ID3 `USLT` e equivalentes) quase nunca tem timestamp - carregava sem sincronia
  e nunca tentava melhorar, mesmo com internet disponível e o usuário nunca percebendo
  que dava pra ter algo melhor. Corrigido: condição virou `!lyrics.lyrics.synced` (dispara
  também quando há letra mas sem sincronia, não só quando está vazia) chamando uma função
  nova, `LocalTuneViewModel.autoUpgradeLyricsSyncIfNeeded()`, em vez de
  `fetchLyricsOnline()` direto - essa função só SUBSTITUI a letra atual se achar uma
  versão sincronizada online (ou se não havia nada carregado antes); se a busca só achar
  outro texto sem sincronia, mantém o que já estava mostrando (evita trocar uma letra
  incorporada boa por outra pior/diferente só porque "achou alguma coisa" em outra fonte)
  e nunca mostra mensagem de erro (é um upgrade silencioso em segundo plano, não um pedido
  explícito do usuário). `fetchLyricsOnline()` original continua intacto, ainda usado pelo
  botão explícito "Buscar letra online" no menu de opções - lá faz sentido aplicar
  qualquer coisa que achar, porque foi pedido na hora.
- **3. "Escrever letra" quando não existe nenhuma:** já existia de verdade - o botão
  "Colar letra" (vazio) / "Editar letra" (com letra) sempre abriu o mesmo
  `LyricsEditorDialog` com um `OutlinedTextField` comum, que aceita digitar OU colar
  igualmente (é só um campo de texto multi-linha). O problema era só o RÓTULO: "Colar
  letra" e o placeholder "Cole a letra aqui" sugeriam que só dava pra colar, escondendo
  que dava pra digitar a letra do zero. Renomeado pra "Adicionar letra" (botão e item do
  menu) e placeholder pra "Cole ou escreva a letra aqui (...)" - mesma funcionalidade de
  sempre, só deixando claro que as duas formas funcionam. Nenhuma mudança de código no
  fluxo de salvar (`LyricsRepository.save()`/`LyricsSource.MANUAL` já cobriam isso).
- **Verificado:** `./gradlew :app:compileDebugKotlin` limpo, sem warning novo (inclusive
  removido `onFetchLyrics` de `HomeScreen`, que ficou sem uso depois da mudança #2 - só
  `FullPlayer` ainda precisa dele, pro botão explícito).
- **Teste combinado com a ADR-033:** usuário confirmou essas três mudanças instalando via
  o popup de auto-atualização (push → CI → Release novo → app pede pra atualizar
  sozinho), não via `adb install` manual - primeiro teste de ponta a ponta da
  auto-atualização desde que foi ativada. Ver achados do teste e a feature de changelog
  resumido que saiu dele na ADR-039 abaixo.

## ADR-039 — Primeiro teste real da auto-atualização: 2 achados + changelog resumido no popup

- **Contexto (17/09/2026):** primeira vez testando a auto-atualização (ADR-033) de ponta
  a ponta, depois das três mudanças da ADR-038. Dois problemas reais apareceram no
  caminho, mais um pedido de melhoria pro popup.
- **Achado 1 - build local nunca detecta atualização, de propósito:** o app instalado no
  aparelho de teste tinha sido compilado localmente (`assembleRelease` direto, sem CI),
  então `BuildConfig.RELEASE_TAG` era `"local-dev"` - e
  `UpdateCheckRepository.isNewerThanCurrent()` desliga a checagem inteira nesse caso
  (`if (BuildConfig.RELEASE_TAG == "local-dev") return false`, decisão de propósito da
  ADR-033: notificar sobre uma build de teste não faz sentido). Não é bug - mas quer
  dizer que **build local nunca serve de base pra testar o popup de atualização**;
  precisa de uma build de verdade do CI instalada primeiro (ex. baixada de um Release
  mais antigo via `gh release download`) pra ter uma tag real pra comparar.
- **Achado 2 - upload do APK pro Release trava/falha aleatoriamente:** o passo "Publica
  Release automático" já vinha demorando mais que o resto do pipeline (visto antes, sem
  explicação clara). Na tentativa seguinte, o upload do APK (35MB, tamanho normal) deu
  `Headers Timeout Error` depois de ~7min parado em "Uploading PailerFM.apk..." -
  provável instabilidade da API de asset upload do GitHub combinada com
  `softprops/action-gh-release@v2`, não algo errado na configuração (tamanho do arquivo
  normal, mesma operação que às vezes completa em segundos). Deixou pra trás uma release
  **publicada mas sem asset nenhum** (`v2026.09.17-41` original, 0 assets) - perigoso
  porque `UpdateCheckRepository` pega sempre o item `[0]` de `GET /releases?per_page=1`
  (o mais recente): com uma release quebrada na frente, o app nunca chegaria nem na
  release anterior que funcionava. Corrigido ao vivo: `gh release delete ... --cleanup-tag`
  pra apagar a release quebrada e a tag junto, depois `gh run rerun --failed` (reaproveita
  o build já compilado, só repete os passos que falharam) - sucesso na 2a tentativa.
  **Não mudar sem saber:** se isso acontecer nas próximas, o mesmo par de comandos
  resolve; não decidido ainda se vale a pena automatizar um retry dentro do próprio
  workflow (ex. `nick-invision/retry` em volta do passo de publicar).
- **Melhoria pedida pelo usuário, implementada na mesma sessão:** o popup mostrava só "A
  versão vX.Y.Z já está disponível", sem dizer o que mudou. Agora o corpo da release no
  GitHub (campo `body`) vira um changelog resumido automático - um bullet por commit
  desde a release anterior, só a PRIMEIRA linha da mensagem (nunca o corpo técnico
  detalhado). `LatestReleaseInfo` (`UpdateCheckRepository.kt`) ganhou `notes: String?`
  lido direto do `body` da release; `UpdateAvailableDialog` (`LocalTuneApp.kt`) mostra
  "Novidades da vX.Y.Z:" + os bullets quando `notes` existe, cai pro texto genérico
  antigo quando não (releases antigas, ou tag manual sem changelog).
  - `build-apk.yml`: `actions/checkout@v4` ganhou `fetch-depth: 0` (histórico completo +
    tags - o checkout raso padrão não tem tags antigas pra comparar). Novo passo "Monta
    changelog resumido": `git describe --tags --abbrev=0 HEAD^` acha a tag anterior
    alcançável a partir do commit PAI (a tag desta build ainda não existe nesse ponto do
    workflow); `git log <tag-anterior>..HEAD --pretty=format:'- %s' --no-merges` monta um
    bullet por commit. Sem tag nenhuma ainda (primeiro release do repo), cai pros últimos
    20 commits.
  - **Não decidido / limitação aceita:** os bullets são as mensagens de commit CRUAS
    (escritas pra outra sessão/desenvolvedor entender o porquê técnico, não pro usuário
    final) - às vezes um commit vai ser algo tipo "CI: corrige maxdepth do find" que não
    diz muito pra quem só quer saber "o que ficou melhor". Aceito por enquanto (pedido
    explícito do usuário era "o mais resumido possível", sem curadoria manual por
    release) - revisitar se os changelogs ficarem confusos na prática.
- **Verificado:** `./gradlew :app:compileDebugKotlin` limpo, YAML do workflow validado
  (`python -c "import yaml; ..."`). Não testado ainda ao vivo (próxima release já sai com
  isso, sem push nesta sessão - pedido do usuário).
