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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes das métricas de ancoragem.
 *
 * Os números são conferidos à mão. A conta é curta, mas se ela errar o modelo
 * **aparece no lugar errado** — ou, no pior caso (milímetros em vez de metros),
 * centenas de metros fora da câmera. Foi exatamente o bug que o app Android
 * documenta em `ModelMetrics`.
 */
class ModelMetricsTest {

    private val metrics = ModelMetrics(
        center = Vec3(1f, 2f, 3f),
        halfExtent = Vec3(10f, 5f, 2f),
    )

    private companion object {
        /** Tolerância das comparações de `Float` (produtos de escala). */
        const val TOLERANCE = 1e-6f
    }

    @Test
    fun `a maior dimensao e a maior meia-extensao vezes dois`() {
        assertEquals(20f, metrics.largestDimension)
    }

    @Test
    fun `a normalizacao faz a maior dimensao medir um metro`() {
        assertEquals(0.05f, metrics.normalization)

        // Um modelo de 20 unidades com normalização 0,05 é exibido com 1 m de lado
        // quando o tamanho escolhido é 1 m.
        val scaleForOneMeter = metrics.normalization * 1f
        assertEquals(1f, metrics.largestDimension * scaleForOneMeter)
    }

    @Test
    fun `sem bounding box a normalizacao nao mexe no tamanho`() {
        val empty = ModelMetrics(Vec3.ZERO, Vec3.ZERO)
        assertEquals(0f, empty.largestDimension)
        assertEquals(1f, empty.normalization)
    }

    @Test
    fun `anchorPosition centraliza na largura, apoia a base na normal e centra na altura da imagem`() {
        val scale = metrics.normalization * 0.2f // 0,05 × 20 cm

        val position = metrics.anchorPosition(scale = scale, elevationMeters = 0f)

        // As comparações usam tolerância: a escala é um produto de `Float`
        // (0,05f × 0,2f), então o resultado tem um resíduo de ponto flutuante.
        //
        // Referencial do marcador (o do ARCore, e o que o `solvePnP` produz): X = largura,
        // **Y = normal** (sai do papel) e Z = altura NA imagem — decisão 34.
        assertEquals(-0.01f, position.x, TOLERANCE, "x = -centro.x × escala (largura)")
        assertEquals(-0.01f, position.y, TOLERANCE, "y apoia a base do arquivo (Z) no plano, no eixo da normal")
        assertEquals(-0.02f, position.z, TOLERANCE, "z centraliza a profundidade do arquivo (Y) na altura da imagem")
    }

    @Test
    fun `a elevacao desloca o modelo apenas no eixo da normal`() {
        val scale = 0.01f
        val base = metrics.anchorPosition(scale = scale, elevationMeters = 0f)

        // +50 cm afasta o modelo do plano do marcador: a normal é o **Y** do referencial do
        // marcador (X = largura, Y = normal, Z = altura NA imagem — decisão 34), e é o único
        // eixo em que o modelo sai do papel.
        val elevated = metrics.anchorPosition(scale = scale, elevationMeters = 0.5f)
        assertEquals(base.x, elevated.x)
        assertEquals(base.z, elevated.z)
        assertEquals(base.y + 0.5f, elevated.y, 0.0001f)

        val lowered = metrics.anchorPosition(scale = scale, elevationMeters = -0.5f)
        assertEquals(base.y - 0.5f, lowered.y, 0.0001f)
    }

    @Test
    fun `a ancoragem acompanha a escala atual`() {
        // Este é o coração da correção: o deslocamento é multiplicado pela escala
        // VIGENTE, e não congelado em unidades cruas do arquivo.
        val small = metrics.anchorPosition(scale = 0.01f, elevationMeters = 0f)
        val big = metrics.anchorPosition(scale = 1f, elevationMeters = 0f)

        assertEquals(-0.01f, small.x)
        assertEquals(-1f, big.x)
        assertTrue(big.x < small.x, "um modelo maior exige deslocamento maior para centralizar na largura")
    }
}
