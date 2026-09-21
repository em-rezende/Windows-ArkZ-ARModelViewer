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

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Grava um PNG na biblioteca de imagens do Windows.
 *
 * É o equivalente desktop do `saveBitmapToGallery` do app Android: lá o
 * `MediaStore` colocava a imagem na galeria (`Pictures/ArkZ ARModelViewer`) sem
 * pedir permissão; aqui é literalmente a pasta `%USERPROFILE%\Pictures\ArkZ
 * ARModelViewer`, que aparece no Explorador, na galeria do Windows e no "Salvar
 * como" de qualquer aplicativo.
 *
 * Usa `ImageIO` (o codificador PNG do próprio JDK): nenhuma dependência extra para
 * uma operação que o app já fazia no Android.
 */
object GallerySaver {

    /**
     * Grava [image] como PNG em [directory], criando a pasta se preciso.
     *
     * @return o caminho legível do arquivo salvo — o mesmo tipo de texto que o app
     *   Android mostra na barra de status ("Pictures/ArkZ ARModelViewer/arquivo.png").
     */
    fun saveImage(image: BufferedImage, fileName: String, directory: File): Result<String> = runCatching {
        require(fileName.isNotBlank()) { "Nome de arquivo vazio." }
        directory.mkdirs()
        if (!directory.isDirectory) {
            error("Não foi possível criar a pasta ${directory.absolutePath}.")
        }

        val target = File(directory, fileName)
        // `ImageIO.write` devolve `false` quando não há codificador para o formato
        // — silencioso por natureza, e a imagem simplesmente não existiria no disco.
        val written = ImageIO.write(image, "png", target)
        check(written) { "O ImageIO não tem suporte a PNG nesta instalação." }

        target.absolutePath
    }

    /** Grava uma captura de tela em `Pictures/ArkZ ARModelViewer`. */
    fun saveScreenshot(image: BufferedImage, fileName: String): Result<String> =
        saveImage(image, fileName, AppDirectories.pictures)

    /** Grava a imagem de um marcador em `Pictures/ArkZ ARModelViewer/Marcadores`. */
    fun saveMarkerImage(image: BufferedImage, fileName: String): Result<String> =
        saveImage(image, fileName, AppDirectories.markerPictures)
}
