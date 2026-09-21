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
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Testes da escolha da pasta de imagens.
 *
 * O caso que importa é o desta máquina: a pasta "Imagens" foi **movida para outro disco**
 * (`D:\Imagens`), e por isso `%USERPROFILE%\Pictures` não existe. Quem sabe a resposta é o
 * Windows — a chave `My Pictures` do registro —, e é isso que o código passou a consultar.
 * A primeira versão adivinhava por `%OneDrive%\Pictures` e acertava por acaso em alguns
 * computadores, errando em todos os outros.
 */
class AppDirectoriesTest {

    @TempDir
    lateinit var tempDir: Path

    private fun directory(vararg names: String): File =
        tempDir.resolve(names.joinToString("/")).toFile().apply { mkdirs() }

    /** Um "registro" de mentira: só responde o que o teste definir. */
    private fun shellFolders(vararg values: Pair<String, String>): (String) -> String? {
        val map = values.toMap()
        return { name -> map[name] }
    }

    @Test
    fun `usa a pasta Imagens do Windows, mesmo movida para outro disco`() {
        val home = directory("usuario")
        val moved = directory("D", "Imagens")

        val base = AppDirectories.pictureBase(
            environment = emptyMap(),
            home = home,
            shellFolder = shellFolders(AppDirectories.PICTURES_SHELL_FOLDER to moved.path),
            canWriteInto = { true },
        )

        assertEquals(moved, base, "a resposta do Windows vence qualquer palpite")
    }

    @Test
    fun `sem a resposta do Windows vale a pasta Imagens do usuario`() {
        val home = directory("usuario")
        val pictures = directory("usuario", "Pictures")

        val base = AppDirectories.pictureBase(
            environment = emptyMap(),
            home = home,
            shellFolder = shellFolders(),
            canWriteInto = { true },
        )

        assertEquals(pictures, base)
    }

    @Test
    fun `um caminho do Windows que nao existe e descartado`() {
        val home = directory("usuario")
        val pictures = directory("usuario", "Pictures")

        val base = AppDirectories.pictureBase(
            environment = emptyMap(),
            home = home,
            shellFolder = shellFolders(
                AppDirectories.PICTURES_SHELL_FOLDER to tempDir.resolve("nao-existe").toString(),
            ),
            canWriteInto = { true },
        )

        assertEquals(pictures, base)
    }

    @Test
    fun `se nao der para gravar em Imagens, as capturas vao para Documentos`() {
        // O pedido explícito de quem usa o aplicativo: "não salve em OneDrive; se não der
        // na Imagens, use Documentos".
        val home = directory("usuario")
        val pictures = directory("usuario", "Pictures")
        val documents = directory("D", "Documentos")

        val base = AppDirectories.pictureBase(
            environment = emptyMap(),
            home = home,
            shellFolder = shellFolders(
                AppDirectories.PICTURES_SHELL_FOLDER to pictures.path,
                AppDirectories.DOCUMENTS_SHELL_FOLDER to documents.path,
            ),
            // "Imagens" existe, mas a escrita é recusada (pasta somente leitura).
            canWriteInto = { it != pictures },
        )

        assertEquals(documents, base)
    }

    @Test
    fun `usa o Documentos do Windows quando nao ha Imagens em lugar nenhum`() {
        val home = directory("usuario")
        val documents = directory("D", "Documentos")

        val base = AppDirectories.pictureBase(
            environment = emptyMap(),
            home = home,
            shellFolder = shellFolders(
                AppDirectories.PICTURES_SHELL_FOLDER to tempDir.resolve("ausente").toString(),
                AppDirectories.DOCUMENTS_SHELL_FOLDER to documents.path,
            ),
            canWriteInto = { true },
        )

        assertEquals(documents, base)
    }

    @Test
    fun `sem nenhuma pasta existente, aponta para Documentos do usuario`() {
        val home = directory("usuario")

        val base = AppDirectories.pictureBase(
            environment = emptyMap(),
            home = home,
            shellFolder = shellFolders(),
            canWriteInto = { true },
        )

        assertEquals(File(home, "Documents"), base, "quem grava é que cria a pasta")
    }

    @Test
    fun `o caminho do registro e lido com espacos no meio`() {
        // A saída real do `reg query`, com um caminho que tem espaço.
        val output = """
            HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Explorer\User Shell Folders
                My Pictures    REG_EXPAND_SZ    C:\Users\Ana\Minhas Imagens
        """.trimIndent()

        assertEquals("C:\\Users\\Ana\\Minhas Imagens", AppDirectories.parseRegistryValue(output))
    }

    @Test
    fun `o caminho do registro desta maquina e lido como esta`() {
        // A saída real desta máquina, em que "Imagens" foi movida para outro disco.
        val output = """
            HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Explorer\User Shell Folders
                My Pictures    REG_EXPAND_SZ    D:\Imagens
        """.trimIndent()

        assertEquals("D:\\Imagens", AppDirectories.parseRegistryValue(output))
    }

    @Test
    fun `um valor sem caminho nao vira pasta`() {
        assertNull(AppDirectories.parseRegistryValue(""))
        assertNull(AppDirectories.parseRegistryValue("ERRO: o sistema não pôde encontrar..."))
        assertNull(
            AppDirectories.parseRegistryValue(
                "HKEY_CURRENT_USER\\...\\User Shell Folders\n    My Pictures    REG_EXPAND_SZ    ",
            ),
        )
    }

    @Test
    fun `as variaveis do caminho do registro sao expandidas`() {
        val expanded = AppDirectories.expandEnvironment(
            path = "%USERPROFILE%\\Imagens",
            environment = mapOf("USERPROFILE" to "C:\\Users\\Ana"),
        )

        assertEquals("C:\\Users\\Ana\\Imagens", expanded)
    }
}
