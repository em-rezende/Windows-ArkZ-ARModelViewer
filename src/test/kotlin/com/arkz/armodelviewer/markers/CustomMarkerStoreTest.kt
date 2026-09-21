/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.markers

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes do armazenamento de marcadores personalizados.
 *
 * Tudo roda numa pasta temporária (`@TempDir`), nunca na pasta real do usuário — e
 * o índice é verificado **como texto**, porque é ele que o usuário final pode
 * inspecionar quando um marcador "desaparece" (arquivo movido, PNG apagado).
 */
class CustomMarkerStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private fun store() = CustomMarkerStore(directory = tempDir.toFile())

    @Test
    fun `criar um marcador grava o PNG e o indice`() {
        val generated = MarkerGenerator.generate("Portaria")

        val result = store().addImage(generated, "Portaria")

        assertTrue(result.isSuccess, "esperado sucesso, veio: ${result.exceptionOrNull()}")
        val marker = assertNotNull(result.getOrNull())
        assertEquals("Portaria", marker.label)
        assertTrue(marker.isCustom)
        assertTrue(marker.id.startsWith("custom_"))

        val png = tempDir.resolve("${marker.id}.png").toFile()
        assertTrue(png.isFile, "o PNG do marcador deveria existir em ${png.absolutePath}")
        assertTrue(png.length() > 0, "o PNG do marcador está vazio")

        val line = tempDir.resolve("index.txt").toFile().readText(Charsets.UTF_8).trim()
        assertEquals("${marker.id}|Portaria|${marker.id}.png|0.15", line)
    }

    @Test
    fun `listar devolve os marcadores ordenados pelo rotulo`() {
        val store = store()
        store.addImage(MarkerGenerator.generate("Zebra", sizePixels = 205), "Zebra").getOrThrow()
        store.addImage(MarkerGenerator.generate("alfa", sizePixels = 205), "alfa").getOrThrow()

        assertEquals(listOf("alfa", "Zebra"), store.list().map { it.label })
    }

    @Test
    fun `carregar definicoes devolve as imagens prontas para uso`() {
        val store = store()
        store.addImage(MarkerGenerator.generate("Sala 1", sizePixels = 205), "Sala 1").getOrThrow()

        val definitions = store.loadDefinitions()

        assertEquals(1, definitions.size)
        val definition = definitions.first()
        assertEquals("Sala 1", definition.label)
        assertEquals(MarkerCatalog.DEFAULT_PHYSICAL_WIDTH_METERS, definition.physicalWidthMeters)
        assertEquals(205, definition.image.width)
    }

    @Test
    fun `carregar de arquivo usa o nome do arquivo como rotulo`() {
        val source = tempDir.resolve("planta-baixa.png").toFile()
        javax.imageio.ImageIO.write(
            MarkerGenerator.generate("Planta", sizePixels = 205),
            "png",
            source,
        )
        val store = CustomMarkerStore(
            directory = tempDir.resolve("destino").toFile(),
        )

        val marker = store.addFromFile(source).getOrThrow()

        assertEquals("planta-baixa", marker.label)
        assertTrue(store.loadDefinitions().isNotEmpty())
    }

    @Test
    fun `rotulo com separador nao quebra o indice`() {
        val marker = store().addImage(
            MarkerGenerator.generate("A|B", sizePixels = 205),
            "A|B",
        ).getOrThrow()

        val entry = store().list().single()
        assertEquals("A|B", marker.label)
        assertEquals("A-B", entry.label, "o separador é trocado por '-' no arquivo")
        assertEquals(marker.id, entry.id)
    }

    @Test
    fun `remover apaga o PNG e a entrada do indice`() {
        val store = store()
        val marker = store.addImage(MarkerGenerator.generate("Remover", sizePixels = 205), "Remover")
            .getOrThrow()

        store.remove(marker.id)

        assertTrue(store.list().isEmpty())
        assertTrue(!tempDir.resolve("${marker.id}.png").toFile().exists())
    }

    @Test
    fun `linha corrompida no indice e ignorada em vez de derrubar o app`() {
        tempDir.resolve("index.txt").toFile().writeText("linha-invalida\noutra|coisa\n", Charsets.UTF_8)

        assertTrue(store().list().isEmpty())
        assertTrue(store().loadDefinitions().isEmpty())
    }

    @Test
    fun `imagem ausente faz o marcador ser omitido`() {
        val store = store()
        val marker = store.addImage(MarkerGenerator.generate("Sumido", sizePixels = 205), "Sumido")
            .getOrThrow()
        tempDir.resolve("${marker.id}.png").toFile().delete()

        // A entrada continua no índice (o arquivo pode voltar), mas a definição não
        // é criada — e o app não quebra por causa disso.
        assertEquals(1, store.list().size)
        assertTrue(store.loadDefinitions().isEmpty())
    }

    @Test
    fun `decodificar uma linha valida devolve os campos`() {
        val entry = CustomMarkerStore.Entry.decode("custom_1|Projeto|file.png|0.2")

        assertNotNull(entry)
        assertEquals("custom_1", entry.id)
        assertEquals("Projeto", entry.label)
        assertEquals("file.png", entry.fileName)
        assertEquals(0.2f, entry.widthMeters)

        assertNull(CustomMarkerStore.Entry.decode("custom_1|Projeto"))
        assertNull(CustomMarkerStore.Entry.decode("custom_1|Projeto|file.png|largura-invalida"))
    }
}
