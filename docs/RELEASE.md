# Build, instalação e testes no aparelho

Como testamos o app no celular real durante o desenvolvimento — build release por
padrão, ferramentas portáteis (sem depender do Android Studio aberto), instalação via
ADB e como investigar problema quando algo não aparece na tela.

## Por que build release (e não debug)

Toda instalação de teste usa **release**, não debug, por pedido do usuário: builds
mais rápidas de instalar/testar e mais perto do app "de verdade".

**Atualizado 17/09/2026 (ADR-032 supera o que este doc dizia antes):** a build release
NÃO assina mais com a chave de debug de propósito — `buildTypes.release.signingConfig`
usa a chave de release DEDICADA (`keystore.properties` local, `storeFile` apontando pra
`../keystore/pailer-release.jks`) sempre que esse arquivo existir na máquina (ou, no CI,
os secrets `RELEASE_KEYSTORE_*`). Nesta máquina (bancada de desenvolvimento local),
`keystore.properties` **existe** — confirmado 17/09/2026 ao instalar uma build sobre o
app já no aparelho. Só cai pra chave de debug automática (`~/.android/debug.keystore`,
efêmera por máquina) quando NENHUMA das duas fontes está disponível (ex.: clonando o
repo do zero sem o arquivo nem os secrets).

**Na prática, isso significa:** `gradle assembleDebug` produz um APK com uma assinatura
DIFERENTE da que já está instalada no aparelho de teste (que foi instalada como release,
com a chave dedicada) — `adb install -r` desse debug falha com
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` em vez de atualizar. Pra atualizar o app já
instalado sem perder dados locais (favoritos, histórico, overrides de metadados), usar
sempre `gradle assembleRelease` (não `assembleDebug`) enquanto `keystore.properties`
existir aqui. A chave de release dedicada era só "pronta e sem uso" quando este doc foi
escrito (ADR-012) — hoje ela é a chave de verdade em uso pras instalações de teste.

`isMinifyEnabled = false` também é de propósito: R8/shrink pode quebrar reflection
(jaudiotagger, MediaStore) de formas difíceis de depurar, e o ganho não compensa pra um
app pessoal não publicado. (JNI do sherpa-onnx removido 16/09/2026 junto da síntese de
voz local, ver ADR-035 em [DECISIONS.md](DECISIONS.md).)

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

Existe nesta máquina (fora do git, `.gitignore`) e **é usado pela build release hoje**
(ver seção "Por que build release" acima, atualizada 17/09/2026 — ADR-032 mudou isso).
Guarda a referência pra chave de release dedicada:

```
storeFile=../keystore/pailer-release.jks
storePassword=...
keyAlias=pailer-player
keyPassword=...
```

`../keystore/` é relativo à raiz do projeto (`Pailer FM\Pailer FM`), então aponta pra
`Pailer FM\keystore\pailer-release.jks` — um nível acima do repo git, ao lado de
`tools/` e `voice-models/`, pra nunca acabar versionado por acidente.
