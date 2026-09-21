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

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Salva uma captura de tela no formato e na pasta do app Android.
 *
 * O que vem do renderizador (a cena pronta, **sem a interface**) é [saveCapture]:
 * o `render/` entrega um `BufferedImage` com câmera + modelo, e aqui ele recebe o
 * mesmo tratamento que o Android dava ao quadro espelhado — redimensionamento
 * opcional pela qualidade escolhida e gravação em
 * `Pictures/ArkZ ARModelViewer/ArkZARModelViewer_<data>.png`.
 *
 * No Android a captura era um espelhamento do *swap chain* do Filament num
 * `ImageReader` (para não capturar a interface). No desktop o caminho é mais
 * simples e mais direto: a cena é renderizada offscreen numa textura, e a
 * interface — que é Compose, em outra camada — nunca participa da imagem. O nome
 * do arquivo, a pasta e as qualidades são idênticos, para que a captura dos dois
 * apps caia lado a lado na mesma pasta.
 */
object ScreenCapture {

    /**
     * Lado maior da captura, em pixels — valor PADRÃO, usado quando o usuário não
     * escolheu outra qualidade no menu.
     *
     * Uma janela grande (2560×1440) geraria buffers de ~14 MB sem ganho real na
     * imagem; 1920 é a mesma "qualidade Alta" oferecida no app Android.
     */
    const val MAX_CAPTURE_LONG_SIDE = 1920

    /** Nome do arquivo, com a data e a hora locais — igual ao do app Android. */
    fun fileName(now: Date = Date()): String =
        "ArkZARModelViewer_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)}.png"

    /** Tamanho de uma captura depois de aplicar a qualidade escolhida. */
    data class CaptureSize(val width: Int, val height: Int)

    /**
     * Tamanho da captura: a mesma proporção da origem, com o lado maior limitado a
     * [maxLongSide] pixels (`null` = sem limite, resolução nativa da janela) e
     * ambos os lados pares (como os drivers esperam de uma superfície de
     * renderização).
     */
    fun scaledSize(width: Int, height: Int, maxLongSide: Int?): CaptureSize {
        val longestSide = maxOf(width, height)
        val factor = if (maxLongSide != null && longestSide > maxLongSide) {
            maxLongSide.toFloat() / longestSide
        } else {
            1f
        }
        return CaptureSize(even((width * factor).toInt()), even((height * factor).toInt()))
    }

    /** Arredonda para baixo até um número par (nunca menor que 2). */
    fun even(value: Int): Int = (value - value % 2).coerceAtLeast(2)

    /**
     * Amostra a imagem (32×32 pontos) para detectar um quadro de **uma única cor**
     * — sinal de que a cena não chegou a ser desenhada.
     *
     * É a mesma verificação do app Android, onde uma captura de uma cor só
     * significava que o espelhamento não rodou. Aqui ela protege contra salvar um
     * PNG preto quando o renderizador falha: é melhor devolver um erro visível do
     * que gravar um arquivo vazio de conteúdo.
     */
    fun isSingleColor(image: BufferedImage): Boolean {
        val stepX = (image.width / 32).coerceAtLeast(1)
        val stepY = (image.height / 32).coerceAtLeast(1)
        val first = image.getRGB(0, 0)

        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                if (image.getRGB(x, y) != first) return false
                x += stepX
            }
            y += stepY
        }
        return true
    }

    /**
     * Redimensiona (quando necessário) e grava a captura em
     * `Pictures/ArkZ ARModelViewer`.
     *
     * @param directory pasta de destino (injetável para os testes).
     * @return o caminho legível do arquivo salvo, ou o erro explicando o que
     *   impediu a gravação.
     */
    fun saveCapture(
        image: BufferedImage,
        fileName: String = fileName(),
        maxLongSide: Int? = MAX_CAPTURE_LONG_SIDE,
        directory: File = AppDirectories.pictures,
    ): Result<String> {
        val target = scaledSize(image.width, image.height, maxLongSide)

        val resized = if (target.width != image.width || target.height != image.height) {
            BufferedImage(target.width, target.height, BufferedImage.TYPE_INT_ARGB).also { scaled ->
                val graphics = scaled.createGraphics()
                try {
                    graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                    )
                    graphics.setRenderingHint(
                        RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY,
                    )
                    graphics.drawImage(image, 0, 0, target.width, target.height, null)
                } finally {
                    graphics.dispose()
                }
            }
        } else {
            image
        }

        return GallerySaver.saveImage(resized, fileName, directory)
    }
}
