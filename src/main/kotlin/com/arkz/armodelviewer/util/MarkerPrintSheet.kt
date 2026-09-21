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
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Gera a **folha de impressão** de um marcador.
 *
 * A folha tem a mesma composição do gerador do app Android: o marcador limpo em
 * cima, com margem branca em volta, e o **rótulo + instruções ABAIXO** da figura.
 *
 * Nada é desenhado *sobre* o marcador de propósito: qualquer texto por cima muda
 * os detalhes visuais que a detecção usa para reconhecer a imagem — no Android
 * pelo ARCore, aqui pelo casamento de pontos de interesse. Este é um caso em que
 * uma "melhoria estética" quebraria a funcionalidade.
 *
 * O texto quebra em várias linhas e é centralizado: o rótulo é digitado pelo
 * usuário (pode ser longo) e a instrução muda de tamanho em cada idioma. No
 * Android isso era um `StaticLayout`; aqui é quebra por palavras medida com
 * `FontMetrics` — o resultado visível é o mesmo.
 */
object MarkerPrintSheet {

    /** Lado do marcador na folha, em pixels (≈ 900 px ≈ 7,6 cm a 300 dpi). */
    const val MARKER_PIXELS = 900

    /** Margem branca em volta do marcador (zona de silêncio para a impressão). */
    private const val MARGIN_RATIO = 0.08f

    /** Tamanho do rótulo e da instrução, proporcionais ao marcador. */
    private const val LABEL_TEXT_RATIO = 0.075f
    private const val NOTE_TEXT_RATIO = 0.032f

    /** Espaço entre o rótulo e a instrução. */
    private const val CAPTION_GAP_RATIO = 0.03f

    /** Largura da folha para um marcador — conferível e usada nos testes. */
    fun sheetWidth(): Int = MARKER_PIXELS + 2 * margin()

    /**
     * Desenha a folha de impressão em memória.
     *
     * @param marker marcador a imprimir (imagem + rótulo).
     * @param note instrução já traduzida e formatada (o `print_sheet_note` do
     *   idioma atual, com a largura em centímetros).
     */
    fun build(marker: MarkerDefinition, note: String): BufferedImage {
        val markerSize = MARKER_PIXELS
        val margin = margin()
        val sheetWidth = markerSize + 2 * margin
        val textWidth = sheetWidth - 2 * margin
        val captionGap = (markerSize * CAPTION_GAP_RATIO).toInt()

        val labelFont = Font(Font.SANS_SERIF, Font.BOLD, (markerSize * LABEL_TEXT_RATIO).toInt())
        val noteFont = Font(Font.SANS_SERIF, Font.PLAIN, (markerSize * NOTE_TEXT_RATIO).toInt())

        // Uma "régua" gráfica só para medir: a altura final da folha depende de
        // quantas linhas o rótulo e a instrução ocupam.
        val ruler = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        var graphics: Graphics2D = ruler.createGraphics()
        val labelLines = wrap(marker.label, labelFont, textWidth, graphics)
        val noteLines = wrap(note, noteFont, textWidth, graphics)
        val lineHeight = graphics.fontMetrics.height
        graphics.dispose()

        val captionTop = margin + markerSize + margin
        val sheetHeight = captionTop + labelLines.size * lineHeight + captionGap +
            noteLines.size * lineHeight + margin

        val sheet = BufferedImage(sheetWidth, sheetHeight, BufferedImage.TYPE_INT_ARGB)
        graphics = sheet.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, sheetWidth, sheetHeight)

            // Ampliação SEM interpolação: os módulos ficam com bordas nítidas, que
            // é o que a detecção precisa para reconhecer a figura impressa.
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
            )
            graphics.drawImage(marker.image, margin, margin, markerSize, markerSize, null)

            graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
            )
            graphics.color = Color.BLACK
            graphics.font = labelFont
            var top = captionTop
            labelLines.forEach { line ->
                drawCentered(graphics, line, sheetWidth, top)
                top += lineHeight
            }

            top += captionGap
            graphics.font = noteFont
            noteLines.forEach { line ->
                drawCentered(graphics, line, sheetWidth, top)
                top += lineHeight
            }
        } finally {
            graphics.dispose()
        }

        return sheet
    }

    /** Margem branca em volta do marcador, em pixels. */
    private fun margin(): Int = (MARKER_PIXELS * MARGIN_RATIO).toInt()

    /** Desenha [text] centralizado horizontalmente, com o topo da linha em [top]. */
    private fun drawCentered(graphics: Graphics2D, text: String, sheetWidth: Int, top: Int) {
        val metrics = graphics.fontMetrics
        val width = metrics.stringWidth(text)
        graphics.drawString(text, (sheetWidth - width) / 2, top + metrics.ascent)
    }

    /** Quebra [text] em linhas que cabem em [maxWidth], respeitando as palavras. */
    private fun wrap(text: String, font: Font, maxWidth: Int, graphics: Graphics2D): List<String> {
        graphics.font = font
        val metrics = graphics.fontMetrics
        val lines = mutableListOf<String>()

        text.split('\n').forEach { paragraph ->
            val words = paragraph.split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                lines.add("")
                return@forEach
            }

            val current = StringBuilder()
            words.forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (current.isEmpty() || metrics.stringWidth(candidate) <= maxWidth) {
                    current.clear()
                    current.append(candidate)
                } else {
                    lines.add(current.toString())
                    current.clear()
                    current.append(word)
                }
            }
            if (current.isNotEmpty()) lines.add(current.toString())
        }

        return if (lines.isEmpty()) listOf("") else lines
    }
}
