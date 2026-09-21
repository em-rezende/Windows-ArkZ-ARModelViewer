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

/**
 * Traduz a falha de carregamento de uma **biblioteca nativa** numa mensagem que diz o que fazer.
 *
 * ## O problema que originou isto
 *
 * No notebook do cliente o aplicativo abriu, o vídeo da câmera funcionou e **carregar modelo
 * falhou**, com esta frase no rodapé:
 *
 * ```
 * Could not initialize class org.lwjgl.assimp.Assimp
 * ```
 *
 * Essa frase é o invólucro que o JVM cria quando o inicializador estático de uma classe lança: o
 * motivo real — um `UnsatisfiedLinkError` — fica **na causa**, e a interface mostrava só o
 * invólucro. Diagnóstico da causa, feito na tabela de importação das DLLs do próprio pacote:
 * a `assimp.dll` do LWJGL (e a `draco.dll` que ela usa) importa `msvcp140.dll`,
 * `vcruntime140.dll` e `vcruntime140_1.dll` — o runtime do **Visual C++ 2015-2022**. Numa máquina
 * sem ele, o carregador do Windows não acha essas DLLs e o Assimp não inicializa.
 *
 * A correção principal é de empacotamento (o `createDistributable` copia as três DLLs para junto
 * do `.exe`, veja o `build.gradle.kts`). O que sobra aqui é o **diagnóstico**: mostrar a causa
 * profunda em vez do invólucro e dizer qual é o caminho da solução.
 *
 * Curiosidade que fecha o caso: o OpenCV não sofre do mesmo, embora também importe o runtime — o
 * JavaCPP extrai a própria cópia do runtime na pasta dele, junto das DLLs do OpenCV. Por isso a
 * câmera funcionava no notebook e só a conversão de modelo falhava.
 */
object NativeLibraries {

    /** DLLs do runtime do Visual C++ que as bibliotecas nativas do aplicativo importam. */
    val visualCppRuntime = listOf("msvcp140.dll", "vcruntime140.dll", "vcruntime140_1.dll")

    /** A causa mais profunda de [error] — é ela que traz o motivo de verdade. */
    fun rootCause(error: Throwable): Throwable {
        var current = error
        while (true) {
            current = current.cause?.takeIf { cause -> cause !== current } ?: return current
        }
    }

    /**
     * `true` quando a falha veio do carregador de bibliotecas do sistema (e não, por exemplo, de
     * um arquivo de modelo inválido).
     */
    fun isNativeLibraryFailure(error: Throwable): Boolean = chain(error).any { current ->
        current is UnsatisfiedLinkError ||
            current is ExceptionInInitializerError ||
            (
                current is NoClassDefFoundError &&
                    current.message?.startsWith("Could not initialize class") == true
                )
    }

    /**
     * Mensagem para o usuário: a explicação do carregador (causa profunda) e, quando o runtime do
     * Visual C++ não está nem na pasta do aplicativo nem no Windows, o caminho da solução.
     *
     * [missingVcppRuntime] vem de [missingVisualCppRuntime] — separado para esta função continuar
     * sendo pura e testável.
     */
    fun describe(error: Throwable, missingVcppRuntime: List<String> = emptyList()): String {
        val root = rootCause(error)
        val motivo = root.message?.takeIf { it.isNotBlank() } ?: root.javaClass.simpleName

        if (!isNativeLibraryFailure(error)) return motivo

        val dica = if (missingVcppRuntime.isEmpty()) {
            "Pode ser a biblioteca nativa de conversão de modelos: veja a pasta do aplicativo e o log."
        } else {
            "Nesta máquina falta o runtime do Visual C++ (${missingVcppRuntime.joinToString(", ")}) — " +
                "instale o \"Microsoft Visual C++ Redistributable 2015-2022 (x64)\" e abra o " +
                "aplicativo de novo."
        }

        return "A biblioteca nativa de conversão de modelos não carregou: $motivo. $dica"
    }

    /**
     * Quais DLLs do runtime do Visual C++ não estão na pasta do aplicativo **nem** no Windows.
     *
     * É o que se pode afirmar sem tentar carregar nada (a resposta definitiva vem do próprio
     * erro). Serve para o log da abertura e para o relatório do `--check-model`.
     */
    fun missingVisualCppRuntime(
        appDirectory: File = applicationDirectory(),
        windowsDirectory: File = File(System.getenv("WINDIR") ?: "C:\\Windows"),
    ): List<String> = visualCppRuntime.filterNot { name ->
        File(appDirectory, name).isFile || File(windowsDirectory, "System32/$name").isFile
    }

    /**
     * A pasta do executável — no pacote portátil é a raiz (`ArkZ ARModelViewer.exe`), que é
     * justamente o lugar onde o Windows procura as DLLs depois de olhar a pasta da própria DLL.
     */
    fun applicationDirectory(): File =
        runCatching { ProcessHandle.current().info().command().orElse(null) }
            .getOrNull()
            ?.let { command -> File(command).parentFile }
            ?: File(".")

    /** A falha e todas as suas causas, da mais externa para a mais profunda. */
    private fun chain(error: Throwable): List<Throwable> = buildList {
        var current: Throwable? = error
        while (current != null && size < 16) {
            add(current)
            current = current.cause?.takeIf { it !== current }
        }
    }
}
