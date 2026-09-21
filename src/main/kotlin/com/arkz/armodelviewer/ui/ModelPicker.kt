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

import com.arkz.armodelviewer.model.ModelFileStaging
import com.arkz.armodelviewer.render.AssimpModelLoader
import com.arkz.armodelviewer.render.PreparedModel
import com.arkz.armodelviewer.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Escolha do modelo 3D: o diálogo **nativo do Windows** e a preparação do arquivo.
 *
 * ## Por que o `FileDialog` do AWT
 *
 * É o diálogo do sistema (o mesmo que qualquer programa do Windows abre), com a barra
 * lateral, os locais de rede e a caixa de digitação de nome — e é o que o app Android
 * fazia com o seletor de documentos. O filtro é por **extensão** (`ModelFileStaging`),
 * porque o Windows não conhece `.glb`, `.obj` nem `.ply` fora de programas específicos.
 *
 * ## O trabalho pesado sai da thread de interface
 *
 * Copiar o arquivo para a área do app e convertê-lo para GLB (`.obj`, `.stl`, `.ply`,
 * `.3mf`) pode levar segundos num modelo grande: tudo acontece em [Dispatchers.IO], e a
 * interface só recebe o resultado.
 */
object ModelPicker {

    /**
     * Abre o diálogo nativo e devolve o arquivo escolhido (`null` se o usuário cancelar).
     *
     * @param title título da janela do diálogo (traduzido).
     */
    suspend fun choose(title: String, parent: Frame? = null): File? = withContext(Dispatchers.IO) {
        val dialog = FileDialog(parent, title, FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            ModelFileStaging.supportedExtensions.any { extension ->
                name.lowercase().endsWith(".$extension")
            }
        }
        dialog.isVisible = true

        val directory = dialog.directory
        val file = dialog.file
        dialog.dispose()

        if (directory == null || file == null) null else File(directory, file)
    }

    /**
     * Prepara o arquivo escolhido: valida a extensão, copia para a área de dados do app e,
     * quando o formato não é glTF, converte para GLB ao lado da cópia.
     *
     * Devolve o modelo medido e pronto para o renderizador — ou o motivo da recusa, que é
     * mostrado ao usuário (formato não suportado, arquivo ilegível, malha vazia).
     */
    suspend fun prepare(
        source: File,
        onLog: (String) -> Unit = { message -> AppLog.info(message) },
    ): Result<PreparedModel> = withContext(Dispatchers.IO) {
        ModelFileStaging.stage(source).mapCatching { staged ->
            onLog("Modelo escolhido: ${staged.displayName}")
            AssimpModelLoader.prepare(staged.file, onLog).getOrThrow()
        }
    }

    /**
     * Abre o diálogo nativo para escolher uma **imagem** (carregar marcador).
     *
     * É o mesmo diálogo do modelo, com outro filtro: o usuário pode apontar para uma foto,
     * um QR code ou um print de tela que já esteja no computador.
     */
    suspend fun chooseImage(title: String, parent: Frame? = null): File? =
        withContext(Dispatchers.IO) {
            val dialog = FileDialog(parent, title, FileDialog.LOAD)
            dialog.setFilenameFilter { _, name ->
                IMAGE_EXTENSIONS.any { extension -> name.lowercase().endsWith(".$extension") }
            }
            dialog.isVisible = true

            val directory = dialog.directory
            val file = dialog.file
            dialog.dispose()

            if (directory == null || file == null) null else File(directory, file)
        }

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "bmp", "gif")
}
