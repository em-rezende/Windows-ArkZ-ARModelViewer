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

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.arkz.armodelviewer.render.BackgroundFrame
import com.arkz.armodelviewer.render.SceneFrame
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Image as SkiaImage
import java.awt.image.BufferedImage

/**
 * As duas conversões de imagem que ligam a câmera e o renderizador à tela, **com os
 * buffers reaproveitados**.
 *
 * * [toBackgroundFrame] — o quadro da webcam (o `BufferedImage` que a captura entrega) em
 *   RGBA, que é o que o renderizador sobe para a textura do plano de fundo. A ordem é
 *   **RGBA** (e não BGRA) porque é a ordem que o `PixelBufferDescriptor` do Filament
 *   declara: trocar as duas primeiras letras aqui deixa o vídeo com vermelho e azul
 *   trocados — um defeito que passa por "configuração de câmera" quando não se sabe onde
 *   procurar;
 * * [toImageBitmap] — o quadro **desenhado** (já em RGBA) como imagem do Compose, por
 *   `Image.makeRaster` do Skia: sem compressão e sem alocação por quadro. Reaproveitar o
 *   caminho do vídeo (que codifica PNG) custaria de 10 a 25 ms por quadro.
 *
 * A classe é **estado mutável de propósito** (é um cache): guarde uma instância e chame os
 * métodos a cada quadro.
 */
class SceneImageCache {

    private var pixels = IntArray(0)
    private var rgba = ByteArray(0)

    /** Quadro da câmera em RGBA, de cima para baixo — pronto para a textura do Filament. */
    fun toBackgroundFrame(image: BufferedImage): BackgroundFrame {
        val width = image.width
        val height = image.height
        val count = width * height

        if (pixels.size < count) {
            pixels = IntArray(count)
            rgba = ByteArray(count * 4)
        }

        image.getRGB(0, 0, width, height, pixels, 0, width)

        var target = 0
        for (index in 0 until count) {
            val pixel = pixels[index]
            rgba[target++] = ((pixel shr 16) and 0xFF).toByte() // R
            rgba[target++] = ((pixel shr 8) and 0xFF).toByte()  // G
            rgba[target++] = (pixel and 0xFF).toByte()          // B
            rgba[target++] = ((pixel shr 24) and 0xFF).toByte() // A
        }

        return BackgroundFrame(rgba, width, height)
    }

    /**
     * Quadro desenhado como imagem do AWT, para salvar em PNG (a captura de tela).
     *
     * É o caminho de volta da [toBackgroundFrame]: aqui o RGBA vira o `TYPE_INT_ARGB` que o
     * `ImageIO` grava. Sem a conversão, o arquivo salvo sairia com vermelho e azul trocados
     * — o mesmo cuidado da ida, pelo mesmo motivo.
     */
    fun toBufferedImage(frame: SceneFrame): BufferedImage {
        val image = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_ARGB)
        val pixels = IntArray(frame.width * frame.height)

        var index = 0
        for (position in pixels.indices) {
            val red = frame.rgba[index].toInt() and 0xFF
            val green = frame.rgba[index + 1].toInt() and 0xFF
            val blue = frame.rgba[index + 2].toInt() and 0xFF
            val alpha = frame.rgba[index + 3].toInt() and 0xFF
            pixels[position] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            index += 4
        }

        image.setRGB(0, 0, frame.width, frame.height, pixels, 0, frame.width)
        return image
    }

    /**
     * Quadro desenhado como imagem do Compose.
     *
     * A imagem é criada declarando `ColorType.RGBA_8888` — e não o "N32" do Skia, que no
     * Windows é BGRA: sem isso, vermelho e azul sairiam trocados na tela.
     */
    fun toImageBitmap(frame: SceneFrame): ImageBitmap {
        val info = ImageInfo(
            frame.width,
            frame.height,
            ColorType.RGBA_8888,
            ColorAlphaType.UNPREMUL,
        )
        return SkiaImage.makeRaster(info, frame.rgba, frame.rowBytes).toComposeImageBitmap()
    }
}
