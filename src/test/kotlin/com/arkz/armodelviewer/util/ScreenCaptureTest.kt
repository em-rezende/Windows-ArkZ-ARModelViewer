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
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes da captura de tela (nomes, qualidades e gravação).
 *
 * A renderização da cena em si é testada na etapa 7, junto com o renderizador; o
 * que está aqui é tudo que o Android também fazia *depois* de capturar: nome com
 * data, limite de resolução, detecção de quadro vazio e gravação.
 */
class ScreenCaptureTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `o nome do arquivo segue o padrao do app Android`() {
        val name = ScreenCapture.fileName()

        assertTrue(
            name.matches(Regex("ArkZARModelViewer_\\d{8}_\\d{6}\\.png")),
            "nome inesperado: '$name'",
        )
    }

    @Test
    fun `a qualidade limita o lado maior mantendo a proporcao`() {
        val size = ScreenCapture.scaledSize(width = 2560, height = 1440, maxLongSide = 1920)

        assertEquals(1920, size.width)
        assertEquals(1080, size.height)
    }

    @Test
    fun `uma imagem menor que o limite nao e ampliada`() {
        val size = ScreenCapture.scaledSize(width = 800, height = 600, maxLongSide = 1920)

        assertEquals(800, size.width)
        assertEquals(600, size.height)
    }

    @Test
    fun `sem limite o tamanho apenas fica par`() {
        val size = ScreenCapture.scaledSize(width = 1001, height = 999, maxLongSide = null)

        assertEquals(1000, size.width)
        assertEquals(998, size.height)
    }

    @Test
    fun `o arredondamento nunca desce abaixo de dois pixels`() {
        assertEquals(2, ScreenCapture.even(1))
        assertEquals(2, ScreenCapture.even(0))
        assertEquals(100, ScreenCapture.even(101))
    }

    @Test
    fun `detecta um quadro de uma cor so`() {
        val flat = BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB).apply {
            val graphics = createGraphics()
            graphics.color = Color.BLACK
            graphics.fillRect(0, 0, width, height)
            graphics.dispose()
        }
        assertTrue(ScreenCapture.isSingleColor(flat), "um quadro preto não tem cena desenhada")

        flat.setRGB(150, 60, Color.WHITE.rgb)
        assertFalse(ScreenCapture.isSingleColor(flat))
    }

    @Test
    fun `a captura e gravada com a qualidade pedida`() {
        val source = BufferedImage(2560, 1440, BufferedImage.TYPE_INT_ARGB).apply {
            val graphics = createGraphics()
            graphics.color = Color(0xFF336699.toInt())
            graphics.fillRect(0, 0, width, height)
            graphics.color = Color.WHITE
            graphics.fillRect(100, 100, 400, 300)
            graphics.dispose()
        }

        val path = ScreenCapture.saveCapture(
            image = source,
            fileName = "captura.png",
            maxLongSide = 1920,
            directory = tempDir.toFile(),
        ).getOrThrow()

        val saved = ImageIO.read(File(path))
        assertEquals(1920, saved.width)
        assertEquals(1080, saved.height)
        assertFalse(
            ScreenCapture.isSingleColor(saved),
            "a captura deve manter o conteúdo da cena, e não virar uma cor só",
        )
    }

    @Test
    fun `a captura sem limite usa a resolucao nativa`() {
        val source = BufferedImage(1024, 768, BufferedImage.TYPE_INT_ARGB)

        val path = ScreenCapture.saveCapture(
            image = source,
            fileName = "nativa.png",
            maxLongSide = null,
            directory = tempDir.toFile(),
        ).getOrThrow()

        val saved = ImageIO.read(File(path))
        assertEquals(1024, saved.width)
        assertEquals(768, saved.height)
    }
}
