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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes das informações do app.
 *
 * O valor real aqui é o primeiro teste: ele prova que a **tarefa do Gradle**
 * (`generateAppProperties`) roda e que o `version.properties` chega ao classpath
 * em runtime. Sem isso, o menu "Sobre" mostraria "desconhecida" — e ninguém
 * descobriria até abrir o app.
 *
 * O nome é comparado com o `rootProject.name` do `settings.gradle.kts`: os dois
 * lados têm de continuar iguais.
 */
class AppInfoTest {

    @Test
    fun `o nome do app vem do version_properties gerado pelo Gradle`() {
        assertEquals("ArkZ ARModelViewer Desktop", appName())
    }

    @Test
    fun `a versao tem o formato de versao de release`() {
        val version = appVersionLabel()
        assertTrue(
            version.matches(Regex("\\d+\\.\\d+\\.\\d+")),
            "versão inesperada: '$version' — confira `appVersion` no build.gradle.kts",
        )
    }

    @Test
    fun `o diagnostico descreve o computador e o Java em uso`() {
        val label = deviceLabel()
        assertTrue(label.contains(System.getProperty("os.name")), "sem o nome do sistema: '$label'")
        assertTrue(label.contains(System.getProperty("os.arch")), "sem a arquitetura: '$label'")
        assertTrue(label.contains("Java"), "sem a versão do Java: '$label'")
    }

    @Test
    fun `os enderecos do projeto apontam para o repositorio e o site`() {
        assertTrue(DEVELOPER_SITE_URL.startsWith("https://"))
        assertTrue(DEVELOPER_EMAIL.contains("@"))
        assertTrue(PROJECT_URL.contains("Windows-ArkZ-ARModelViewer"))
        assertTrue(ANDROID_PROJECT_URL.contains("Android-ArkZ-ARModelViewer"))
    }
}
