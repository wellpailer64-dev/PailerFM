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

- [ ] **Serializar requests** ao `RadioVoiceSynthesisService` (fila única/canal) — mata R5
  e é pré-condição para cache de engine
- [ ] **Cache LRU de engines TTS** no processo `:radio_voice` (matar ADR-003 com segurança):
  manter 1–2 engines, `release()` ao despejar
- [ ] **Limpeza de WAVs órfãos**: nome por token + varredura no início da sessão +
  delete ao receber resultado morto — mata R4
- [ ] **Desacoplar caminho sherpa do `ttsReady` legado** — mata R6/R7

## P1 — Pipeline determinístico do boletim

- [ ] Implementar `RadioBulletinController` conforme spec da Seção B de
  [STATE_MACHINE.md](STATE_MACHINE.md): FSM Idle/Generating/Speaking + generationId
- [ ] Corrigir R1: resume condicional (nunca anular pausa manual)
- [ ] Corrigir R2: widget/notificação durante `Speaking` deve matar/inutilizar a locução,
  nunca tocar música por cima
- [ ] Corrigir R3: skip cancela boletim pendente; decidir se transição manual conta no
  intervalo (hoje não conta)

## P2 — Testes das regras puras

- [ ] Setup de teste unitário (JUnit + coroutines-test; sem instrumentado no começo)
- [ ] `fitFor()` / `limitWords()` (limites de palavras por duração)
- [ ] Anti-repetição de sessão (`radioSequenceSimilarity`, seeds)
- [ ] Normalização/filtros de busca e lookup keys de artista/álbum
- [ ] Regras da FSM (tabela estado × evento) assim que o controller existir

## P3 — Desmontar monólitos (incremental, uma tela/feature por vez)

- [ ] Split `LocalTuneViewModel.kt` (~1.5k linhas): LibraryViewModel, RadioViewModel,
  MetadataViewModel + coordenador de estado global
- [ ] Split `LocalTuneApp.kt` (~3.4k linhas): um arquivo por tela/feature (Home, Artists,
  Albums, Radio, Settings panels, Player, Widgets preview)

## P4 — Unificar pipeline de áudio

- [ ] Centralizar reprodução no Media3/ExoPlayer; avaliar substituir o `MediaPlayer`
  dos anúncios por player dedicado na mesma sessão ou AudioAttributes próprios
- [ ] Definir política única de audio focus para música vs. locução

## P5 — Features (só depois do pipeline determinístico)

- [ ] Dois locutores + diálogos de notícia como feature estável (hoje depende das
      correções acima para não virar fonte de race conditions)
- [ ] Ligar o redator local de verdade (`OptionalLocalLlmRadioScriptWriter`):
      prompt controlado, JSON curto, timeout agressivo (ver ADR-002)
- [ ] Atualizar este doc conforme itens fecham

## Backlog sem prioridade

- CI simples (build debug a cada push) quando houver repo remoto
- Versionar projeto canônico em git (raiz `Pailer FM\Pailer FM`) — snapshots
  `Pailer-Player-source-package-*` continuam como backup manual
