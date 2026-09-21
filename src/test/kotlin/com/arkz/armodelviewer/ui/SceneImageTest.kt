/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.ui

import com.arkz.armodelviewer.render.BackgroundFrame
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Testes da conversão do quadro da câmera para a textura do renderizador.
 *
 * É um teste pequeno que guarda uma armadilha grande: a ordem dos canais e das linhas. Um
 * `ARGB` do AWT lido como `BGRA` deixa o vídeo com **vermelho e azul trocados** — um
 * defeito que parece "configuração da câmera" e custa horas; e linhas invertidas deixam o
 * vídeo de **cabeça para baixo**, que é o defeito clássico de quem trabalha com texturas.
 */
class SceneImageTest {

    @Test
    fun `o quadro vira RGBA de cima para baixo`() {
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0xFFFF0000.toInt()) // canto superior esquerdo: vermelho opaco
        image.setRGB(1, 0, 0xFF00FF00.toInt()) // verde
        image.setRGB(0, 1, 0xFF0000FF.toInt()) // azul (linha de BAIXO)
        image.setRGB(1, 1, 0xFFFFFFFF.toInt()) // branco

        val frame = SceneImageCache().toBackgroundFrame(image)

        assertEquals(2, frame.width)
        assertEquals(2, frame.height)
        assertEquals(8, frame.rowBytes)

        // Primeiro pixel: R, G, B, A — nesta ordem.
        assertEquals(listOf(255, 0, 0, 255), rgbaAt(frame, 0, 0))
        assertEquals(listOf(0, 255, 0, 255), rgbaAt(frame, 1, 0))
        // A linha de baixo continua sendo a de baixo: a primeira linha do arranjo é o TOPO.
        assertEquals(listOf(0, 0, 255, 255), rgbaAt(frame, 0, 1))
        assertEquals(listOf(255, 255, 255, 255), rgbaAt(frame, 1, 1))
    }

    @Test
    fun `o cache reaproveita o arranjo entre quadros`() {
        val cache = SceneImageCache()

        val first = cache.toBackgroundFrame(solidImage(3, 3, 0xFF102030.toInt()))
        val second = cache.toBackgroundFrame(solidImage(2, 2, 0xFF405060.toInt()))

        // O arranjo é o mesmo (é um cache) e continua com os valores do quadro NOVO.
        assertEquals(first.rgba, second.rgba)
        assertEquals(listOf(0x40, 0x50, 0x60, 255), rgbaAt(second, 0, 0))
    }

    private fun solidImage(width: Int, height: Int, argb: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            for (x in 0 until width) image.setRGB(x, y, argb)
        }
        return image
    }

    /** Os quatro canais do pixel (x, y), como inteiros de 0 a 255. */
    private fun rgbaAt(frame: BackgroundFrame, x: Int, y: Int): List<Int> {
        val index = y * frame.rowBytes + x * 4
        return (0 until 4).map { frame.rgba[index + it].toInt() and 0xFF }
    }
}
