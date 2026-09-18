# TODO / Dívida Técnica

> **Regra de ouro deste projeto:** preservar o comportamento atual antes de refatorar.
> Toda mudança estrutural mantém as funcionalidades existentes e é feita incrementalmente.
> O app já funciona e está em uso diário. Nada de "clean architecture deluxe" que mate a rádio no processo.

Ordem acordada em 2026-08-22. Cada fase só começa depois da anterior testada no aparelho.
Referências R1–R7 = races catalogadas em [STATE_MACHINE.md](STATE_MACHINE.md).

## P0 — Proteger o que pode crashar/vazar (antes de qualquer refactor)

> **Parcial (23/08/2026):** instaladas as proteções do ADR-009 — watchdog de anúncio
> (rede de proteção contra fala travada), `setWakeMode` e isenção de otimização de
> bateria. Os itens estruturais abaixo continuam pendentes; o watchdog será
> substituído pelo controller no P1.
>
> **25/08/2026:** player parou sozinho de novo, mas fora do Modo Rádio também — não é
> só R1-R8. Log de diagnóstico instalado (ver ADR-009 em DECISIONS.md); próximo passo
> é pegar o `adb logcat | grep PailerPlaybackDiag` de uma ocorrência real antes de
> decidir a correção.

- ~~**Serializar requests** ao `RadioVoiceSynthesisService`~~ — moot, serviço removido
  16/09/2026 junto da síntese de voz local (ver ADR-035 em DECISIONS.md)
- ~~**Cache LRU de engines TTS** no processo `:radio_voice`~~ — moot, mesma remoção
- [ ] **Limpeza de WAVs órfãos**: nome por token + varredura no início da sessão +
  delete ao receber resultado morto — mata R4 (ainda relevante pros `.wav` baixados do
  feed remoto, ver `saveCoreBufferManifest()`/`ORPHAN_CLEANUP_GRACE_MS`)
- [ ] **Buffer local não reavalia `expires_at` de boletim já baixado** (ADR-036,
  17/09/2026): `BroadcastFeedRepository` já pula item vencido **antes** de baixar, mas um
  boletim que já está em `coreBufferDir` esperando a vez não é checado de novo contra o
  próprio prazo — pode tocar mesmo depois de vencido se ficar tempo demais no buffer sem
  ser consumido. Precisa entender o ciclo de vida do buffer em
  `LocalTuneViewModel.kt` (`refillBulletinBuffer`/`saveCoreBufferManifest`/limpeza de
  órfãos) antes de mexer — não investigado ainda.
- ~~**Desacoplar caminho sherpa do `ttsReady` legado**~~ — moot, `ttsReady`/TTS Android
  removidos 16/09/2026
- [ ] **Fallback de repetição do boletim (ADR-041, 18/09/2026) não gira o pool de forma
  justa**: quando o pool do feed é menor que o histórico anti-repetição de 80, o
  fallback em `BroadcastFeedRepository.downloadNextApprovedBulletinBlocking()` pega o
  primeiro item do manifest que não está no `bulletinBuffer` agora (ordem do próprio
  feed), não o menos tocado recentemente dentro do histórico — pode favorecer sempre o
  mesmo item em vez de girar o pool inteiro. Não é urgente (o bug crítico corrigido era
  "nunca mais toca boletim nenhum"), mas seria melhor ordenar os candidatos do fallback
  pela posição em `recentBulletinStoryKeys` (mais antigo primeiro).

## P1 — Pipeline determinístico do boletim

- [ ] Implementar `RadioBulletinController` conforme spec da Seção B de
  [STATE_MACHINE.md](STATE_MACHINE.md): FSM Idle/Generating/Speaking + generationId
- [ ] Corrigir R1: resume condicional (nunca anular pausa manual)
- [ ] Corrigir R2: widget/notificação durante `Speaking` deve matar/inutilizar a locução,
  nunca tocar música por cima
- [ ] Corrigir R3: skip cancela boletim pendente; decidir se transição manual conta no
  intervalo (hoje não conta)

## P2 — Testes das regras puras

- [x] Setup de teste unitário (JUnit) — feito junto da feature de letras (ADR-023):
      `junit:junit:4.13.2` em `testImplementation`, primeiro teste em
      `app/src/test/.../LrcParserTest.kt`. `coroutines-test` ainda não foi adicionado.
- [ ] `fitFor()` / `limitWords()` (limites de palavras por duração)
- [ ] Anti-repetição de sessão (`radioSequenceSimilarity`, seeds)
- [ ] Normalização/filtros de busca e lookup keys de artista/álbum
- [ ] Regras da FSM (tabela estado × evento) assim que o controller existir

## P3 — Desmontar monólitos (incremental, uma tela/feature por vez)

- [ ] Split `LocalTuneViewModel.kt` (~3.7k linhas): LibraryViewModel, RadioViewModel,
  MetadataViewModel + coordenador de estado global
- [ ] Split `LocalTuneApp.kt` (~8.9k linhas): um arquivo por tela/feature (Home, Artists,
  Albums, Radio, Settings panels, Player, Widgets preview)

## P4 — Unificar pipeline de áudio

- [ ] Centralizar reprodução no Media3/ExoPlayer; avaliar substituir o `MediaPlayer`
  dos anúncios por player dedicado na mesma sessão ou AudioAttributes próprios
- [ ] Definir política única de audio focus para música vs. locução

## P5 — Features (só depois do pipeline determinístico)

- ~~Dois locutores + diálogos de notícia como feature estável~~ — feito via central de
  broadcast externa (redação humana+IA fora do app), não pelo redator local (removido
  16/09/2026, ver ADR-035)
- ~~Ligar o redator local de verdade (`OptionalLocalLlmRadioScriptWriter`)~~ — moot,
  redator local removido inteiro 16/09/2026
- ~~Boletim "especial" fura fila no buffer~~ — feito 17/09/2026 (ADR-037):
  `BroadcastFeedRepository` baixa `content_type == "especial"` com prioridade,
  `LocalTuneViewModel` insere no início do buffer (`addFirst`) em vez do fim.
- [ ] Atualizar este doc conforme itens fecham

## Backlog sem prioridade

- CI simples (build debug a cada push) quando houver repo remoto
- ~~Versionar projeto canônico em git~~ — feito em 26/08/2026 (repo local em
  `Pailer FM\Pailer FM`, primeiro commit `4020d07`). Snapshots antigos
  `Pailer-Player-source-package-*` movidos para `_archive/backups-manuais/`
  na raiz do workspace; sem repo remoto ainda, então continuam como backup extra.
