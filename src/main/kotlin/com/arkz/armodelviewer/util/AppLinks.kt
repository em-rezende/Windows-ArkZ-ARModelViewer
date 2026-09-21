/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3, publicada pela Free Software
 * Foundation. Este programa é distribuído na esperança de que seja útil, mas SEM
 * NENHUMA GARANTIA; sem mesmo a garantia implícita de COMERCIABILIDADE ou
 * ADEQUAÇÃO A UM PROPÓSITO ESPECÍFICO. Veja o arquivo LICENSE na raiz do projeto.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 * Versão Windows do app Android:
 * https://github.com/em-rezende/Android-ArkZ-ARModelViewer
 */

package com.arkz.armodelviewer.util

import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Properties

/** Site do desenvolvedor (menu → "Sobre" e item de menu equivalente ao da Play Store). */
const val DEVELOPER_SITE_URL = "https://em-rezende.github.io/"

/** E-mail de contato do desenvolvedor. */
const val DEVELOPER_EMAIL = "emrezende@gmail.com"

/** Repositório DESTA versão (Windows). */
const val PROJECT_URL = "https://github.com/em-rezende/Windows-ArkZ-ARModelViewer"

/** Repositório da versão Android (origem do projeto). */
const val ANDROID_PROJECT_URL = "https://github.com/em-rezende/Android-ArkZ-ARModelViewer"

/**
 * Abre [url] no navegador padrão do Windows.
 *
 * É o equivalente desktop do `Context.openExternal` do app Android: lá era um
 * `Intent.ACTION_VIEW`; aqui é o `Desktop.Action.BROWSE`, que o Windows resolve
 * pelo navegador padrão do usuário.
 *
 * @return `null` quando conseguiu abrir; caso contrário, a mensagem para a
 *   barra de status (a interface não tem como mostrar uma exceção de AWT).
 */
fun openExternal(url: String): String? = runCatching {
    val desktop = desktopOrNull() ?: return "Este computador não tem ambiente gráfico para abrir o endereço."
    if (!desktop.isSupported(Desktop.Action.BROWSE)) {
        return "Este computador não tem um navegador padrão configurado."
    }
    desktop.browse(URI(url))
    null
}.getOrElse { error ->
    "Não foi possível abrir o endereço: ${error.message ?: error.javaClass.simpleName}"
}

/**
 * Abre o cliente de e-mail padrão com destinatário, assunto e corpo prontos.
 *
 * Usa `Desktop.Action.MAIL` com `mailto:` — a mesma ideia do `ACTION_SENDTO`
 * do Android: abre um aplicativo de e-mail, e não um mensageiro qualquer.
 */
fun sendEmail(to: String, subject: String, body: String): String? = runCatching {
    val desktop = desktopOrNull() ?: return "Este computador não tem ambiente gráfico para abrir o e-mail."
    if (!desktop.isSupported(Desktop.Action.MAIL)) {
        return "Nenhum aplicativo de e-mail configurado."
    }
    val query = "subject=${encodeQuery(subject)}&body=${encodeQuery(body)}"
    desktop.mail(URI("mailto:$to?$query"))
    null
}.getOrElse { error ->
    "Não foi possível abrir o e-mail: ${error.message ?: error.javaClass.simpleName}"
}

/** Copia [text] para a área de transferência. Devolve `false` se não conseguiu. */
fun copyToClipboard(text: String): Boolean = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    true
}.getOrDefault(false)

/**
 * Nome do app (`app_name`), lido de `version.properties` — gerado pelo Gradle.
 *
 * Uma única fonte de verdade: o mesmo valor do `packageName`/`versionName` do
 * build e do `rootProject.name` da janela.
 */
fun appName(): String = appProperties.getProperty("app.name") ?: "ArkZ ARModelViewer"

/**
 * Versão instalada do app.
 *
 * O Android lia do `PackageManager`; aqui o valor vem de `version.properties`,
 * gravado pelo Gradle a partir da versão declarada no `build.gradle.kts`.
 */
fun appVersionLabel(): String = appProperties.getProperty("app.version") ?: "desconhecida"

/** Descrição do computador, usada no feedback e no diagnóstico. */
fun deviceLabel(): String = buildString {
    append(System.getProperty("os.name"))
    append(' ')
    append(System.getProperty("os.version"))
    append(" (")
    append(System.getProperty("os.arch"))
    append(", Java ")
    append(System.getProperty("java.version"))
    append(')')
}

/** Navegador de arquivos/pastas — usado para "abrir a pasta da captura". */
fun openDirectory(path: String): String? = runCatching {
    val desktop = desktopOrNull() ?: return "Este computador não tem ambiente gráfico para abrir a pasta."
    if (!desktop.isSupported(Desktop.Action.OPEN)) return "Não foi possível abrir a pasta."
    desktop.open(java.io.File(path))
    null
}.getOrElse { error ->
    "Não foi possível abrir a pasta: ${error.message ?: error.javaClass.simpleName}"
}

/** `Desktop` só existe com ambiente gráfico; em testes/headless devolve `null`. */
private fun desktopOrNull(): Desktop? =
    if (GraphicsEnvironment.isHeadless()) null else Desktop.getDesktop()

/** Codifica um parâmetro de `mailto:` (`+` para espaços, como manda a RFC 6068). */
private fun encodeQuery(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

/**
 * O `ClassLoader` do app para ler os recursos gerados.
 *
 * Existe como objeto só porque `java.util.Properties::class.java.classLoader` é
 * o *bootstrap* (`null`) — as classes do próprio JDK não servem para achar
 * recursos do aplicativo.
 */
private object AppResources

/**
 * `version.properties` gerado pelo Gradle (veja `generateAppProperties` no
 * build.gradle.kts). Se o recurso não estiver no classpath — o que só acontece
 * num ambiente de teste mal montado — o app continua funcionando com os valores
 * padrão, em vez de estourar na primeira leitura.
 */
private val appProperties: Properties by lazy {
    Properties().apply {
        AppResources::class.java.classLoader
            .getResourceAsStream("version.properties")
            ?.use { stream -> load(stream) }
    }
}
