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

import com.arkz.armodelviewer.markers.MarkerDefinition
import com.arkz.armodelviewer.markers.MarkerGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes da folha de impressão.
 *
 * O teste decisivo é o último: **nenhum pixel escuro "de texto" pode aparecer
 * dentro da área do marcador**. Desenhar o rótulo por cima da figura é a mudança
 * mais natural do mundo para quem olha a folha — e quebraria o reconhecimento,
 * porque altera exatamente os detalhes que a detecção procura.
 */
class MarkerPrintSheetTest {

    private val margin = (MarkerPrintSheet.MARKER_PIXELS * 0.08f).toInt()

    private fun marker(label: String = "Marcador A"): MarkerDefinition = MarkerDefinition(
        id = "marker_a",
        label = label,
        physicalWidthMeters = 0.15f,
        image = MarkerGenerator.generate(label, sizePixels = MarkerPrintSheet.MARKER_PIXELS),
    )

    @Test
    fun `a largura da folha e o marcador mais as duas margens`() {
        assertEquals(MarkerPrintSheet.MARKER_PIXELS + 2 * margin, MarkerPrintSheet.sheetWidth())
    }

    @Test
    fun `a folha tem o marcador desenhado e o texto abaixo dele`() {
        val sheet = MarkerPrintSheet.build(marker(), "Imprima em 15 cm de largura.")

        assertEquals(MarkerPrintSheet.sheetWidth(), sheet.width)
        assertTrue(sheet.height > MarkerPrintSheet.MARKER_PIXELS, "a folha precisa do rodapé de texto")

        // Centro do finder superior-esquerdo do QR: preto (a imagem foi desenhada).
        val module = MarkerPrintSheet.MARKER_PIXELS / 41
        val black = sheet.getRGB(
            margin + (4 * module) + module / 2,
            margin + (4 * module) + module / 2,
        )
        assertEquals(0xFF000000.toInt(), black)

        // Rodapé: existe tinta preta (o rótulo e/ou a instrução).
        assertTrue(hasBlackPixels(sheet, margin + MarkerPrintSheet.MARKER_PIXELS + margin, sheet.height))
    }

    @Test
    fun `nada e desenhado sobre a area do marcador`() {
        val sheet = MarkerPrintSheet.build(
            marker("Um rótulo bem mais longo que o normal, para forçar a quebra em várias linhas"),
            "Aponte a câmera para a figura impressa a 30–60 cm, com boa iluminação.",
        )

        // Todo pixel da área do marcador é preto ou branco puros: a imagem do
        // marcador é binária e o desenho foi feito com interpolação "nearest" —
        // qualquer cinza aqui seria texto (ou suavização) por cima do código.
        for (y in margin until margin + MarkerPrintSheet.MARKER_PIXELS) {
            for (x in margin until margin + MarkerPrintSheet.MARKER_PIXELS) {
                val pixel = sheet.getRGB(x, y)
                assertTrue(
                    pixel == 0xFFFFFFFF.toInt() || pixel == 0xFF000000.toInt(),
                    "pixel não binário em ($x, $y): ${Integer.toHexString(pixel)} — " +
                        "alguma coisa foi desenhada sobre o marcador",
                )
            }
        }
    }

    @Test
    fun `um rotulo longo aumenta a altura da folha`() {
        val note = "Imprima em 15 cm de largura."

        val short = MarkerPrintSheet.build(marker("A"), note).height
        val long = MarkerPrintSheet.build(marker("Marcador do projeto de ampliação da recepção"), note).height

        assertTrue(long > short, "esperado quebra de linha: $long deveria ser maior que $short")
    }

    @Test
    fun `a folha comeca e termina com margem branca`() {
        val sheet = MarkerPrintSheet.build(marker(), "Instrução.")

        assertEquals(0xFFFFFFFF.toInt(), sheet.getRGB(0, 0))
        assertEquals(0xFFFFFFFF.toInt(), sheet.getRGB(sheet.width - 1, sheet.height - 1))
    }

    /** Existe pelo menos um pixel escuro na faixa horizontal informada? */
    private fun hasBlackPixels(image: java.awt.image.BufferedImage, fromY: Int, toY: Int): Boolean {
        for (y in fromY until minOf(toY, image.height)) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) == 0xFF000000.toInt()) return true
            }
        }
        return false
    }
}
