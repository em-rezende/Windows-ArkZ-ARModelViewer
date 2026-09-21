import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    // O plugin do compilador Compose é obrigatório a partir do Kotlin 2.0.
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

group = "com.arkz.armodelviewer"
val appVersion = "1.0.5"
version = appVersion

// Nome do pacote — fonte única, como a versão: o empacotamento precisa dele para saber onde a
// imagem portátil é escrita (veja o fim deste arquivo).
val appPackageName = "ArkZ ARModelViewer"

kotlin {
    // O Filament no desktop é acessado por FFM (Project Panama): Java 22+.
    // O toolchain garante a mesma versão na compilação e no runtime empacotado.
    jvmToolchain(libs.versions.jdk.get().toInt())

    compilerOptions {
        // Bytecode alvo: 22 é o primeiro que expõe a API de FFM usada pelo
        // filament-kmp (o compilador roda no JDK do toolchain, 25).
        jvmTarget.set(JvmTarget.valueOf("JVM_${libs.versions.jvmTarget.get()}"))
    }
}

// O projeto não tem fontes Java, mas o Gradle valida que o alvo do `compileJava`
// e o do Kotlin sejam compatíveis — com o toolchain 25 e o bytecode 22 eles
// divergiriam. Alinhar aqui é a solução recomendada (sem baixar o toolchain só
// para igualar o número).
java {
    sourceCompatibility = JavaVersion.VERSION_22
    targetCompatibility = JavaVersion.VERSION_22
}

dependencies {
    // ---------- UI (Compose for Desktop) ----------
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)

    // ---------- Renderização 3D (Filament) ----------
    // `filament`  = Engine/Scene/View/Camera/Texture/TransformManager (API próxima
    //               da do Filament Android).
    // `gltfio`    = leitura de .glb/.gltf (AssetLoader/ResourceLoader).
    // `filament-utils` = utilitários (manipulador de câmera, KTX, etc.).
    // `filament-compose` = ponte com o Compose (superfície + readback para Skia).
    implementation(libs.filament)
    implementation(libs.filament.gltfio)
    implementation(libs.filament.utils)
    implementation(libs.filament.compose)
    // Materiais compilados em tempo de execução (o plano de fundo/quadro da câmera).
    implementation(libs.filament.filamat)

    // ---------- Conversão de formatos que o Filament não lê ----------
    implementation(libs.lwjgl)
    implementation(libs.lwjgl.assimp)
    runtimeOnly(variantOf(libs.lwjgl) { classifier("natives-windows") })
    runtimeOnly(variantOf(libs.lwjgl.assimp) { classifier("natives-windows") })

    // ---------- Câmera + visão computacional ----------
    // Só o módulo OpenCV: a captura de vídeo e a detecção de marcador saem daqui
    // (não usamos o restante do JavaCV, que arrastaria FFmpeg para o instalador).
    // O OpenBLAS é obrigatório: o `opencv_core` do Bytedeco herda dele e o nativo
    // dele é o que faz o OpenCV inicializar.
    implementation(libs.javacpp)
    implementation(libs.opencv)
    implementation(libs.openblas)
    runtimeOnly(variantOf(libs.javacpp) { classifier("windows-x86_64") })
    runtimeOnly(variantOf(libs.opencv) { classifier("windows-x86_64") })
    runtimeOnly(variantOf(libs.openblas) { classifier("windows-x86_64") })

    // ---------- Testes ----------
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Os testes usam o mesmo `AppLog` do aplicativo; esta marca diz ao log para não
    // gravar no arquivo do usuário (o `stderr` continua saindo, para o teste que
    // falhar ficar visível na saída do Gradle).
    systemProperty("arkz.test", "true")
    // O Filament no desktop é acessado por FFM (Project Panama): sem esta opção o JVM
    // recusa o acesso nativo que as bibliotecas usam.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    testLogging {
        events("passed", "skipped", "failed")
        // Mostra a saída dos testes: os diagnósticos das bibliotecas NATIVAS (Filament,
        // OpenCV, Assimp) saem por aí, e é por onde se descobre o motivo de uma asserção
        // nativa ou de um modelo que não aparece.
        showStandardStreams = true
    }
}

// ---------------------------------------------------------------------------
// Ferramentas de linha de comando
// ---------------------------------------------------------------------------

/**
 * Lista as webcams que o OpenCV consegue abrir (índices usados pelo app).
 *
 * O `tools/list-cameras.ps1` chama esta tarefa e, em paralelo, consulta o Windows
 * para mostrar os nomes amigáveis dos dispositivos.
 */
tasks.register<JavaExec>("listCameras") {
    group = "tools"
    description = "Lista as webcams disponíveis (índices do OpenCV)."
    mainClass.set("com.arkz.armodelviewer.tools.CameraListKt")
    classpath = sourceSets.main.get().runtimeClasspath
    // `jvmArgs(...)` (método) e não `jvmArgs += ...`: a propriedade é anulável e o
    // operador de atribuição fica ambíguo no Kotlin DSL do Gradle.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

// ---------------------------------------------------------------------------
// Versão do app como recurso: uma única fonte de verdade (`appVersion`, acima),
// lida em runtime por `util.AppLinks.appVersionLabel()` — o equivalente desktop
// do que o Android obtém do PackageManager.
// ---------------------------------------------------------------------------
val generatedResources = layout.buildDirectory.dir("generated/app-resources")
val generateAppProperties = tasks.register("generateAppProperties") {
    val outputDir = generatedResources
    val name = rootProject.name
    val version = appVersion
    inputs.property("appName", name)
    inputs.property("appVersion", version)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().asFile.resolve("version.properties")
        file.parentFile.mkdirs()
        file.writeText(
            """
            # Gerado pelo Gradle (build.gradle.kts). Não edite à mão.
            app.name=$name
            app.version=$version
            """.trimIndent() + "\n",
        )
    }
}

sourceSets.main {
    resources.srcDir(generatedResources)
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(generateAppProperties)
}

// ---------------------------------------------------------------------------
// Empacotamento (.msi via jpackage). Requer o WiX Toolset instalado.
// ---------------------------------------------------------------------------
compose.desktop {
    application {
        mainClass = "com.arkz.armodelviewer.MainKt"
        // FFM (Project Panama) exige liberar o acesso nativo das classes do
        // classpath (sem isso o JVM avisa — e, nas versões mais novas, recusa).
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        // Empacota o JRE do toolchain (22+) para o FFM funcionar na máquina do
        // usuário, mesmo que ela não tenha nenhum Java instalado.
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt()))
        }.get().metadata.installationPath.asFile.absolutePath

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = appPackageName
            packageVersion = appVersion

            // `sun.misc.Unsafe` (módulo `jdk.unsupported`) é exigido pelo LWJGL: sem ele o Assimp
            // nem inicializa. O defeito só aparece **no pacote** — `gradlew run` usa um JDK
            // completo, então o desenvolvimento nunca o vê. Foi a causa do
            // `Could not initialize class org.lwjgl.assimp.Assimp` no notebook (decisão 30):
            // o runtime montado pelo jpackage não traz o módulo.
            modules("jdk.unsupported")

            description = "Visualizador de modelos 3D em Realidade Aumentada para Windows"
            vendor = "Ark-Z Arquitetura Ltda"
            copyright = "© 2026 Ark-Z Arquitetura Ltda · Desenvolvedor: Ezequiel M. Rezende"
            licenseFile.set(layout.projectDirectory.file("LICENSE").asFile)
            windows {
                menu = true
                shortcut = true
                dirChooser = true
                menuGroup = "ArkZ ARModelViewer"
                // O ícone do PROGRAMA (o do `.exe`, e portanto o da barra de tarefas e o do
                // atalho do menu Iniciar). Sem esta linha, o empacotador usa o ícone padrão do
                // Java — o ícone da janela (`Main.kt`) é outro caminho, que só vale em runtime.
                iconFile.set(layout.projectDirectory.file("src/main/resources/icons/Ark-Z_Logo.ico").asFile)
                // Identificador fixo de versão: permite ao Windows ATUALIZAR uma
                // instalação anterior em vez de criar uma segunda entrada.

// ---------------------------------------------------------------------------
// Runtime do Visual C++ ao lado do executável.
//
// O LWJGL extrai a sua `assimp.dll` para uma pasta temporária própria — **só ela**: o jar nativo
// não traz o runtime do MSVC. O carregador do Windows procura as dependências, em ordem: a pasta
// da própria DLL, a pasta do aplicativo, o `System32`, a pasta atual e o `PATH`. Numa máquina sem
// o "Visual C++ Redistributable 2015-2022" nenhuma delas tem `msvcp140.dll`, e o resultado em
// campo foi `Could not initialize class org.lwjgl.assimp.Assimp` — o modelo simplesmente não
// carregava (a investigação está na decisão 30 do roadmap).
//
// As três DLLs já vêm no JRE que o jpackage empacota (o próprio JBR as usa, em `runtime/bin`);
// basta copiá-las para a **raiz do pacote**, que é a pasta do `.exe` — o segundo lugar onde o
// Windows procura. Assim o pacote portátil cumpre o que o README promete: roda sem instalar nada.
//
// Ressalva registrada para a etapa 8: o `.msi` monta a própria imagem dentro do jpackage, então
// esta cópia não chega nele — lá o caminho é `appResourcesRootDir` mais `AddDllDirectory` na
// abertura do app (o `resources` do pacote já é apontado por `compose.application.resources.dir`).
// ---------------------------------------------------------------------------
val vcRuntimeDlls = listOf("msvcp140.dll", "vcruntime140.dll", "vcruntime140_1.dll")

val appImageDirectory = layout.buildDirectory.dir("compose/binaries/main/app/$appPackageName")

tasks.matching { it.name == "createDistributable" }.configureEach {
    doLast {
        val imageDir = appImageDirectory.get().asFile
        if (!imageDir.isDirectory) {
            logger.warn("Imagem portátil não encontrada em $imageDir: runtime do Visual C++ não copiado.")
            return@doLast
        }

        val ausentes = mutableListOf<String>()
        vcRuntimeDlls.forEach { name ->
            val origem = File(imageDir, "runtime/bin/$name")
            if (origem.isFile) {
                origem.copyTo(File(imageDir, name), overwrite = true)
            } else {
                ausentes += name
            }
        }

        val copiadas = vcRuntimeDlls - ausentes.toSet()
        if (copiadas.isNotEmpty()) {
            logger.lifecycle("Runtime do Visual C++ ao lado do executável: ${copiadas.joinToString(", ")}")
        }
        if (ausentes.isNotEmpty()) {
            logger.warn(
                "Não encontrei $ausentes em runtime/bin: o pacote vai depender do Visual C++ " +
                    "instalado na máquina de destino.",
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Diagnóstico do carregamento de modelo (Assimp), pela linha de comando.
// Use `-Pmodelo="caminho do arquivo"`; sem a propriedade, testa um modelo do repositório.
// ---------------------------------------------------------------------------
tasks.register<JavaExec>("modelCheck") {
    group = "tools"
    description = "Diagnóstico do carregamento de modelo (Assimp): use -Pmodelo=\"caminho\"."
    mainClass.set("com.arkz.armodelviewer.tools.ModelDiagnosticsKt")
    classpath = sourceSets.main.get().runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // `getOrElse` resolve a propriedade **aqui**: passar o `Provider` direto faria a linha de
    // comando receber o `toString()` dele ("or(provider(?), fixed(...))") — foi o que aconteceu na
    // primeira execução, e o relatório do diagnóstico registrou o caminho esquisito.
    args("--check-model", providers.gradleProperty("modelo").getOrElse("3d_models/ArkZ_logo.obj"))
}

                upgradeUuid = "5b1f0c62-9a3d-4e77-8f21-6d4c0a7e93b8"
            }
        }
    }
}
