/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.i18n

import java.util.Locale
import java.util.ResourceBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes dos bundles de idioma (os arquivos `.properties` de
 * `src/main/resources/i18n/`).
 *
 * Eles protegem as decisões que o app Android não tinha e que são fáceis de
 * quebrar sem ninguém notar:
 *
 *  1. **paridade de chaves** — o Android tem 110 chaves no pt-BR e 97 nos outros
 *     idiomas (13 caem no padrão, em português). Aqui todos os idiomas têm as
 *     mesmas chaves;
 *  2. **UTF-8 preservado** — o pipeline de geração (PowerShell → `.properties`)
 *     pode corromper acentos e ideogramas sem falhar o build (foi o que
 *     aconteceu enquanto os scripts não tinham BOM);
 *  3. **marcadores de formatação iguais** — uma tradução que troca `%1$s` por
 *     `%1s` ou esquece o argumento faz o app estourar em runtime.
 */
class I18nBundlesTest {

    /** Idiomas oferecidos no menu ⋮ → Idioma (sem o "Sistema", que é dinâmico). */
    private val locales = listOf(
        Locale("pt", "BR"),
        Locale("pt", "PT"),
        Locale("en"),
        Locale("es"),
        Locale("fr"),
        Locale("de"),
        Locale("it"),
        Locale("zh", "CN"),
    )

    /**
     * `getNoFallbackControl` é essencial: sem ele, um idioma sem tradução cairia
     * no bundle do idioma do SISTEMA (poderia ser qualquer coisa) em vez do
     * bundle padrão do projeto, que é o pt-BR — exatamente como o `values/` do
     * Android.
     */
    private val control: ResourceBundle.Control =
        ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)

    private fun bundle(locale: Locale): ResourceBundle =
        ResourceBundle.getBundle("i18n/strings", locale, control)

    @Test
    fun `todos os idiomas tem exatamente o mesmo conjunto de chaves`() {
        val baseKeys = bundle(Locale.ROOT).keys.toList().sorted()

        // Os 110 do app Android + 6 desta versão (`camera_title`, `camera_status_running`,
        // `section_model`, `action_fullscreen`, `status_auto_scale_applied` e `about_license`:
        // o Android não tinha escolha de dispositivo, nem os menus organizados desta forma, nem
        // tela cheia, nem o aviso de escala aplicada sobre a imagem, nem o aviso de licença no
        // diálogo Sobre — que a GPL-3.0 pede numa interface interativa).
        assertEquals(
            116,
            baseKeys.size,
            "o bundle padrão (pt-BR) tem ${baseKeys.size} chaves; ao acrescentar uma chave, " +
                "acrescente-a também nos outros 7 bundles e atualize este número",
        )

        locales.forEach { locale ->
            val keys = bundle(locale).keys.toList().sorted()
            val missing = baseKeys - keys
            val extra = keys - baseKeys
            assertTrue(
                missing.isEmpty() && extra.isEmpty(),
                "$locale fora de paridade — faltando: $missing | sobrando: $extra",
            )
        }
    }

    @Test
    fun `nenhum valor fica vazio em nenhum idioma`() {
        (listOf(Locale.ROOT) + locales).forEach { locale ->
            val resource = bundle(locale)
            resource.keys.toList().forEach { key ->
                assertTrue(
                    resource.getString(key).isNotBlank(),
                    "valor vazio em $locale para a chave '$key'",
                )
            }
        }
    }

    @Test
    fun `os marcadores de formatacao coincidem com os do padrao`() {
        val base = bundle(Locale.ROOT)
        locales.forEach { locale ->
            val resource = bundle(locale)
            base.keys.toList().forEach { key ->
                assertEquals(
                    placeholdersOf(base.getString(key)),
                    placeholdersOf(resource.getString(key)),
                    "marcadores diferentes em $locale para '$key'",
                )
            }
        }
    }

    @Test
    fun `o bundle chines preserva os caracteres multibyte`() {
        val value = bundle(Locale("zh", "CN")).getString("action_reset")
        assertTrue(
            value.any { it.code > 0x7F },
            "esperado texto com caracteres multibyte em zh-CN, veio: '$value' — " +
                "pipeline de i18n perdendo a codificação UTF-8?",
        )
    }

    @Test
    fun `os acentos do portugues sobrevivem a geracao`() {
        val value = bundle(Locale.ROOT).getString("adjust_rotation_x")
        assertFalse(value.contains("Ã"), "acento corrompido (mojibake) em '$value'")
        assertTrue(
            value.contains("ã") || value.contains("ç") || value.contains("é"),
            "esperado acento em '$value'",
        )
    }

    @Test
    fun `pt-BR cai no bundle padrao e nao no idioma do sistema`() {
        // Não existe strings_pt_BR.properties: o pt-BR É o bundle padrão.
        assertEquals(
            bundle(Locale.ROOT).getString("action_reset"),
            bundle(Locale("pt", "BR")).getString("action_reset"),
        )
    }

    @Test
    fun `os textos do Windows substituem os do Android em todos os idiomas`() {
        (listOf(Locale.ROOT) + locales).forEach { locale ->
            val resource = bundle(locale)
            assertFalse(
                resource.getString("menu_rate_play").contains("Play"),
                "o item de menu ainda fala da Play Store em $locale: " +
                    "'${resource.getString("menu_rate_play")}'",
            )
            assertFalse(
                resource.getString("permission_rationale").contains("ARCore"),
                "o aviso de câmera ainda fala do ARCore em $locale: " +
                    "'${resource.getString("permission_rationale")}'",
            )
            assertTrue(
                resource.getString("about_description").contains("Filament"),
                "o Sobre deve citar o renderizador usado no Windows ($locale)",
            )
            assertTrue(
                resource.getString("permission_open_settings").isNotBlank() &&
                    resource.getString("permission_title").isNotBlank(),
                "as mensagens de câmera precisam existir em $locale",
            )
        }
    }

    @Test
    fun `a ajuda menciona o log do Windows e nao o Logcat`() {
        val help = bundle(Locale.ROOT).getString("help_body")
        assertTrue(help.contains("LOCALAPPDATA"), "a Ajuda deve indicar onde fica o log do app")
        assertFalse(help.contains("adb logcat"), "a Ajuda não deve mandar o usuário usar o Logcat")
        assertTrue(help.contains("15 cm"), "a Ajuda deve manter a instrução de impressão em 15 cm")
    }

    /** Marcadores de posição (`%1$s`, `%2$d`...) de um texto, em ordem. */
    private fun placeholdersOf(value: String): List<String> =
        Regex("%\\d+\\\$[sd]").findAll(value).map { it.value }.sorted().toList()
}
