/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.util

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * As pastas do aplicativo no Windows — o equivalente desktop do que o app Android
 * obtém de `filesDir`, `getExternalFilesDir(Pictures)` e da galeria (`MediaStore`).
 *
 * | Android | Windows |
 * |---|---|
 * | `Pictures/ArkZ ARModelViewer` (galeria) | `%USERPROFILE%\Pictures\ArkZ ARModelViewer` |
 * | `Pictures/ArkZ ARModelViewer/Marcadores` | idem, subpasta `Marcadores` |
 * | `filesDir/custom_markers` | `%LOCALAPPDATA%\ArkZ ARModelViewer\custom_markers` |
 * | `filesDir/opened_models` | `%LOCALAPPDATA%\ArkZ ARModelViewer\opened_models` |
 * | `SharedPreferences` | `%LOCALAPPDATA%\ArkZ ARModelViewer\preferences.properties` |
 * | Logcat | `%LOCALAPPDATA%\ArkZ ARModelViewer\logs\app.log` |
 *
 * Os nomes das pastas em `Pictures` são **os mesmos do app Android**, em todos os
 * idiomas: são constante de código, não texto de interface — um usuário que usa os
 * dois apps encontra as imagens no mesmo lugar.
 *
 * O nome da subpasta de marcadores ("Marcadores") também é mantido em português de
 * propósito, para não gerar duas pastas diferentes conforme o idioma do Windows.
 */
object AppDirectories {

    /** `%LOCALAPPDATA%\ArkZ ARModelViewer` (dados do app: preferências, cópias, log). */
    val root: File get() = rootDirectory

    /** Preferências do app (`preferences.properties`). */
    val preferencesFile: File get() = File(root, "preferences.properties")

    /** Marcadores personalizados criados/carregados pelo usuário. */
    val customMarkers: File get() = File(root, "custom_markers")

    /**
     * Cópias dos modelos abertos pelo usuário.
     *
     * É uma pasta de DADOS (e não de cache): no Android o arquivo ficava em
     * `filesDir` porque o `cacheDir` podia ser apagado no meio de um carregamento.
     * No Windows o mesmo vale para `%LOCALAPPDATA%`, que não é limpo por
     * ferramentas de limpeza de disco.
     */
    val openedModels: File get() = File(root, "opened_models")

    /** Pasta do log do aplicativo. */
    val logs: File get() = File(root, "logs")

    /** Caminho do log (`app.log`), citado no texto de Ajuda. */
    val logFile: File get() = File(logs, "app.log")

    /** `%USERPROFILE%\Pictures\ArkZ ARModelViewer` — capturas de tela. */
    val pictures: File get() = picturesDirectory

    /** `…\Pictures\ArkZ ARModelViewer\Marcadores` — folhas e imagens para impressão. */
    val markerPictures: File get() = File(pictures, MARKER_FOLDER_NAME)

    /** Nome da pasta na biblioteca de imagens (o mesmo do app Android). */
    const val PICTURES_FOLDER_NAME = "ArkZ ARModelViewer"

    /** Subpasta de marcadores (o mesmo nome do app Android, em qualquer idioma). */
    const val MARKER_FOLDER_NAME = "Marcadores"

    /** Cria todas as pastas necessárias. Seguro de chamar mais de uma vez. */
    fun ensureAll() {
        listOf(root, customMarkers, openedModels, logs, pictures, markerPictures)
            .forEach { directory -> directory.mkdirs() }
    }

    private val rootDirectory: File by lazy {
        val base = System.getenv("LOCALAPPDATA")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: File(System.getProperty("user.home"))
        File(base, PICTURES_FOLDER_NAME).also { it.mkdirs() }
    }

    private val picturesDirectory: File by lazy {
        val home = File(System.getProperty("user.home"))
        File(pictureBase(environment = System.getenv(), home = home), PICTURES_FOLDER_NAME)
            .also { it.mkdirs() }
    }

    /**
     * A base da biblioteca de imagens do usuário.
     *
     * **A pergunta é feita ao Windows, e não adivinhada**: quem sabe onde "Imagens" está é o
     * próprio sistema, na chave `User Shell Folders` (`My Pictures`), que responde mesmo
     * quando a pasta foi movida para outro disco — como neste computador, em que ela está em
     * `D:\Imagens` e `%USERPROFILE%\Pictures` simplesmente não existe. A primeira versão
     * tentava `%OneDrive%\Pictures` como palpite, o que estava **errado** para este caso (a
     * pasta do OneDrive existia, mas não era a biblioteca de imagens).
     *
     * A ordem é:
     *  1. `My Pictures` do Windows (resolvido do registro, com as variáveis expandidas);
     *  2. `%USERPROFILE%\Pictures` (a instalação padrão, quando o registro não responde);
     *  3. `Personal` do Windows, ou seja, a pasta **Documentos** do usuário — o plano B
     *     pedido por quem usa o aplicativo: se não dá para gravar em "Imagens", é lá que as
     *     capturas devem ficar;
     *  4. `%USERPROFILE%\Documents`.
     *
     * Cada candidata só vale se **existir e aceitar escrita** — testada de verdade, criando e
     * apagando um arquivo dentro dela (o `canWrite()` do Java não enxerga recusa de permissão
     * do Windows). Sem isso, um caminho de rede fora do ar ou uma pasta somente leitura
     * seriam escolhidos e a captura falharia depois, com a mensagem passageira na tela.
     */
    internal fun pictureBase(
        environment: Map<String, String>,
        home: File,
        shellFolder: (String) -> String? = ::windowsShellFolder,
        canWriteInto: (File) -> Boolean = ::canWriteInto,
    ): File {
        val candidates = buildList {
            add(shellFolder(PICTURES_SHELL_FOLDER))
            add(home.resolve("Pictures").absolutePath)
            add(shellFolder(DOCUMENTS_SHELL_FOLDER))
            add(home.resolve("Documents").absolutePath)
        }

        return candidates
            .filterNotNull()
            .map { path -> File(expandEnvironment(path, environment)) }
            .firstOrNull { directory -> directory.isDirectory && canWriteInto(directory) }
            ?: home.resolve("Documents")
    }

    /** Nome do valor do registro com a pasta "Imagens" (igual em qualquer idioma). */
    internal const val PICTURES_SHELL_FOLDER = "My Pictures"

    /** Nome do valor do registro com a pasta "Documentos" (igual em qualquer idioma). */
    internal const val DOCUMENTS_SHELL_FOLDER = "Personal"

    /**
     * Lê um valor de `User Shell Folders` do registro, pelo `reg.exe` do Windows.
     *
     * É a fonte oficial e funciona com a pasta movida para outro disco. Qualquer falha
     * (regra de política que bloqueia o `reg`, saída inesperada, tempo esgotado) devolve
     * `null` e a escolha continua na lista — nunca derruba o aplicativo por causa disso.
     */
    private fun windowsShellFolder(name: String): String? = runCatching {
        val process = ProcessBuilder(
            "reg",
            "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders",
            "/v",
            name,
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(REGISTRY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroy()
            return null
        }
        if (process.exitValue() != 0) return null

        parseRegistryValue(output)
    }.getOrNull()

    /**
     * Extrai o caminho da saída do `reg query`.
     *
     * A linha é `    My Pictures    REG_EXPAND_SZ    D:\Imagens` — o nome e o tipo vêm antes
     * do caminho, que pode conter espaços, então o corte é feito **no tipo**, e não no
     * espaço.
     */
    internal fun parseRegistryValue(output: String): String? = output.lineSequence()
        .firstOrNull { it.contains("REG_") }
        ?.let { line ->
            line.substringAfter("REG_EXPAND_SZ ", missingDelimiterValue = "")
                .ifBlank { line.substringAfter("REG_SZ ", missingDelimiterValue = "") }
        }
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    /** Troca `%NOME%` pelo valor da variável de ambiente (o registro guarda assim). */
    internal fun expandEnvironment(path: String, environment: Map<String, String>): String {
        var expanded = path
        environment.forEach { (name, value) ->
            expanded = expanded.replace("%$name%", value, ignoreCase = true)
        }
        return expanded
    }

    /**
     * A pasta aceita escrita? A prova é criar e apagar um arquivo — perguntar ao
     * `canWrite()` do Java não revela recusa de permissão no Windows.
     */
    private fun canWriteInto(directory: File): Boolean = runCatching {
        val probe = File(directory, ".arkz-escrita")
        probe.writeText("")
        probe.delete()
    }.getOrDefault(false)

    /** Tempo máximo esperando o `reg.exe` responder. */
    private const val REGISTRY_TIMEOUT_MILLIS = 2_000L
}
