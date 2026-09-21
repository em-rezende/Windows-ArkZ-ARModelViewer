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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Testes da resolução de idioma.
 *
 * Este é o ponto que **mais** mudou de plataforma no app Android: sem o controle
 * de `ResourceBundle` sem fallback, um idioma não traduzido cai no bundle do
 * idioma do SISTEMA (que pode ser qualquer coisa) em vez do bundle padrão. Foi
 * exatamente isso que fez o app Android aparecer em português de Portugal num
 * aparelho brasileiro.
 */
class AppLocaleTest {

    @Test
    fun `os idiomas oferecidos no menu sao unicos e completos`() {
        val tags = appLanguages.map { it.tag }

        assertEquals(9, appLanguages.size, "8 idiomas + a opção Sistema")
        assertEquals(tags.size, tags.toSet().size, "há códigos repetidos: $tags")
        assertEquals(null, tags.first(), "Sistema é a primeira opção")
    }

    @Test
    fun `cada idioma oferecido tem o seu rotulo no bundle`() {
        appLanguages.forEach { language ->
            val label = stringsFor(language.tag)[language.labelKey]

            assertTrue(label.isNotBlank(), "rótulo vazio para ${language.tag}")
        }
    }

    @Test
    fun `o idioma padrao da lista e o pt-BR com os textos do Android`() {
        val ptBr = stringsFor("pt-BR")

        assertEquals("pt-BR", ptBr.locale.toLanguageTag())
        assertTrue(
            ptBr["marker_all"].contains("marcadores", ignoreCase = true),
            "esperado o texto do app Android em pt-BR, veio: '${ptBr["marker_all"]}'",
        )
    }

    @Test
    fun `idiomas diferentes devolvem textos diferentes`() {
        val ptBr = stringsFor("pt-BR")["action_reset"]
        val en = stringsFor("en")["action_reset"]

        assertNotEquals(ptBr, en, "o bundle en não pode devolver o texto do pt-BR")
        assertTrue(en.isNotBlank())
    }

    @Test
    fun `um codigo desconhecido cai no bundle padrao do projeto`() {
        // Não existe strings_xx_YY.properties e o idioma do sistema não pode ser
        // usado como fallback: o resultado é o bundle padrão (pt-BR), como o
        // `values/` do Android.
        val unknown = stringsFor("xx-YY")

        assertEquals(stringsFor("pt-BR")["action_reset"], unknown["action_reset"])
    }

    @Test
    fun `um idioma presente no sistema mas sem traducao cai no padrao`() {
        // `ja` não tem tradução no projeto; se o Windows estiver em japonês, o app
        // continua no bundle padrão em vez de mostrar chaves cruas.
        assertEquals(stringsFor("pt-BR")["menu_help"], stringsFor("ja")["menu_help"])
    }

    @Test
    fun `format aplica os argumentos posicionais do Android`() {
        val strings = stringsFor("pt-BR")

        assertTrue(strings.format("about_version", "1.0.0").contains("1.0.0"))
        assertTrue(strings.format("diagnostics_markers", 3).contains("3"))
    }

    @Test
    fun `as chaves sao as mesmas do bundle padrao para qualquer idioma`() {
        val expected = stringsFor("pt-BR").keys

        appLanguages.mapNotNull { it.tag }.forEach { tag ->
            assertEquals(expected, stringsFor(tag).keys, "conjunto de chaves diferente em $tag")
        }
    }

    @Test
    fun `o Sobre mostra o direito autoral, o desenvolvedor, o site, o e-mail e a licenca`() {
        // Não é enfeite: a seção 5 da GPL-3.0 pede o aviso de direito autoral e de licença
        // numa interface interativa ("about box"). Este teste é a garantia de que o aviso
        // não sai da tela numa refatoração do diálogo.
        val texto = aboutLines(stringsFor("pt-BR")).joinToString("\n")

        assertTrue(texto.contains("Copyright (C) 2026 Ark-Z Arquitetura Ltda"), "sem o direito autoral")
        assertTrue(texto.contains("Ezequiel M. Rezende"), "sem o desenvolvedor")
        assertTrue(texto.contains("https://em-rezende.github.io/"), "sem o site")
        assertTrue(texto.contains("emrezende@gmail.com"), "sem o e-mail")
        assertTrue(
            texto.contains("GNU General Public License v3.0"),
            "sem o aviso de licença — a GPL-3.0 pede o aviso na própria interface",
        )
    }

    @Test
    fun `a linha da licenca muda de idioma e os dados de credito nao`() {
        val ptBr = aboutLines(stringsFor("pt-BR"))
        val en = aboutLines(stringsFor("en"))

        assertEquals(ptBr.size, en.size, "o Sobre tem o mesmo número de linhas em qualquer idioma")
        // A licença é traduzida e o rótulo do desenvolvedor acompanha o idioma...
        assertNotEquals(ptBr.last(), en.last(), "a licença deveria estar traduzida no bundle en")
        assertNotEquals(ptBr[3], en[3], "o rótulo do desenvolvedor deveria acompanhar o idioma")
        // ...mas os DADOS (nome, site e e-mail) nunca: são nomes próprios e contato.
        assertTrue(
            ptBr[3].endsWith("Ezequiel M. Rezende") && en[3].endsWith("Ezequiel M. Rezende"),
            "o nome do desenvolvedor não deve ser traduzido: '${ptBr[3]}' / '${en[3]}'",
        )
        assertEquals(ptBr[2], en[2], "o aviso de direito autoral não deve ser traduzido")
        assertEquals(ptBr[4], en[4], "o site não deve ser traduzido")
        assertEquals(ptBr[5], en[5], "o e-mail não deve ser traduzido")
    }

    @Test
    fun `a chave do rotulo do idioma escolhido acompanha a escolha`() {
        assertEquals("language_zh", appLanguageLabelKey("zh-CN"))
        assertEquals("language_system", appLanguageLabelKey(null))
        assertEquals("language_system", appLanguageLabelKey("xx-YY"))
    }
}
