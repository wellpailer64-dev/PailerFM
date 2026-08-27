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

