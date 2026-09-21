/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.model

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Testes da preparação do arquivo de modelo.
 *
 * A validação é por **extensão** (nunca por tipo MIME), e isso é uma decisão
 * herdada do app Android: lá o `ACTION_OPEN_DOCUMENT` filtra por MIME, e o Android
 * não reconhece `.glb`, `.obj` e `.ply` (chegam como `application/octet-stream`).
 * Aqui o diálogo do Windows filtra por extensão, mas a validação continua no mesmo
 * lugar porque o usuário pode digitar qualquer nome na caixa do diálogo.
 */
class ModelFileStagingTest {

    @TempDir
    lateinit var tempDir: Path

    private val destination: File get() = tempDir.resolve("opened_models").toFile()

    private fun sourceFile(name: String, content: String = "conteudo"): File =
        tempDir.resolve(name).toFile().apply { writeText(content) }

    @Test
    fun `copia o modelo com nome unico e preserva o nome original`() {
        val source = sourceFile("mesa.glb")

        val staged = ModelFileStaging.stage(source, destination).getOrThrow()

        assertEquals("mesa.glb", staged.displayName)
        assertTrue(staged.file.name.startsWith("modelo_"), staged.file.name)
        assertTrue(staged.file.name.endsWith(".glb"), staged.file.name)
        assertTrue(staged.file.isFile)
        assertEquals(staged.file.absolutePath, staged.location)
        assertTrue(source.isFile, "o arquivo original não pode ser apagado nem movido")
        assertEquals("conteudo", staged.file.readText())
    }

    @Test
    fun `duas escolhas do mesmo arquivo geram destinos diferentes`() {
        val source = sourceFile("casa.glb")
        val first = ModelFileStaging.stage(source, destination).getOrThrow()

        // A localização é a chave observada pelo carregamento: com o mesmo caminho
        // para todos os arquivos, escolher um segundo modelo não recarregaria nada
        // e o primeiro continuaria na cena (bug documentado no app Android).
        Thread.sleep(10)
        val second = ModelFileStaging.stage(source, destination).getOrThrow()

        assertNotEquals(first.file, second.file)
    }

    @Test
    fun `todas as extensoes suportadas sao aceitas`() {
        ModelFileStaging.supportedExtensions.forEach { extension ->
            val staged = ModelFileStaging.stage(sourceFile("modelo.$extension"), destination)

            assertTrue(staged.isSuccess, "'.$extension' deveria ser aceita: ${staged.exceptionOrNull()}")
            assertEquals(extension, staged.getOrThrow().file.extension)
        }
    }

    @Test
    fun `formato nao suportado e recusado com a mensagem de ajuda`() {
        val dae = ModelFileStaging.stage(sourceFile("cena.dae"), destination)

        assertTrue(dae.isFailure)
        val message = dae.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("COLLADA"), "mensagem inesperada: $message")
        assertTrue(message.contains("Blender"), "a ajuda deve ensinar a converter: $message")
        assertEquals(0, destination.listFiles()?.size ?: 0, "nada deve ser copiado")
    }

    @Test
    fun `arquivo sem extensao e recusado`() {
        val result = ModelFileStaging.stage(sourceFile("modelo"), destination)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("desconhecido"))
    }

    @Test
    fun `as mensagens de formato citam a alternativa correta`() {
        assertTrue(ModelFileStaging.unsupportedFormatMessage("fbx").contains("glTF 2.0"))
        assertTrue(ModelFileStaging.unsupportedFormatMessage("xyz").contains(".3mf"))
    }

    @Test
    fun `arquivo inexistente falha sem excecao vazando para a interface`() {
        val result = ModelFileStaging.stage(tempDir.resolve("nao-existe.glb").toFile(), destination)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().isNotBlank())
    }

    @Test
    fun `cleanup apaga apenas as copias`() {
        val source = sourceFile("predio.glb")
        ModelFileStaging.stage(source, destination).getOrThrow()
        ModelFileStaging.stage(source, destination).getOrThrow()

        ModelFileStaging.cleanup(destination)

        assertEquals(0, destination.listFiles()?.size ?: 0)
        assertTrue(source.isFile, "o arquivo do usuário não é tocado pelo cleanup")
    }
}
