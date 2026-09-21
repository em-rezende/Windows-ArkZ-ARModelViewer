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
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Image as SkiaImage
import java.awt.image.BufferedImage

/**
 * Converte os quadros da câmera em `ImageBitmap` **reaproveitando os buffers**.
 *
 * Por que não reutilizar `BufferedImage.toImageBitmap()` (que codifica um PNG)?
 * Porque isso custaria de 10 a 25 ms por quadro: a 30 quadros por segundo o vídeo
 * da webcam ficaria visivelmente travado. Aqui os pixels são copiados uma única
 * vez para um arranjo de bytes reaproveitado e o Skia cria a imagem em memória
 * (`Image.makeRaster`), sem compressão e sem alocação por quadro.
 *
 * A classe é **estado mutável de propósito** (é um cache): guarde uma instância
 * por `remember` e chame [toImageBitmap] a cada quadro.
 *
 * Nota de cor: o arranjo é montado em **RGBA** e a imagem é criada declarando
 * `ColorType.RGBA_8888` — sem depender do "N32" do Skia, que no Windows é BGRA.
 * Sem isso, vermelho e azul sairiam trocados no vídeo.
 */
class CameraBitmapCache {

    private var pixels = IntArray(0)
    private var bytes = ByteArray(0)

    /** Cria um `ImageBitmap` com o conteúdo de [image]. */
    fun toImageBitmap(image: BufferedImage): ImageBitmap {
        val width = image.width
        val height = image.height
        val count = width * height

        if (pixels.size < count) {
            pixels = IntArray(count)
            bytes = ByteArray(count * 4)
        }

        image.getRGB(0, 0, width, height, pixels, 0, width)

        var target = 0
        for (index in 0 until count) {
            val pixel = pixels[index]
            bytes[target++] = ((pixel shr 16) and 0xFF).toByte() // R
            bytes[target++] = ((pixel shr 8) and 0xFF).toByte()  // G
            bytes[target++] = (pixel and 0xFF).toByte()          // B
            bytes[target++] = ((pixel shr 24) and 0xFF).toByte() // A
        }

        val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
        return SkiaImage.makeRaster(info, bytes, width * 4).toComposeImageBitmap()
    }
}
