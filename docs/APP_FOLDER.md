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

Ao escolher a pasta, o app cria automaticamente 4 subpastas dentro dela
(`AppFolderRepository.ensureSubfolders`):

```
<pasta escolhida>/
├── Backup/               ← gerido pelo app, sempre atualizado
│   └── pailer_fm_backup.json
├── Logs/                 ← gerido pelo app, so recebe arquivo em caso de crash
│   └── crash_AAAA-MM-DD_HH-mm-ss.txt
├── Redator Local/        ← COPIA DE REFERENCIA, nao e lida pelo app em uso normal
│   └── <ultimo .zip importado pela UI>
└── Pacote de Vozes/      ← COPIA DE REFERENCIA, nao e lida pelo app em uso normal
    └── <ultimo .zip importado pela UI>
```

| Subpasta | O que tem | Quem escreve | O app lê de volta? |
|---|---|---|---|
| `Backup/` | `pailer_fm_backup.json` — favoritos, overrides de álbum/artista, fotos de artista (base64), histórico, config de boletim | `BackupRepository.performBackup()` — manual ("Fazer backup agora") ou automático (1x/dia perto da meia-noite, ver `BackupScheduler`) | Sim, via "Restaurar de um arquivo" (escolha manual do arquivo, não automática) |
| `Logs/` | Um `.txt` por crash, com stack trace completo + versão do app | `PailerApplication` (handler de `Thread.setDefaultUncaughtExceptionHandler`), só se a pasta oficial já estiver configurada | Não — só para o usuário/dev ler manualmente |
| `Redator Local/` | Cópia do `.zip` que o usuário importou pela última vez em "Importar redator" | `RadioWriterPackageRepository.importPackage()`, melhor esforço (nunca falha a importação por causa disso) | **Não** |
| `Pacote de Vozes/` | Cópia do `.zip` que o usuário importou pela última vez em "Importar pacote" | `RadioVoicePackageRepository.importPackage()`, melhor esforço | **Não** |

## Por que Redator Local/Pacote de Vozes são só cópia de referência

Os modelos de verdade (GGUF do redator via llama.cpp, ONNX da voz via sherpa-onnx) são
carregados por **código nativo** (JNI) que precisa de um caminho de arquivo de disco real
(`File.absolutePath`). Uma pasta escolhida via SAF/`ACTION_OPEN_DOCUMENT_TREE` é acessada
pelo app como um `content://` Uri administrado pelo `DocumentsContract` — não existe garantia
de que vire um caminho de arquivo de verdade (e depender disso quebraria em qualquer provider
que não seja armazenamento local puro, ex. Google Drive).

Por isso o app **continua** extraindo/rodando os pacotes em armazenamento privado interno:

```
filesDir/radio_writer/            ← pacote de redator ATIVO (ver TTS.md/RADIO_PIPELINE.md)
filesDir/radio_voice_package/     ← pacote de voz ATIVO
```

O `.zip` original que o usuário importou é copiado pra pasta oficial **só como referência/
organização** — reimportar o pacote sempre lê o `.zip` que o usuário escolher no seletor de
arquivos naquele momento (pode ser o da pasta oficial ou de qualquer outro lugar), nunca a
cópia de referência diretamente.

**Consequência prática:** colocar/trocar um arquivo manualmente dentro de `Redator Local/` ou
`Pacote de Vozes/` (ex.: pelo gerenciador de arquivos do Android) **não muda o que o app usa**.
Pra ativar um pacote diferente é sempre necessário reimportar pela UI (Configurações →
Boletins da rádio → "Importar redator"/"Importar pacote"), apontando pro arquivo desejado.

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
