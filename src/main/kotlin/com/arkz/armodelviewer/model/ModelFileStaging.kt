/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.model

import com.arkz.armodelviewer.util.AppDirectories
import java.io.File

/**
 * Um modelo já copiado para a área de dados do app.
 *
 * @property file Arquivo local.
 * @property displayName Nome original do arquivo (mostrado na interface).
 */
data class StagedModel(
    val file: File,
    val displayName: String,
) {
    /**
     * Localização entregue ao carregador de modelos.
     *
     * No Android isto era uma URI `file://` **e não** a `content://` original,
     * porque a permissão de leitura concedida pelo seletor expirava quando a
     * Activity era recriada. No desktop o problema não existe (o usuário escolhe
     * um arquivo do disco), mas a cópia continua valendo: o nome único por escolha
     * é o que dispara o recarregamento, e o arquivo original pode ser movido
     * enquanto o app está aberto.
     */
    val location: String get() = file.absolutePath
}

/**
 * Copia para a área de dados do app um modelo escolhido pelo usuário no diálogo
 * nativo do Windows.
 *
 * Espelha o `ModelFileStaging` do app Android, com uma diferença de plataforma
 * importante: lá a validação precisa ser por **extensão** porque o Android não
 * reconhece os tipos MIME de `.glb`, `.obj` e `.ply` (chegam como
 * `application/octet-stream` e apareceriam esmaecidos num filtro por MIME). Aqui o
 * `FileDialog` do Compose filtra por extensão, o que é melhor para o usuário — mas
 * a validação continua sendo por extensão, no mesmo lugar, porque o usuário pode
 * digitar um nome qualquer na caixa de texto do diálogo.
 */
object ModelFileStaging {

    /**
     * Extensões aceitas: glTF/GLB (`.glb`/`.gltf`), OBJ (`.obj`), STL (`.stl`),
     * PLY (`.ply`) e 3MF (`.3mf`).
     *
     * `.dae` (COLLADA) **não** é suportado: o Filament só lê glTF/GLB e a
     * conversão dos outros formatos passa pelo Assimp, que não trata COLLADA de
     * forma confiável para impressão 3D — veja [unsupportedFormatMessage].
     */
    val supportedExtensions: Set<String> = setOf("glb", "gltf", "obj", "stl", "ply", "3mf")

    /** Mensagem de ajuda para formatos que não são suportados. */
    fun unsupportedFormatMessage(extension: String): String = when (extension.lowercase()) {
        "dae" ->
            "O formato .dae (COLLADA) não é suportado. Converta o arquivo para .glb " +
                "(Blender: Arquivo ▸ Exportar ▸ glTF 2.0) e carregue o .glb."
        "fbx" ->
            "O formato .fbx não é suportado. Exporte como .glb (glTF 2.0)."
        else ->
            "Formato \".$extension\" não suportado. Use .glb, .gltf, .obj, .stl, .ply ou .3mf."
    }

    /**
     * Copia [source] para a pasta de modelos do app.
     *
     * @param directory pasta de destino (injetável para testes).
     * @return [Result] para que a camada de interface possa mostrar mensagens
     *   específicas (formato inválido, arquivo ilegível, etc.) — como no Android.
     */
    fun stage(
        source: File,
        directory: File = AppDirectories.openedModels,
    ): Result<StagedModel> = runCatching {
        require(source.isFile) { "Não foi possível ler o arquivo selecionado." }

        val displayName = source.name
        val extension = displayName.substringAfterLast('.', "").lowercase()
        require(extension in supportedExtensions) {
            unsupportedFormatMessage(extension.ifEmpty { "desconhecido" })
        }

        directory.mkdirs()
        // Nome ÚNICO por escolha — e não "opened_model.glb" fixo. A localização do
        // arquivo é a chave observada pelo carregamento: com o mesmo caminho para
        // todos os `.glb`, escolher um segundo modelo não disparava recarregamento
        // nenhum e o primeiro continuava na cena.
        val target = File(directory, "modelo_${System.currentTimeMillis()}.$extension")
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }

        StagedModel(file = target, displayName = displayName)
    }

    /** Apaga as cópias antigas dos modelos (chamado uma vez, ao abrir a tela). */
    fun cleanup(directory: File = AppDirectories.openedModels) {
        runCatching {
            directory.listFiles()?.forEach { file -> file.delete() }
        }
    }
}
