# Build, instalação e testes no aparelho

Como testamos o app no celular real durante o desenvolvimento — build release por
padrão, ferramentas portáteis (sem depender do Android Studio aberto), instalação via
ADB e como investigar problema quando algo não aparece na tela.

## Por que build release (e não debug)

Toda instalação de teste usa **release**, não debug, por pedido do usuário: builds
mais rápidas de instalar/testar e mais perto do app "de verdade".

A assinatura da build release depende de existir um `keystore.properties`
(`app/build.gradle.kts`, `buildTypes.release.signingConfig =
signingConfigs.findByName("release") ?: getByName("debug")`):

- **Com `keystore.properties`** (o `pailer-release.jks` dedicado): assina com a chave de
  release. É o caso do **CI** — o workflow `.github/workflows/build-apk.yml` materializa
  `keystore.properties` a partir de 4 secrets (`RELEASE_KEYSTORE_BASE64`,
  `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) e apaga o
  arquivo + o `.jks` no fim do job — e da máquina do dev que guarda o `.jks`. O APK do
  GitHub Releases é esse.
- **Sem `keystore.properties`** (clone novo, sem o `.jks`): cai na chave de debug local
  automática, como sempre foi (ADR-012). Build funciona offline, sem precisar do arquivo.

`adb install -r` só atualiza por cima **sem desinstalar** quando a assinatura bate. APKs
da mesma família de chave (todos do CI, ou todos de debug da mesma máquina) instalam uns
por cima dos outros; trocar de família num aparelho que já tem o app exige **um**
desinstall (apaga favoritos, histórico, overrides, pacotes TTS, letras, pasta oficial) —
fazer backup pela UI antes (Configurações → Backup). Ver ADR-012.

### Configurar os secrets de assinatura (uma vez)

```
base64 -w0 pailer-release.jks > keystore.b64
gh secret set RELEASE_KEYSTORE_BASE64   < keystore.b64
gh secret set RELEASE_KEYSTORE_PASSWORD          # cola a senha do keystore
gh secret set RELEASE_KEY_ALIAS                  # cola o alias da chave
gh secret set RELEASE_KEY_PASSWORD               # cola a senha da chave
rm keystore.b64
```

`isMinifyEnabled = false` também é de propósito: R8/shrink pode quebrar reflection
(jaudiotagger, MediaStore) e o JNI do sherpa-onnx de formas difíceis de depurar, e o
ganho não compensa pra um app pessoal não publicado.

## Ferramentas portáteis (não depende do Android Studio)

JDK 17 e Gradle 8.7 ficam extraídos em `tools/` (workspace, fora do repo git):

```powershell
$env:JAVA_HOME = "<workspace>\tools\jdk17"
& "<workspace>\tools\gradle-8.7\bin\gradle" :app:assembleRelease --offline
```

`--offline` funciona no dia a dia porque as dependências já estão no cache do Gradle.
Se `lintVitalAnalyzeRelease` falhar pedindo uma dependência de lint não cacheada, rodar
uma vez **sem** `--offline` (com internet) resolve e cacheia pra sempre.

APK sai em `app/build/outputs/apk/release/app-release.apk`.

## ADB — instalar e testar

O SDK do Android (`platform-tools`) já tem `adb.exe`; o celular fica conectado por
depuração USB. Fluxo padrão:

```powershell
adb devices                                   # confirma o aparelho autorizado
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell am start -n com.pailer.localtune/.MainActivity
```

Pra investigar um problema:

```powershell
adb logcat -c                                 # limpa o buffer antes de reproduzir o passo
# ...reproduzir o passo no aparelho...
adb logcat -d -v time | Select-String "PailerRadioVoice|PailerPlaybackDiag|AndroidRuntime"
```

Screenshot do estado atual da tela (útil pra confirmar UI sem precisar descrever por
texto):

```powershell
adb shell screencap -p /sdcard/s.png
adb pull /sdcard/s.png caminho\local\s.png
```

Tags de log úteis já existentes no app: `PailerRadioVoice` (rádio, boletins, vinhetas —
ver `TAG_RADIO_VOICE` em `LocalTuneViewModel.kt`) e `PailerPlaybackDiag`
(estado do player, ver ADR-009 em [DECISIONS.md](DECISIONS.md)).

### Nota de ambiente (bash/Git Bash no Windows)

`adb.exe` é um binário Windows — chamado de dentro do Git Bash, ele não entende path
POSIX (`/sdcard/...` vira `C:/Program Files/Git/sdcard/...`). Prefixar o comando com
`MSYS_NO_PATHCONV=1` resolve pro lado do `shell` (`/sdcard/...` chega intacto ao
aparelho); `adb pull`/`adb push` com destino **local** ainda precisam de path estilo
Windows (`C:\Users\...`), não POSIX.

## Cópia de distribuição (dist/PailerFM.apk)

`dist/PailerFM.apk` é a cópia "pronta pra compartilhar" do app, fora do fluxo de teste
via ADB — mesmo binário de `app/build/outputs/apk/release/app-release.apk`, só copiado
pra um nome/lugar estável (o `build/` é apagado a cada `gradle clean` ou build limpa).
Fica fora do git (`.gitignore`, mesmo motivo dos zips grandes em `voice-models/`) —
binário grande, sem sentido versionar.

Não é atualizada automaticamente pelo build — depois de gerar um `app-release.apk` novo
que valeu a pena distribuir, copiar por cima:

```powershell
copy app\build\outputs\apk\release\app-release.apk dist\PailerFM.apk
```

## keystore.properties

Existe mas fica fora do git (`.gitignore`) e não é usado pela build release hoje (ver
seção acima). Guarda a referência pra chave de release dedicada:

```
storeFile=../keystore/pailer-release.jks
storePassword=...
keyAlias=pailer-player
keyPassword=...
```

`../keystore/` é relativo à raiz do projeto (`Pailer FM\Pailer FM`), então aponta pra
`Pailer FM\keystore\pailer-release.jks` — um nível acima do repo git, ao lado de
`tools/` e `voice-models/`, pra nunca acabar versionado por acidente.
