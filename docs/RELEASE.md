# Build, instalação e testes no aparelho

Como testamos o app no celular real durante o desenvolvimento — build release por
padrão, ferramentas portáteis (sem depender do Android Studio aberto), instalação via
ADB e como investigar problema quando algo não aparece na tela.

## Por que build release (e não debug)

Toda instalação de teste usa **release**, não debug, por pedido do usuário: builds
mais rápidas de instalar/testar e mais perto do app "de verdade".

A assinatura da build release é **de propósito a mesma chave de debug** (ver
`app/build.gradle.kts`, `buildTypes.release.signingConfig`), e não a chave de release
dedicada em `keystore.properties`/`../keystore/pailer-release.jks`. Isso é intencional:
com a mesma assinatura, `adb install -r` sempre atualiza por cima do app já instalado,
sem nunca precisar desinstalar — e desinstalar apaga dados locais (favoritos,
histórico, overrides de metadados, o pacote de vozes TTS importado). A chave de
release dedicada fica pronta e sem uso pra um dia publicar de verdade (Play Store ou
distribuição fora do debug); trocar pra ela em `buildTypes.release` é decisão consciente
— ver comentário no topo do `app/build.gradle.kts`.

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
