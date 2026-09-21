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

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes do tradutor de falhas de biblioteca nativa.
 *
 * O caso que originou o código está registrado na decisão 30 do roadmap: no notebook sem o
 * runtime do Visual C++, carregar modelo mostrava no rodapé apenas
 * `Could not initialize class org.lwjgl.assimp.Assimp` — o invólucro do JVM, sem o motivo e
 * sem o que fazer. Os testes aqui usam a **mesma cadeia de exceções** que a máquina produziu
 * (medida na causa do erro real), e não uma invenção: é ela que precisa continuar legível.
 *
 * A conferência de "quais DLLs faltam" é feita com pastas temporárias, para não depender do
 * que está instalado na máquina que roda o teste.
 */
class NativeLibrariesTest {

    @TempDir
    lateinit var tempDir: Path

    /** A cadeia exata do notebook: invólucro → erro do inicializador → falha de carregamento. */
    private fun erroDoNotebook(): Throwable {
        val raiz = UnsatisfiedLinkError(
            "C:\\Temp\\lwjgl-arkz\\assimp.dll: Não foi possível localizar o módulo " +
                "especificado (msvcp140.dll).",
        )
        // `ExceptionInInitializerError(Throwable)` guarda o lançado como causa — o construtor
        // sem argumentos NÃO serve: ele já chama `initCause(null)` e a causa vira imutável.
        val inicializador = ExceptionInInitializerError(raiz)
        return NoClassDefFoundError("Could not initialize class org.lwjgl.assimp.Assimp")
            .apply { initCause(inicializador) }
    }

    /** Uma pasta vazia para representar "sem as DLLs". */
    private fun pastaVazia(nome: String): File = tempDir.resolve(nome).toFile().apply { mkdirs() }

    /** Uma pasta com os nomes de DLL informados. */
    private fun pastaCom(nome: String, dlls: List<String>): File =
        pastaVazia(nome).apply { dlls.forEach { dll -> File(this, dll).writeText("x") } }

    @Test
    fun `a mensagem mostra a causa profunda, e nao o involucro do jvm`() {
        val texto = NativeLibraries.describe(erroDoNotebook())

        assertContains(texto, "Não foi possível localizar o módulo")
        assertFalse(
            texto == "Could not initialize class org.lwjgl.assimp.Assimp",
            "o invólucro do JVM sozinho não diz nada ao usuário: \"$texto\"",
        )
        assertContains(texto, "biblioteca nativa", message = "e é uma falha de biblioteca nativa")
    }

    @Test
    fun `sem o runtime do visual c++ a mensagem diz o que instalar`() {
        val texto = NativeLibraries.describe(erroDoNotebook(), NativeLibraries.visualCppRuntime)

        assertContains(texto, "Visual C++ Redistributable 2015-2022 (x64)")
        NativeLibraries.visualCppRuntime.forEach { dll -> assertContains(texto, dll) }
    }

    @Test
    fun `com o runtime presente a mensagem nao manda instalar nada`() {
        val texto = NativeLibraries.describe(erroDoNotebook(), emptyList())

        assertFalse(texto.contains("Redistributable"), "não há o que instalar: \"$texto\"")
    }

    @Test
    fun `uma falha de formato nao ganha o texto do runtime`() {
        val texto = NativeLibraries.describe(IllegalArgumentException("o formato .dae não é suportado"))

        assertEquals("o formato .dae não é suportado", texto)
    }

    @Test
    fun `erro sem mensagem cai no nome da classe`() {
        assertEquals("IllegalStateException", NativeLibraries.describe(IllegalStateException()))
    }

    @Test
    fun `reconhece falha de biblioteca nativa e nao confunde com erro de formato`() {
        assertTrue(NativeLibraries.isNativeLibraryFailure(erroDoNotebook()))
        assertTrue(NativeLibraries.isNativeLibraryFailure(UnsatisfiedLinkError("no msvcp140 in java.library.path")))
        assertFalse(NativeLibraries.isNativeLibraryFailure(IllegalArgumentException("formato não suportado")))
    }

    @Test
    fun `lista as tres dlls quando a maquina nao tem nenhuma`() {
        val ausentes = NativeLibraries.missingVisualCppRuntime(pastaVazia("app"), pastaVazia("windows"))

        assertEquals(NativeLibraries.visualCppRuntime, ausentes)
    }

    @Test
    fun `a pasta do aplicativo basta para o runtime estar presente`() {
        val aplicativo = pastaCom("app2", NativeLibraries.visualCppRuntime)

        assertTrue(
            NativeLibraries.missingVisualCppRuntime(aplicativo, pastaVazia("windows2")).isEmpty(),
            "o pacote agora leva as DLLs ao lado do executável",
        )
    }

    @Test
    fun `o System32 do Windows tambem conta como presente`() {
        val windows = pastaVazia("windows3")
        File(windows, "System32").mkdirs()
        NativeLibraries.visualCppRuntime.forEach { dll -> File(windows, "System32/$dll").writeText("x") }

        assertTrue(
            NativeLibraries.missingVisualCppRuntime(pastaVazia("app3"), windows).isEmpty(),
            "é o caso de quem tem o Visual C++ instalado",
        )
    }
}
