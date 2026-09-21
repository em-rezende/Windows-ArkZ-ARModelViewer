/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.markers

import com.arkz.armodelviewer.util.AppDirectories
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Armazena os **marcadores personalizados** — imagens escolhidas pelo usuário
 * (arquivo de imagem do computador) ou desenhadas pelo próprio app
 * ([MarkerGenerator]) para serem rastreadas pela detecção.
 *
 * Diferenças em relação ao app Android, e por quê:
 *
 *  * a imagem é copiada para `%LOCALAPPDATA%\ArkZ ARModelViewer\custom_markers` e
 *    regravada como PNG; no Android a cópia existia porque a permissão de leitura
 *    da URI original expirava — aqui ela tem o mesmo mérito (o arquivo original
 *    pode ser movido ou apagado pelo usuário) e mantém o índice válido;
 *  * o índice fica em `index.txt`, uma linha por marcador no MESMO formato de
 *    `Entry.encode()` do Android (`id|rótulo|arquivo|largura`), em vez do
 *    `StringSet` do `SharedPreferences`: é legível, ordenado e diffável.
 */
class CustomMarkerStore(
    private val directory: File = AppDirectories.customMarkers,
    private val indexFile: File = File(directory, INDEX_FILE_NAME),
) {

    /** Metadados de um marcador personalizado. */
    data class Entry(
        val id: String,
        val label: String,
        val fileName: String,
        val widthMeters: Float,
    ) {
        /** Codificação simples (`|` é removido do rótulo para não quebrar o formato). */
        fun encode(): String = "$id|${label.replace('|', '-')}|$fileName|$widthMeters"

        companion object {
            fun decode(encoded: String): Entry? {
                val parts = encoded.split('|')
                if (parts.size < 4) return null
                val width = parts[3].toFloatOrNull() ?: return null
                return Entry(parts[0], parts[1], parts[2], width)
            }
        }
    }

    /** Lista os marcadores personalizados salvos (ordenados pelo rótulo). */
    fun list(): List<Entry> = readIndex()
        .mapNotNull { Entry.decode(it) }
        .sortedBy { it.label.lowercase() }

    /** Carrega as imagens dos marcadores personalizados, prontas para uso. */
    fun loadDefinitions(): List<MarkerDefinition> = list().mapNotNull { entry ->
        runCatching {
            val file = File(directory, entry.fileName)
            require(file.isFile) { "Imagem do marcador não encontrada: ${entry.fileName}" }
            val image = file.inputStream().use { decodeArgb(it) }
            MarkerDefinition(
                id = entry.id,
                label = entry.label,
                physicalWidthMeters = entry.widthMeters,
                image = image,
                isCustom = true,
            )
        }.getOrNull()
    }

    /**
     * Registra um marcador a partir de uma **imagem já pronta** — usada pelo
     * "Criar marcador" (que desenha a figura com [MarkerGenerator]) e pelo
     * "Carregar marcador" (depois de a imagem escolhida ser decodificada).
     */
    fun addImage(
        image: BufferedImage,
        label: String,
        widthMeters: Float = MarkerCatalog.DEFAULT_PHYSICAL_WIDTH_METERS,
    ): Result<MarkerDefinition> = runCatching {
        store(
            image = image,
            label = label.trim().takeIf { it.isNotEmpty() } ?: "Marcador personalizado",
            widthMeters = widthMeters,
        )
    }

    /**
     * Copia [source] para o app e registra um novo marcador — o "Carregar
     * marcador" (imagem que já está no computador).
     *
     * Sem nome digitado, o rótulo vem do nome do arquivo — como no app Android.
     */
    fun addFromFile(
        source: File,
        label: String? = null,
        widthMeters: Float = MarkerCatalog.DEFAULT_PHYSICAL_WIDTH_METERS,
    ): Result<MarkerDefinition> = runCatching {
        require(source.isFile) { "Arquivo não encontrado: ${source.absolutePath}" }
        val image = source.inputStream().use { decodeArgb(it) }
        store(
            image = image,
            label = label?.takeIf { it.isNotBlank() }
                ?: source.nameWithoutExtension.ifBlank { "Marcador personalizado" },
            widthMeters = widthMeters,
        )
    }

    /** Remove um marcador personalizado (metadados + imagem). */
    fun remove(id: String) {
        writeIndex(list().filterNot { it.id == id })
        File(directory, "$id.png").delete()
    }

    /**
     * Grava o PNG e os metadados, e devolve o marcador pronto para uso imediato.
     *
     * O identificador carrega o horário (`custom_<millis>`), como no app Android:
     * dois marcadores criados com o mesmo nome são marcadores diferentes.
     */
    private fun store(image: BufferedImage, label: String, widthMeters: Float): MarkerDefinition {
        val id = "custom_${System.currentTimeMillis()}"
        val fileName = "$id.png"
        directory.mkdirs()

        val target = File(directory, fileName)
        check(ImageIO.write(image, "png", target)) { "O ImageIO não tem suporte a PNG." }

        val entry = Entry(id = id, label = label, fileName = fileName, widthMeters = widthMeters)
        writeIndex(list().filterNot { it.id == id } + entry)

        return MarkerDefinition(
            id = entry.id,
            label = entry.label,
            physicalWidthMeters = entry.widthMeters,
            image = image,
            isCustom = true,
        )
    }

    private fun readIndex(): List<String> =
        if (!indexFile.isFile) {
            emptyList()
        } else {
            runCatching { indexFile.readLines(Charsets.UTF_8).filter { it.isNotBlank() } }
                .getOrDefault(emptyList())
        }

    private fun writeIndex(entries: List<Entry>) {
        directory.mkdirs()
        indexFile.writeText(entries.joinToString("\n") { it.encode() } + "\n", Charsets.UTF_8)
    }

    private companion object {
        const val INDEX_FILE_NAME = "index.txt"
    }
}
