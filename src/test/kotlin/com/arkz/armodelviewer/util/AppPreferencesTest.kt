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

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes das preferências do app.
 *
 * Duas coisas importam: os **valores padrão** (a primeira execução precisa abrir
 * sem Ajuda automática e com a captura em 1920, como no Android) e a
 * **persistência** (o idioma escolhido tem de sobreviver ao reinício). O teste com
 * arquivo corrompido existe porque essa é a única falha que deixaria o usuário sem
 * app: um arquivo ilegível não pode impedir a abertura.
 */
class AppPreferencesTest {

    @TempDir
    lateinit var tempDir: Path

    private fun preferences() = AppPreferences(tempDir.resolve("preferences.properties").toFile())

    @Test
    fun `os valores padrao sao os mesmos do app Android`() {
        val preferences = preferences()

        assertFalse(preferences.helpShown, "a Ajuda deve aparecer na primeira execução")
        assertNull(preferences.languageTag, "o padrão é seguir o idioma do sistema")
        assertEquals(1920, preferences.captureMaxLongSide)
    }

    @Test
    fun `o que foi gravado sobrevive a uma nova instancia`() {
        preferences().apply {
            helpShown = true
            languageTag = "zh-CN"
            captureMaxLongSide = null
        }

        val reloaded = preferences()
        assertTrue(reloaded.helpShown)
        assertEquals("zh-CN", reloaded.languageTag)
        assertNull(reloaded.captureMaxLongSide, "sem limite é guardado como 0 e lido como null")
    }

    @Test
    fun `o arquivo e texto legivel com as chaves do Android`() {
        preferences().apply {
            languageTag = "en"
            captureMaxLongSide = 1280
        }

        val text = tempDir.resolve("preferences.properties").toFile().readText(Charsets.UTF_8)

        assertTrue(text.contains("language_tag=en"), "conteúdo: $text")
        assertTrue(text.contains("capture_max_long_side=1280"), "conteúdo: $text")
    }

    @Test
    fun `voltar para o idioma do sistema limpa a escolha`() {
        preferences().languageTag = "fr"

        preferences().languageTag = null

        assertNull(preferences().languageTag)
    }

    @Test
    fun `arquivo corrompido nao impede o app de abrir`() {
        tempDir.resolve("preferences.properties").toFile().writeText("\u0000lixo\u0000", Charsets.UTF_8)

        val preferences = preferences()

        assertFalse(preferences.helpShown)
        assertNull(preferences.languageTag)
    }
}
