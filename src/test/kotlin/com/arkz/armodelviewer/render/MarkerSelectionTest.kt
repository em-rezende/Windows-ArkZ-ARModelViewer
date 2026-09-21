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

/**
 * Testes da escolha de marcador.
 *
 * É uma regra de quatro linhas, e é justamente por isso que vale testá-la: em cima dela
 * está a decisão que o usuário toma na interface ("todos os marcadores" ou um específico),
 * e um erro aqui aparece como um sintoma confuso — "o modelo está na figura errada" ou "o
 * modelo sumiu quando escolhi o marcador".
 */
class MarkerSelectionTest {

    private fun marker(
        name: String,
        state: TrackingState,
        extentX: Float = 0.15f,
    ) = DetectedMarker(
        index = 1,
        name = name,
        label = name,
        trackingState = state,
        trackingMethod = TrackingMethod.FULL_TRACKING,
        centerPose = Pose(
            rotation = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 1.0, 0.0),
            translation = Vec3(0f, 0f, -0.5f),
        ),
        extentX = extentX,
        extentZ = extentX,
        corners = listOf(Vec2(-1f, -1f), Vec2(1f, -1f), Vec2(1f, 1f), Vec2(-1f, 1f)),
        inlierCount = 100,
    )

    @Test
    fun `qualquer marcador aceita o primeiro rastreado`() {
        val markers = listOf(
            marker("marker_b", TrackingState.TRACKING),
            marker("marker_a", TrackingState.TRACKING),
        )

        assertEquals("marker_b", MarkerSelection.pick(markers, MarkerSelection.ALL_MARKERS_ID)?.name)
        assertEquals("marker_b", MarkerSelection.pick(markers, "")?.name, "vazio é o mesmo que qualquer um")
    }

    @Test
    fun `um marcador escolhido recebe o modelo mesmo nao sendo o primeiro`() {
        val markers = listOf(
            marker("marker_b", TrackingState.TRACKING),
            marker("marker_a", TrackingState.TRACKING),
        )

        assertEquals("marker_a", MarkerSelection.pick(markers, "marker_a")?.name)
    }

    @Test
    fun `marcador escolhido mas pausado nao recebe o modelo`() {
        val markers = listOf(marker("marker_a", TrackingState.PAUSED))

        assertNull(
            MarkerSelection.pick(markers, "marker_a"),
            "rastreio pausado não ancora o modelo — é a regra do RenderScene",
        )
    }

    @Test
    fun `sem marcador rastreado nao ha escolha`() {
        val markers = listOf(
            marker("marker_a", TrackingState.PAUSED),
            marker("marker_b", TrackingState.STOPPED),
        )

        assertNull(MarkerSelection.pick(markers, MarkerSelection.ALL_MARKERS_ID))
        assertNull(MarkerSelection.pick(markers, "marker_a"))
    }

    @Test
    fun `marcador escolhido que nao esta no quadro nao ancora`() {
        val markers = listOf(marker("marker_a", TrackingState.TRACKING))

        assertNull(
            MarkerSelection.pick(markers, "marker_zz"),
            "escolher um marcador ausente não pode cair no que está na frente",
        )
    }
}
