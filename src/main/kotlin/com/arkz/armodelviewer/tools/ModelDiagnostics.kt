/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.tools

import com.arkz.armodelviewer.render.AssimpModelLoader
import com.arkz.armodelviewer.util.AppDirectories
import com.arkz.armodelviewer.util.AppLog
import com.arkz.armodelviewer.util.NativeLibraries
import com.arkz.armodelviewer.util.appName
import com.arkz.armodelviewer.util.appVersionLabel
import java.io.File
import kotlin.system.exitProcess

/**
 * Diagnóstico do carregamento de modelo, pela linha de comando.
 *
 * ```powershell
 * "ArkZ ARModelViewer.exe" --check-model "C:\caminho\modelo.obj"
 * ```
 *
 * Existe para investigar um problema **na máquina do usuário**, sem depender da interface: foi
 * assim que o caso do notebook apareceu — "não consigo carregar o modelo 3D" e, no rodapé, só o
 * invólucro `Could not initialize class org.lwjgl.assimp.Assimp`.
 *
 * O relatório vai para **dois** lugares, porque o pacote portátil é um programa de janela (sem
 * console) e um `println` não apareceria em lugar nenhum:
 *
 * | Onde | Por quê |
 * |---|---|
 * | `%LOCALAPPDATA%\ArkZ ARModelViewer\logs\diagnostico-modelo.txt` | arquivo para mandar por e-mail |
 * | `%LOCALAPPDATA%\ArkZ ARModelViewer\logs\app.log` | fica junto do resto do histórico |
 *
 * O código de saída é `0` quando o modelo carrega, `1` quando falha e `2` quando faltou o
 * caminho do arquivo — útil para um script de verificação.
 */
object ModelDiagnostics {

    /** Argumento que liga o modo de diagnóstico. */
    const val MODEL_ARGUMENT = "--check-model"

    /** Nome do relatório, ao lado do log. */
    const val REPORT_FILE_NAME = "diagnostico-modelo.txt"

    private const val USAGE = 2

    /**
     * Executa o diagnóstico quando os argumentos o pedem.
     *
     * @return o código de saída, ou `null` quando **não** é uma chamada de diagnóstico (e o
     *   aplicativo deve abrir a janela normalmente).
     */
    fun handle(args: Array<String>): Int? {
        val index = args.indexOf(MODEL_ARGUMENT)
        if (index < 0) return null

        AppLog.start()

        val caminho = args.getOrNull(index + 1)
        if (caminho.isNullOrBlank()) {
            AppLog.error("Uso: $MODEL_ARGUMENT \"caminho do arquivo de modelo\"")
            return USAGE
        }

        val arquivo = File(caminho)
        val report = StringBuilder()
        report.appendLine(header())
        report.appendLine()
        report.appendLine(environment())
        report.appendLine()
        report.appendLine("Arquivo: ${arquivo.absolutePath}")
        report.appendLine()
        report.appendLine("--- log da conversão ---")

        AppLog.info("Diagnóstico de modelo: ${arquivo.absolutePath}")

        val resultado = AssimpModelLoader.prepare(arquivo) { linha ->
            report.appendLine(linha)
            AppLog.info("  $linha")
        }

        resultado.fold(
            onSuccess = { modelo ->
                report.appendLine()
                report.appendLine("--- resultado: OK ---")
                report.appendLine("Arquivo para o Filament: ${modelo.loadFile.absolutePath}")
                report.appendLine("Convertido: ${if (modelo.converted) "sim" else "não (já é glTF)"}")
                report.appendLine("Malhas: ${modelo.meshCount}   Triângulos: ${modelo.triangleCount}")
                report.appendLine("Caixa envolvente: ${modelo.bounds}")
                AppLog.info("Diagnóstico de modelo: OK — ${modelo.triangleCount} triângulos.")
            },
            onFailure = { erro ->
                report.appendLine()
                report.appendLine("--- resultado: FALHOU ---")
                report.appendLine(
                    NativeLibraries.describe(erro, NativeLibraries.missingVisualCppRuntime()),
                )
                report.appendLine()
                report.appendLine(erro.stackTraceToString())
                AppLog.error("Diagnóstico de modelo: falhou (${erro.message})", erro)
            },
        )

        val destino = gravar(report.toString())
        println(report)
        AppLog.info("Relatório do diagnóstico: ${destino?.absolutePath ?: "não foi possível gravar"}")

        return if (resultado.isSuccess) 0 else 1
    }

    /** Grava o relatório ao lado do log. Devolve o arquivo, ou `null` quando não conseguiu. */
    private fun gravar(texto: String): File? = runCatching {
        AppDirectories.logs.mkdirs()
        File(AppDirectories.logs, REPORT_FILE_NAME).apply { writeText(texto, Charsets.UTF_8) }
    }.getOrNull()

    private fun header(): String =
        "Diagnóstico de carregamento de modelo — ${appName()} ${appVersionLabel()}"

    /**
     * O ambiente, em texto: é o que evita a próxima rodada de perguntas por telefone.
     *
     * A conferência do runtime do Visual C++ é por **arquivo**, sem tentar carregar nada — o
     * veredito de verdade é a conversão que vem logo depois.
     */
    private fun environment(): String {
        val appDir = NativeLibraries.applicationDirectory()
        val windowsDir = File(System.getenv("WINDIR") ?: "C:\\Windows")
        val ausentes = NativeLibraries.missingVisualCppRuntime(appDir, windowsDir)

        fun situacao(name: String): String = when {
            File(appDir, name).isFile -> "na pasta do aplicativo"
            File(windowsDir, "System32/$name").isFile -> "no Windows"
            else -> "AUSENTE"
        }

        val texto = StringBuilder()
        texto.appendLine(
            "Sistema: ${System.getProperty("os.name")} ${System.getProperty("os.version")} " +
                "${System.getProperty("os.arch")}",
        )
        texto.appendLine("Java: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
        texto.appendLine("Pasta temporária: ${System.getProperty("java.io.tmpdir")}")
        texto.appendLine("Pasta do aplicativo: ${appDir.absolutePath}")
        texto.appendLine("Pasta de dados: ${AppDirectories.root}")
        NativeLibraries.visualCppRuntime.forEach { name ->
            texto.appendLine("Runtime do Visual C++ — $name: ${situacao(name)}")
        }
        if (ausentes.isNotEmpty()) {
            texto.appendLine("ATENÇÃO: sem ${ausentes.joinToString(", ")} nesta máquina —")
            texto.appendLine("é a causa mais provável de falha na conversão de .obj/.stl/.ply/.3mf.")
        }

        return texto.toString().trimEnd()
    }
}

/**
 * Entrada para a tarefa `modelCheck` do Gradle (`gradlew modelCheck -Pmodelo="caminho"`), que roda
 * o mesmo diagnóstico sem precisar do pacote montado.
 */
fun main(args: Array<String>) {
    exitProcess(ModelDiagnostics.handle(args) ?: 2)
}
