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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes do gerador de marcadores.
 *
 * O teste que importa é o de **determinismo**: o app Android e o Windows precisam
 * produzir a MESMA figura para o mesmo nome. Se isso quebrar, um marcador criado
 * (e impresso) num aparelho Android deixa de ser reconhecido no Windows — uma
 * falha silenciosa, que só apareceria na frente do usuário.
 */
class MarkerGeneratorTest {

    /** Lado útil da imagem padrão: 1024 px / 41 módulos = 24 px por módulo. */
    private val expectedSide = (MarkerGenerator.DEFAULT_SIZE_PIXELS / 41) * 41

    @Test
    fun `o mesmo nome gera exatamente a mesma imagem`() {
        val first = MarkerGenerator.generate("Projeto 1")
        val second = MarkerGenerator.generate("Projeto 1")

        assertEquals(first.width, second.width)
        assertTrue(
            first.toPixelBytes().contentEquals(second.toPixelBytes()),
            "duas gerações do mesmo nome deveriam ser idênticas",
        )
    }

    @Test
    fun `nomes diferentes geram figuras diferentes`() {
        assertFalse(
            MarkerGenerator.generate("Projeto 1").toPixelBytes()
                .contentEquals(MarkerGenerator.generate("Projeto 2").toPixelBytes()),
            "nomes diferentes precisam gerar marcadores diferentes",
        )
    }

    @Test
    fun `maiusculas e espacos nao mudam a figura`() {
        val canonical = MarkerGenerator.generate("Portaria").toPixelBytes()

        assertTrue(canonical.contentEquals(MarkerGenerator.generate("portaria").toPixelBytes()))
        assertTrue(canonical.contentEquals(MarkerGenerator.generate("  PORTARIA  ").toPixelBytes()))
    }

    @Test
    fun `o lado e multiplo exato de modulos e nao fica com meio pixel`() {
        val image = MarkerGenerator.generate("Modulos")
        assertEquals(expectedSide, image.width)
        assertEquals(image.width, image.height)
    }

    @Test
    fun `respeita o tamanho pedido`() {
        val small = MarkerGenerator.generate("Pequeno", sizePixels = 205)
        assertEquals(205, small.width)
        assertEquals(BufferedImage.TYPE_INT_ARGB, small.type)
    }

    @Test
    fun `tamanho invalido e recusado`() {
        assertFailsWith<IllegalArgumentException> { MarkerGenerator.generate("X", sizePixels = 0) }
    }

    @Test
    fun `a zona de silencio em volta e branca`() {
        val image = MarkerGenerator.generate("Silencio")
        val module = image.width / 41
        val lastInner = image.width - module

        assertEquals(WHITE, image.getRGB(0, 0))
        assertEquals(WHITE, image.getRGB(image.width - 1, image.height - 1))
        assertEquals(WHITE, image.getRGB(module, module - 1))
        assertEquals(WHITE, image.getRGB(lastInner, lastInner + 1))
    }

    @Test
    fun `os tres finder patterns estao nos cantos`() {
        val image = MarkerGenerator.generate("Finders")
        val module = image.width / 41
        val quiet = 4 * module

        // O finder tem 7 módulos: quadrado preto externo (0..6), anel branco (1..5)
        // e núcleo preto (2..4). As amostras são feitas na linha central do
        // finder (3,5 módulos), cruzando as três faixas:
        //   0,5 → preto (borda) · 1,5 → branco (anel) · 3,5 → preto (núcleo)
        //   5,5 → branco (anel) · 6,5 → preto (borda)
        val centerOfFinder = 3.5f
        listOf(0 to 0, 26 to 0, 0 to 26).forEach { (moduleX, moduleY) ->
            val left = quiet + moduleX * module
            val top = quiet + moduleY * module
            val y = (top + centerOfFinder * module).toInt()

            listOf(
                0.5f to BLACK,
                1.5f to WHITE,
                3.5f to BLACK,
                5.5f to WHITE,
                6.5f to BLACK,
            ).forEach { (offsetInModules, expected) ->
                val x = (left + offsetInModules * module).toInt()
                assertEquals(
                    expected,
                    image.getRGB(x, y),
                    "módulo $offsetInModules do finder em ($moduleX, $moduleY)",
                )
            }
        }
    }

    @Test
    fun `as areas dos finders nao recebem sorteio`() {
        val image = MarkerGenerator.generate("Reservado")
        val module = image.width / 41
        val quiet = 4 * module

        // A coluna logo à direita do finder superior-esquerdo é o "separador":
        // sempre branca, porque a área está reservada ao sorteio.
        val separatorX = quiet + 7 * module
        for (y in 0 until 7) {
            assertEquals(
                WHITE,
                image.getRGB(separatorX, quiet + y * module + module / 2),
                "o separador do finder não pode receber módulos sorteados",
            )
        }
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }

    /** Bytes dos pixels, para comparar duas imagens sem depender de `equals`. */
    private fun BufferedImage.toPixelBytes(): ByteArray {
        val bytes = ByteArray(width * height * 4)
        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = getRGB(x, y)
                bytes[index++] = (pixel shr 24).toByte()
                bytes[index++] = (pixel shr 16).toByte()
                bytes[index++] = (pixel shr 8).toByte()
                bytes[index++] = pixel.toByte()
            }
        }
        return bytes
    }
}
