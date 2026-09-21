// Configuração de repositórios e do projeto.
//
// O Compose Multiplatform (UI) e o Filament KMP (renderizador Filament no
// desktop/JVM) estão publicados no Maven Central; o plugin do Compose também é
// publicado no portal de plugins do Gradle.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

// O Filament no desktop depende de FFM (Project Panama), que exige JDK 22+.
// O resolvedor da foojay permite ao Gradle BAIXAR o JDK do toolchain quando a
// máquina não tiver exatamente a versão pedida — veja `jvmToolchain` no
// build.gradle.kts. O README explica qual JDK é usado e por quê.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "ArkZ ARModelViewer Desktop"
