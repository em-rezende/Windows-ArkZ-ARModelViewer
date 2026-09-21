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

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes dos marcadores embutidos.
 *
 * O mais importante aqui é que o carregamento é feito **pelo classpath** — é o
 * mesmo caminho que o app usa em runtime. Se as imagens não estiverem
 * empacotadas em `src/main/resources/augmented_images/`, estes testes falham
 * antes de alguém abrir o app e descobrir que "o marcador não é detectado".
 */
class MarkerCatalogTest {

    private val markers = MarkerCatalog.loadBundled()

    @Test
    fun `carrega os dois marcadores embutidos do classpath`() {
        assertEquals(listOf("marker_a", "marker_b"), markers.map { it.id })
    }

    @Test
    fun `a largura fisica padrao e 15 cm`() {
        assertEquals(0.15f, MarkerCatalog.DEFAULT_PHYSICAL_WIDTH_METERS)
        markers.forEach { marker ->
            assertEquals(MarkerCatalog.DEFAULT_PHYSICAL_WIDTH_METERS, marker.physicalWidthMeters)
        }
    }

    @Test
    fun `as imagens sao normalizadas em ARGB`() {
        markers.forEach { marker ->
            assertEquals(
                BufferedImage.TYPE_INT_ARGB,
                marker.image.type,
                "${marker.id} deveria estar em TYPE_INT_ARGB",
            )
        }
    }

    @Test
    fun `as imagens tem tamanho util e nao sao de uma cor so`() {
        markers.forEach { marker ->
            assertTrue(marker.image.width >= 128, "${marker.id} tem ${marker.image.width} px de largura")
            assertEquals(marker.image.width, marker.image.height, "${marker.id} deveria ser quadrado")

            val pixels = HashSet<Int>()
            for (y in 0 until marker.image.height step 8) {
                for (x in 0 until marker.image.width step 8) {
                    pixels.add(marker.image.getRGB(x, y))
                    if (pixels.size > 1) break
                }
                if (pixels.size > 1) break
            }
            assertTrue(pixels.size > 1, "${marker.id} parece ter uma cor só (imagem corrompida?)")
        }
    }

    @Test
    fun `labelFor devolve o rotulo e cai no id quando o marcador e desconhecido`() {
        assertEquals("Marcador A", markers.labelFor("marker_a"))
        assertEquals("custom_123", markers.labelFor("custom_123"))
    }

    @Test
    fun `decodeArgb converte imagens sem canal alfa`() {
        val rgb = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
        val converted = rgb.toArgb()
        val same = converted.toArgb()

        assertEquals(BufferedImage.TYPE_INT_ARGB, converted.type)
        assertTrue(converted !== rgb, "uma imagem sem alfa precisa ser convertida")
        assertTrue(same === converted, "uma imagem já em ARGB não deve ser recopiada")

        // O caminho usado pelo app: uma imagem vinda de um InputStream (PNG).
        val decoded = decodeArgb(encodePng(rgb).inputStream())
        assertEquals(BufferedImage.TYPE_INT_ARGB, decoded.type)
        assertEquals(8, decoded.width)
    }

    private fun encodePng(image: BufferedImage): ByteArray {
        val bytes = ByteArrayOutputStream()
        ImageIO.write(image, "png", bytes)
        return bytes.toByteArray()
    }
}
