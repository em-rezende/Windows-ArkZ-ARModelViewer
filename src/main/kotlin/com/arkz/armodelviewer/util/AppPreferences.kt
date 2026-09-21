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

import java.io.File
import java.util.Properties

/**
 * Preferências simples do app — o equivalente desktop do `SharedPreferences`.
 *
 * Guarda apenas o que precisa sobreviver ao reinício e não pertence a nenhum
 * marcador: se a **Ajuda já foi mostrada** (primeira execução), o **idioma**
 * escolhido no menu ⋮ e a **qualidade escolhida para a captura de tela**. As
 * chaves são as mesmas do app Android (`help_shown`, `language_tag`,
 * `capture_max_long_side`), para que o diagnóstico dos dois apps seja comparável.
 *
 * O arquivo é um `preferences.properties` legível (`%LOCALAPPDATA%\ArkZ
 * ARModelViewer\preferences.properties`): diferente do `SharedPreferences`
 * binário do Android, dá para conferir/editar num editor de texto quando o
 * usuário relata um problema.
 */
class AppPreferences(private val file: File = AppDirectories.preferencesFile) {

    private val properties = Properties().apply {
        if (file.isFile) {
            runCatching { file.inputStream().use { load(it) } }
        }
    }

    /** `true` depois que a Ajuda foi exibida uma vez (primeira execução). */
    var helpShown: Boolean
        get() = properties.getProperty(KEY_HELP_SHOWN)?.toBooleanStrictOrNull() ?: false
        set(value) = put(KEY_HELP_SHOWN, value.toString())

    /**
     * Idioma forçado pelo menu **"Idioma"** (código BCP-47: `pt-BR`, `pt-PT`,
     * `en`, `es`, `fr`, `de`, `it`, `zh-CN`) ou `null` para seguir o idioma do
     * Windows.
     */
    var languageTag: String?
        get() = properties.getProperty(KEY_LANGUAGE_TAG)?.takeIf { it.isNotBlank() }
        set(value) = put(KEY_LANGUAGE_TAG, value.orEmpty())

    /**
     * Lado maior da captura de tela, em pixels — `null` significa "resolução
     * nativa da janela" (sem limite).
     *
     * O valor é guardado como inteiro (0 = sem limite) porque as propriedades não
     * têm tipo anulável — mesma convenção do app Android. Sem nada guardado, o
     * padrão é 1920 (a "qualidade Alta" do menu), exatamente como no Android.
     */
    var captureMaxLongSide: Int?
        get() = (properties.getProperty(KEY_CAPTURE_MAX_LONG_SIDE)?.toIntOrNull()
            ?: DEFAULT_CAPTURE_MAX_LONG_SIDE)
            .takeIf { it > 0 }
        set(value) = put(KEY_CAPTURE_MAX_LONG_SIDE, (value ?: 0).toString())

    /** Grava a preferência no disco imediatamente (as escritas são raríssimas). */
    private fun put(key: String, value: String) {
        properties.setProperty(key, value)
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { output -> properties.store(output, HEADER) }
        }
    }

    private companion object {
        const val HEADER = "ArkZ ARModelViewer - preferencias do aplicativo"
        const val KEY_HELP_SHOWN = "help_shown"
        const val KEY_CAPTURE_MAX_LONG_SIDE = "capture_max_long_side"
        const val KEY_LANGUAGE_TAG = "language_tag"

        /** Mesma resolução usada pela captura antes de ela virar uma opção. */
        const val DEFAULT_CAPTURE_MAX_LONG_SIDE = 1920
    }
}
