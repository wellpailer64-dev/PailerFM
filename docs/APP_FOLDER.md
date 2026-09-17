# Pasta oficial do Pailer FM

> **Regra de ouro deste projeto:** preservar o comportamento atual antes de refatorar.
> Toda mudança estrutural mantém as funcionalidades existentes e é feita incrementalmente.
> O app já funciona e está em uso diário. Nada de "clean architecture deluxe" que mate a rádio no processo.

Pedido do usuário 07/09/2026, depois de perder favoritos e fotos de artista num `adb uninstall`
usado pra debug (ver [DECISIONS.md](DECISIONS.md)): uma única pasta, escolhida uma vez pelo
usuário em qualquer lugar do armazenamento do aparelho (ex.: `/storage/emulated/0/Pailer FM`),
onde o app organiza tudo que faz sentido o usuário enxergar/gerenciar por fora — em vez de
arquivos soltos espalhados pelo `Download` ou pela raiz do armazenamento.

Configurada em **Configurações → Backup → "Escolher pasta oficial"**
(`AppFolderRepository`, `ActivityResultContracts.OpenDocumentTree`). A permissão é
**persistida** (`takePersistableUriPermission`) — o app não pede de novo depois de escolhida
uma vez, nem depois de reiniciar o aparelho.

## Estrutura

Ao escolher a pasta, o app cria automaticamente subpastas dentro dela
(`AppFolderRepository.ensureSubfolders`):

```
<pasta escolhida>/
├── Backup/               ← gerido pelo app, sempre atualizado
│   └── pailer_fm_backup.json
└── Logs/                 ← gerido pelo app, so recebe arquivo em caso de crash
    └── crash_AAAA-MM-DD_HH-mm-ss.txt
```

| Subpasta | O que tem | Quem escreve | O app lê de volta? |
|---|---|---|---|
| `Backup/` | `pailer_fm_backup.json` — favoritos, overrides de álbum/artista, fotos de artista (base64), histórico, config de boletim | `BackupRepository.performBackup()` — manual ("Fazer backup agora") ou automático (1x/dia perto da meia-noite, ver `BackupScheduler`) | Sim, via "Restaurar de um arquivo" (escolha manual do arquivo, não automática) |
| `Logs/` | Um `.txt` por crash, com stack trace completo + versão do app | `PailerApplication` (handler de `Thread.setDefaultUncaughtExceptionHandler`), só se a pasta oficial já estiver configurada | Não — só para o usuário/dev ler manualmente |

**16/09/2026:** as subpastas `Redator Local/` e `Pacote de Vozes/` (cópias de referência
dos pacotes de redator/voz local importados pela UI) foram removidas junto da remoção do
redator local, redator Gemini e síntese de voz local/Gemini/TTS Android — ver ADR-035 em
[DECISIONS.md](DECISIONS.md). Todo boletim vem pronto do feed remoto, não há mais pacote
nenhum pra importar.

## Backup: pasta oficial x arquivo avulso

`BackupRepository` suporta dois destinos, com prioridade fixa:

1. **Pasta oficial** (`AppFolderRepository.hasFolder() == true`) — sempre que configurada,
   o backup vai para `<pasta>/Backup/pailer_fm_backup.json`, ignorando qualquer arquivo
   avulso configurado antes.
2. **Arquivo avulso** (`ActivityResultContracts.CreateDocument`, caminho antigo) — só usado
   se nenhuma pasta oficial estiver configurada. Mantido por compatibilidade com quem
   configurou backup antes dessa pasta existir.

A UI (`BackupSettingsPanel`) esconde os controles de arquivo avulso ("Trocar arquivo de
backup"/"Desligar backup automático") assim que uma pasta oficial está ativa, pra não expor
dois destinos possíveis ao mesmo tempo.

## Relatório de crash

`PailerApplication.onCreate()` encadeia um `Thread.setDefaultUncaughtExceptionHandler`:
grava o stack trace em `Logs/crash_<timestamp>.txt` (se a pasta oficial existir) e **sempre**
repassa pro handler padrão anterior em seguida — isso só acrescenta o log, nunca muda o
comportamento normal de crash do Android (processo encerra, sistema mostra "app parou" etc.).

Sem pasta oficial configurada, o app se comporta exatamente como antes dessa funcionalidade
existir (sem custo, sem permissão extra pedida).
