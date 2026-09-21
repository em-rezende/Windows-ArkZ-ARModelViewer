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
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes do gravador de PNG.
 *
 * O que está sendo protegido aqui é a promessa feita ao usuário: "a imagem foi
 * salva em …". Um `ImageIO.write` que devolve `false` (sem codificador para o
 * formato) não lança exceção — a imagem simplesmente não existiria no disco, e o
 * app diria que salvou.
 */
class GallerySaverTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `grava o PNG e cria a pasta quando ela ainda nao existe`() {
        val directory = tempDir.resolve("Pictures/${AppDirectories.PICTURES_FOLDER_NAME}/Marcadores")
            .toFile()
        assertTrue(!directory.exists(), "a pasta não deveria existir antes do teste")

        val image = BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB)
        val path = GallerySaver.saveImage(image, "marcador.png", directory).getOrThrow()

        val file = File(path)
        assertTrue(file.isFile, "o arquivo deveria existir em $path")
        assertEquals(directory, file.parentFile)

        val read = ImageIO.read(file)
        assertEquals(64, read.width)
        assertEquals(32, read.height)
    }

    @Test
    fun `nome vazio falha em vez de gravar um arquivo sem nome`() {
        val result = GallerySaver.saveImage(
            BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB),
            "   ",
            tempDir.toFile(),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `gravar o mesmo nome de novo substitui o arquivo`() {
        val directory = tempDir.toFile()
        GallerySaver.saveImage(BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "a.png", directory)
            .getOrThrow()

        GallerySaver.saveImage(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "a.png", directory)
            .getOrThrow()

        assertEquals(1, directory.listFiles { file -> file.name == "a.png" }?.size)
        assertEquals(16, ImageIO.read(File(directory, "a.png")).width)
    }
}
