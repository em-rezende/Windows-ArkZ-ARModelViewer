/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3, publicada pela Free Software
 * Foundation. Este programa é distribuído na esperança de que seja útil, mas SEM
 * NENHUMA GARANTIA; sem mesmo a garantia implícita de COMERCIABILIDADE ou
 * ADEQUAÇÃO A UM PROPÓSITO ESPECÍFICO. Veja o arquivo LICENSE na raiz do projeto.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 * Versão Windows do app Android:
 * https://github.com/em-rezende/Android-ArkZ-ARModelViewer
 */

package com.arkz.armodelviewer.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Converte uma imagem AWT em `ImageBitmap` do Compose.
 *
 * Só há dois caminhos no Compose Desktop: um `Skia Image` (como aqui) ou um
 * `ImageBitmap` construído pixel a pixel. Como o Skia cria a imagem a partir de
 * bytes codificados, o PNG é o formato intermediário — é o mesmo formato que o
 * app já usa para salvar capturas e marcadores, então não há custo novo.
 */
fun BufferedImage.toImageBitmap(): ImageBitmap {
    val bytes = ByteArrayOutputStream()
    check(ImageIO.write(this, "png", bytes)) { "O ImageIO não tem suporte a PNG." }
    return SkiaImage.makeFromEncoded(bytes.toByteArray()).toComposeImageBitmap()
}
