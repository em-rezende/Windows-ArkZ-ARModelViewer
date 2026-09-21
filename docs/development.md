# Desenvolvimento

## Requisitos

| Item | Versão | Observação |
|---|---|---|
| JDK | **25** (toolchain) | O Filament no desktop usa FFM (Project Panama), que exige **Java 22+**. O build declara `jvmToolchain(25)` e emite bytecode 22. O Gradle baixa o JDK sozinho (`foojay-resolver-convention`) e o daemon pede a versão em `gradle/gradle-daemon-jvm.properties`. |
| Gradle | 9.7.1 | Já vem pelo wrapper (`gradlew.bat`). |
| WiX Toolset | 3.14+ (ou WiX 4/5) | Só para gerar o `.msi` (`gradlew.bat packageMsi`). O `assemble`/`run`/`test` não precisam dele. |
| Webcam | integrada ou USB | Necessária apenas para o app em execução (etapas 2+). |

> **Bibliotecas nativas:** o Filament, o OpenCV, o OpenBLAS e o LWJGL são baixados
> como **classificadores de plataforma** (`windows-x86_64` / `natives-windows`) do
> Maven Central — não há nada a instalar à mão. Ponto de atenção conhecido: o
> `opencv_core` do Bytedeco **exige o OpenBLAS** (sem o nativo dele, o OpenCV não
> inicializa e a falha aparece como `NoClassDefFoundError` em `openblas_nolapack`),
> e as versões precisam compartilhar o mesmo sufixo do JavaCPP
> (`4.14.0-1.5.14` ↔ `0.3.34-1.5.14`).

> **O que o Windows precisa ter:** nada para o desenvolvimento, mas **duas** armadilhas do
> empacotamento — as duas encontradas em campo, no notebook do cliente, e as duas invisíveis no
> `gradlew.bat run` (que roda num JDK completo). Veja a decisão 30 do roadmap.
>
> 1. **Módulo `jdk.unsupported`.** O LWJGL exige `sun.misc.Unsafe`, que vive nesse módulo, e o
>    jpackage **não o inclui sozinho** no runtime que monta: o Assimp não inicializa e o sintoma é
>    `Could not initialize class org.lwjgl.assimp.Assimp`. Declarado em
>    `nativeDistributions.modules("jdk.unsupported")`.
> 2. **Runtime do Visual C++.** A `assimp.dll` do LWJGL (e a `draco.dll`) importa `msvcp140.dll`,
>    `vcruntime140.dll` e `vcruntime140_1.dll`; sem o "Visual C++ Redistributable 2015-2022"
>    instalado, ela não carrega. O `createDistributable` copia as três de `runtime/bin` (o próprio
>    JBR as traz) para junto do `.exe`.
>
> Por isso a conferência do **pacote** é um passo próprio, além dos testes: `gradlew.bat modelCheck`
> (desenvolvimento) ou `"ArkZ ARModelViewer.exe" --check-model "arquivo"` (no pacote montado).

## Comandos

```powershell
# Compilar
.\gradlew.bat compileKotlin

# Testes (JUnit 5)
.\gradlew.bat test

# Executar (abre a janela)
.\gradlew.bat run

# Listar as webcams (índices do OpenCV) — abre cada dispositivo
.\gradlew.bat -q listCameras

# Diagnóstico do carregamento de modelo (Assimp) — converte de verdade e relata
.\gradlew.bat modelCheck -Pmodelo="3d_models/ArkZ_logo.obj"

# Instalador .msi (precisa do WiX)
.\gradlew.bat packageMsi
```

> `--enable-native-access=ALL-UNNAMED` já está configurado em `compose.desktop.application.jvmArgs`
> e vale também para `run`: sem essa opção o JVM recusa (ou avisa) o acesso nativo
> usado pelo FFM. A tarefa `test` recebe a **mesma** opção, porque alguns testes
> exercitam código nativo de verdade (o `FilamentSpikeTest` cria o motor gráfico do
> Filament e desenha um quadro; os testes do Assimp e do OpenCV abrem os arquivos e
> modelos do repositório).

## Testes que não executam (armadilha do JUnit 5)

Um teste de JUnit 5 **não pode devolver valor**. Um teste escrito como
`fun \`…\`() = runBlocking { … }` cujo último comando é uma função que devolve algo
(`assertNotNull`, `assertFailsWith`, `map`, …) tem **esse** tipo como retorno — e o JUnit
**ignora o método**: não executa, não falha e não avisa. A cobertura some em silêncio.

A conferência é comparar quantos `@Test` o arquivo declara com quantos o relatório diz que
rodaram:

```powershell
(Select-String -Path src\test\kotlin\...\AlgumTest.kt -Pattern '@Test').Count
[xml]$x = Get-Content build\test-results\test\TEST-com.arkz.armodelviewer...AlgumTest.xml
$x.testsuite.tests
```

Os dois números têm de bater. Foi assim que um teste do `SceneComposer` (que terminava com
`assertNotNull`) voltou a executar. A correção, no código, é guardar o valor numa variável
e terminar com uma afirmação que devolve `Unit`.

## Clone de referência do projeto Android

O script de idiomas lê os textos do projeto Android. Ele espera um clone em
`%TEMP%\ArkZ-Android-Ref` (caminho configurável com `-SourceRoot`):

```powershell
git clone --depth 1 https://github.com/em-rezende/Android-ArkZ-ARModelViewer.git "$env:TEMP\ArkZ-Android-Ref"
```

## Scripts de apoio (`tools/`)

| Script | Para que serve |
|---|---|
| `set-utf8-bom.ps1` | Grava BOM UTF-8 nos `.ps1` — **rode depois de editar qualquer script** (o PowerShell 5.1 lê sem BOM como ANSI: os acentos viram mojibake e, se um byte cair numa faixa que o CP1252 mapeia como aspas tipográficas, o script **não compila**). |
| `convert-strings.ps1` | Converte os `strings.xml` do Android nos bundles `.properties`. |
| `apply-i18n-overrides.ps1` | Aplica as divergências de texto do Windows (`tools/i18n-overrides/`). |
| `list-cameras.ps1` | Lista as webcams: nomes pelo Windows (PnP) e índices pelo OpenCV (chama a tarefa `listCameras`). |
| `calibrate-camera.ps1` e `generate-assets.ps1` | **Não existem** — e não precisaram existir: os intrínsecos da câmera não são calibrados à mão (saem do quadro real da detecção, veja `docs/marker-detection.md`), e os ícones (`ui/icons/`) e as folhas de impressão dos marcadores (`MarkerPrintSheet`) foram escritos direto no código. |

### Tarefas do Gradle além do build

| Tarefa | Para que serve |
|---|---|
| `listCameras` | Roda o `CameraDevices.enumerate()` em linha de comando e imprime índice + resolução de cada câmera. Aceita `-Darkz.camera.maxIndex=N`. |
| `run` | Abre a janela do aplicativo. |
| `modelCheck` | Diagnóstico do carregamento de modelo (Assimp): use `-Pmodelo="caminho"`. Grava `logs/diagnostico-modelo.txt`. |
| `createDistributable` | Monta a **imagem portátil** (executável + runtime + nativos) em `build/compose/binaries/main/app/` e copia as DLLs do Visual C++ ao lado do `.exe`. |
| `packageMsi` | Gera o instalador (exige WiX). |

## Publicar no GitHub

O repositório é <https://github.com/em-rezende/Windows-ArkZ-ARModelViewer> e a primeira
versão publicada é a **1.0.0**. O que o repositório carrega, além do código:

| Arquivo | Para que serve |
|---|---|
| `LICENSE` · `NOTICE` | A GPL-3.0 e as licenças de terceiros (o GitHub identifica a licença pelo texto do `LICENSE`). |
| `CHANGELOG.md` | As notas de release (Keep a Changelog + Versionamento Semântico), que também alimentam a *release* do GitHub. |
| `SECURITY.md` | Como relatar uma falha sem expô-la em *issue* pública. |
| `CONTRIBUTING.md` | Ambiente, convenções de código e a armadilha dos testes de JUnit 5. |
| `.gitattributes` | Fim de linha: LF no repositório, CRLF nos `.bat`/`.ps1` e os binários (modelos, ícones, `.exe`) intocados. Sem ele, um `.stl`/`.ply` binário poderia ser corrompido pela conversão. |

```powershell
# 1. Repositório local
git init -b main
git add -A
git commit -m "ArkZ ARModelViewer Desktop 1.0.0: primeira versão pública"

# 2. Enviar para o GitHub (o remoto precisa existir: gh repo create, ou pela interface)
git remote add origin https://github.com/em-rezende/Windows-ArkZ-ARModelViewer.git
git push -u origin main

# 3. Release com as notas do CHANGELOG
git tag -a v1.0.0 -m "ArkZ ARModelViewer Desktop 1.0.0"
git push origin v1.0.0
gh release create v1.0.0 --title "1.0.0" --notes-file CHANGELOG.md
```

> O pacote portátil **não** entra no repositório (o `.gitignore` exclui `build/`, `dist/`,
> `*.zip` e `*.exe`). Para anexá-lo a uma release:
>
> ```powershell
> Compress-Archive -Path "build\compose\binaries\main\app\ArkZ ARModelViewer" `
>                  -DestinationPath "dist\ArkZ-ARModelViewer-1.0.0-portatil.zip"
> gh release upload v1.0.0 "dist\ArkZ-ARModelViewer-1.0.0-portatil.zip"
> ```
>
> São cerca de **250 MB** compactados: vale mais como conveniência (o usuário final não
> instala JDK) do que como obrigação da release.

## Onde o app grava arquivos

| O quê | Onde |
|---|---|
| Capturas de tela | `%USERPROFILE%\Pictures\ArkZ ARModelViewer\` |
| Imagens de marcadores (impressão) | `%USERPROFILE%\Pictures\ArkZ ARModelViewer\Marcadores\` |
| Preferências (idioma, qualidade, ajuda vista) | `%LOCALAPPDATA%\ArkZ ARModelViewer\preferences.properties` |
| Marcadores personalizados | `%LOCALAPPDATA%\ArkZ ARModelViewer\custom_markers\` |
| Cópias dos modelos carregados | `%LOCALAPPDATA%\ArkZ ARModelViewer\opened_models\` |
| Log do aplicativo | `%LOCALAPPDATA%\ArkZ ARModelViewer\logs\app.log` |

Os mesmos conceitos do app Android (galeria, `SharedPreferences`, `filesDir`), com
os equivalentes do Windows — a convenção está descrita em `docs/architecture.md`.
