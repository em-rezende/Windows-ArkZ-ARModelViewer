/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.render

import com.arkz.armodelviewer.ar.DetectedMarker
import com.arkz.armodelviewer.ar.Pose
import com.arkz.armodelviewer.ar.TrackingMethod
import com.arkz.armodelviewer.ar.TrackingState
import com.arkz.armodelviewer.ar.Vec2
import com.arkz.armodelviewer.model.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Testes da escala automática (o modelo passa a medir a largura real do marcador). */
class AutoScaleTest {

    private fun markerWithWidth(width: Float) = DetectedMarker(
        index = 1,
        name = "marker_a",
        label = "Marcador A",
        trackingState = TrackingState.TRACKING,
        trackingMethod = TrackingMethod.FULL_TRACKING,
        centerPose = Pose(
            rotation = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 1.0, 0.0),
            translation = Vec3(0f, 0f, -0.5f),
        ),
        extentX = width,
        extentZ = width,
        corners = listOf(Vec2(-1f, -1f), Vec2(1f, -1f), Vec2(1f, 1f), Vec2(-1f, 1f)),
        inlierCount = 100,
    )

    @Test
    fun `o tamanho passa a ser a largura real do marcador`() {
        // 15 cm: a largura com que os marcadores do app são impressos.
        assertEquals(0.15f, AutoScale.sizeMetersFor(markerWithWidth(0.15f)))
    }

    @Test
    fun `um marcador impresso em outro tamanho tambem vale`() {
        assertEquals(0.3f, AutoScale.sizeMetersFor(markerWithWidth(0.3f)))
    }

    @Test
    fun `sem marcador nao ha tamanho a aplicar`() {
        assertNull(AutoScale.sizeMetersFor(null), "sem marcador o painel mantém o tamanho atual")
    }

    @Test
    fun `larguras fora do razoavel sao limitadas a faixa do painel`() {
        // Um marcador de 5 m (detecção ruim ou leitura absurda) não pode jogar o modelo
        // para fora da faixa que o slider sabe mostrar.
        assertEquals(RenderScene.MAX_SIZE_METERS, AutoScale.sizeMetersFor(markerWithWidth(5f)))
        assertEquals(RenderScene.MIN_SIZE_METERS, AutoScale.sizeMetersFor(markerWithWidth(0.001f)))
        assertNull(AutoScale.sizeMetersFor(markerWithWidth(0f)), "largura zerada não é medida")
        assertNull(
            AutoScale.sizeMetersFor(markerWithWidth(Float.NaN)),
            "largura inválida não é medida",
        )
    }
}
