/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.camera

import org.bytedeco.opencv.global.opencv_core.CV_8UC1
import org.bytedeco.opencv.global.opencv_core.CV_8UC3
import org.bytedeco.opencv.global.opencv_core.CV_8UC4
import org.bytedeco.opencv.opencv_core.Mat
import java.awt.image.BufferedImage

/**
 * Converte um quadro do OpenCV em `BufferedImage` ARGB.
 *
 * O `opencv_videoio` entrega os quadros em **BGR** (`CV_8UC3`, o padrão histórico
 * do OpenCV); algumas webcams e o backend Media Foundation entregam BGRA
 * (`CV_8UC4`) e a captura em tons de cinza é `CV_8UC1`. Os três casos são tratados
 * porque o app precisa que a imagem "simplesmente apareça" em qualquer máquina —
 * uma câmera que devolve quatro canais não pode virar um quadro embaralhado.
 *
 * Usamos uma conversão própria (e não o `Java2DFrameConverter` do JavaCV) de
 * propósito: assim a única dependência nativa é o módulo OpenCV, sem arrastar o
 * FFmpeg inteiro para dentro do instalador.
 *
 * @throws IllegalArgumentException se o quadro estiver vazio ou tiver um número de
 *   canais inesperado.
 */
fun Mat.toArgbImage(): BufferedImage {
    require(!empty()) { "Quadro vazio: a câmera não entregou imagem." }

    val width = cols()
    val height = rows()

    // Um `Mat` com padding por linha (não contínuo) precisa de uma cópia: a
    // leitura linear dos bytes daria a linha "errada" a cada troca de linha.
    val source = if (isContinuous()) this else Mat().also { copyTo(it) }
    try {
        val pixels = IntArray(width * height)
        when {
            channels() == 3 || channels() == 4 -> {
                val channels = channels()
                val bytes = ByteArray(width * height * channels)
                source.data().get(bytes, 0, bytes.size)

                var sourceIndex = 0
                for (pixel in pixels.indices) {
                    val blue = bytes[sourceIndex].toInt() and 0xFF
                    val green = bytes[sourceIndex + 1].toInt() and 0xFF
                    val red = bytes[sourceIndex + 2].toInt() and 0xFF
                    sourceIndex += channels
                    pixels[pixel] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
                }
            }

            channels() == 1 -> {
                val bytes = ByteArray(width * height)
                source.data().get(bytes, 0, bytes.size)

                for (pixel in pixels.indices) {
                    val gray = bytes[pixel].toInt() and 0xFF
                    pixels[pixel] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                }
            }

            else -> error("Quadro com ${channels()} canais não é suportado.")
        }

        return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, width, height, pixels, 0, width)
        }
    } finally {
        if (source !== this) {
            source.release()
        }
    }
}

/** Constantes de tipo usadas pela conversão (mantidas aqui para referência). */
internal object MatTypes {
    val BGR = CV_8UC3
    val BGRA = CV_8UC4
    val GRAY = CV_8UC1
}

/**
 * Luminância da imagem, no layout que o OpenCV espera (`CV_8UC1`, um byte por
 * pixel), escrita dentro de [target].
 *
 * Dois cuidados que fazem diferença em 30 quadros por segundo:
 *
 *  * os pixels são lidos de uma vez, para [pixelScratch] (`getRGB` em bloco), em
 *    vez de um `getRGB(x, y)` por pixel — a chamada por pixel custa cerca de 10 ms
 *    numa imagem de 1280×720, o que já seria um terço do orçamento de um quadro;
 *  * os arranjos são **reaproveitados** (nada é alocado por quadro).
 *
 * A conta usa os mesmos pesos do `cvtColor(BGR2GRAY)` (0,299 R · 0,587 G · 0,114 B):
 * uma fórmula mais barata ("média dos canais") mudaria o contraste das bordas e
 * **pioraria o casamento de pontos de interesse** — a detecção é sensível à escala
 * de cinza.
 *
 * @return [target], para uso encadeado.
 */
fun BufferedImage.toGrayBytes(target: ByteArray, pixelScratch: IntArray): ByteArray {
    val count = width * height
    require(target.size >= count) { "Alvo pequeno demais: ${target.size} bytes para $count pixels." }
    require(pixelScratch.size >= count) {
        "Buffer de pixels pequeno demais: ${pixelScratch.size} para $count pixels."
    }

    getRGB(0, 0, width, height, pixelScratch, 0, width)

    var index = 0
    while (index < count) {
        val pixel = pixelScratch[index]
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        target[index] = ((red * 299 + green * 587 + blue * 114) / 1000).toByte()
        index++
    }

    return target
}
