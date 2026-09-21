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

import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.imageio.ImageIO

/**
 * Log do aplicativo, em arquivo — o equivalente desktop do Logcat do Android.
 *
 * O app Android mandava o usuário rodar `adb logcat -s ArkZARModelViewer` para
 * descobrir por que um modelo não aparecia; aqui o texto de Ajuda aponta para
 * `%LOCALAPPDATA%\ArkZ ARModelViewer\logs\app.log`, que é o mesmo tipo de informação
 * num arquivo que qualquer um consegue abrir.
 *
 * Também vai para o `stderr`: rodando pelo Gradle (`gradlew run`) o erro aparece no
 * terminal na hora, sem precisar abrir o arquivo.
 *
 * As chamadas vêm de threads diferentes (captura, detecção, interface), então a
 * escrita é sincronizada — e sempre dentro de `runCatching`: **log não pode
 * derrubar o app**.
 */
object AppLog {

    /** Acima deste tamanho, o log é rotacionado na abertura do app. */
    private const val MAX_LOG_BYTES = 2L * 1024 * 1024

    private val lock = Any()

    /** Caminho do arquivo de log (citado no texto de Ajuda). */
    val logFile: File get() = AppDirectories.logFile

    fun info(message: String) = write("INFO ", message, null)

    fun warn(message: String, error: Throwable? = null) = write("WARN ", message, error)

    fun error(message: String, error: Throwable? = null) = write("ERROR", message, error)

    /**
     * Roda uma vez na abertura: garante a pasta e evita um log que cresce sem fim
     * (renomeia o anterior, mantendo exatamente uma geração).
     */
    fun start() {
        runCatching {
            AppDirectories.logs.mkdirs()
            if (logFile.isFile && logFile.length() > MAX_LOG_BYTES) {
                val previous = File(logFile.parentFile, "app.previous.log")
                previous.delete()
                logFile.renameTo(previous)
            }
        }
        info("log iniciado (${appName()} ${appVersionLabel()})")

        // Registrado uma vez na abertura: numa máquina sem o runtime do Visual C++ a conversão de
        // modelo falha, e é bom que o log já diga isso antes de o usuário tentar (decisão 30).
        NativeLibraries.missingVisualCppRuntime().takeIf { ausentes -> ausentes.isNotEmpty() }?.let { ausentes ->
            warn(
                "Runtime do Visual C++ ausente nesta máquina (${ausentes.joinToString(", ")}) — " +
                    "a conversão de .obj/.stl/.ply/.3mf pode falhar.",
            )
        }
    }

    /**
     * Grava um quadro em tons de cinza como PNG, ao lado do log.
     *
     * Serve para **reproduzir um problema de detecção depois**: um teste pode ler o
     * arquivo salvo e rodar o detector sobre o quadro exato que falhou na máquina do
     * usuário — o que é bem mais eficiente do que descrever a cena por telefone.
     *
     * @return o caminho do arquivo salvo, ou `null` se não foi possível salvar.
     */
    fun dumpGrayFrame(bytes: ByteArray, width: Int, height: Int, name: String): String? = runCatching {
        require(bytes.size >= width * height) { "Buffer menor que o quadro." }
        AppDirectories.logs.mkdirs()

        val image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
        image.raster.setDataElements(0, 0, width, height, bytes.copyOf(width * height))

        val file = File(AppDirectories.logs, name)
        check(ImageIO.write(image, "png", file)) { "ImageIO sem suporte a PNG." }
        file.absolutePath
    }.getOrElse { error ->
        warn("Não foi possível gravar o quadro de diagnóstico: ${error.message}")
        null
    }

    private fun write(level: String, message: String, error: Throwable?) {
        val line = buildString {
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()))
            append(' ').append(level).append(' ')
            append('[').append(Thread.currentThread().name).append("] ")
            append(message)
            if (error != null) {
                append('\n')
                append(error.stackTraceToString())
            }
        }

        // Durante os testes o arquivo do usuário não é tocado (o Gradle marca a
        // execução com `arkz.test`); o `stderr` continua valendo, para um teste que
        // falhe aparecer na saída do build.
        if (System.getProperty("arkz.test") != "true") {
            synchronized(lock) {
                runCatching {
                    AppDirectories.logs.mkdirs()
                    logFile.appendText(line + "\n", Charsets.UTF_8)
                }
            }
        }

        System.err.println(line)
    }
}
