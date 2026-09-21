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
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes da descrição da cena (`RenderScene`) e dos dados que atravessam a fronteira do
 * renderizador.
 *
 * Nada aqui precisa de GPU: é justamente o ponto da arquitetura — a decisão de "o que
 * desenhar e onde" é tomada em Kotlin puro e testável, e o renderizador só executa.
 */
class RenderSceneTest {

    /** Marcador rastreado a 50 cm à frente da câmera, virado para ela. */
    private fun markerOf(state: TrackingState = TrackingState.TRACKING) = DetectedMarker(
        index = 3,
        name = "marker_04",
        label = "Marcador 4",
        trackingState = state,
        trackingMethod = TrackingMethod.FULL_TRACKING,
        // Referencial do marcador como o ARCore o define: +X = largura da imagem,
        // +Y = normal (sai do plano) e +Z = altura NA imagem.
        centerPose = Pose(
            rotation = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
            translation = Vec3(0.01f, -0.02f, 0.5f),
        ),
        extentX = 0.15f,
        extentZ = 0.15f,
        corners = listOf(Vec2(-1f, -1f), Vec2(1f, -1f), Vec2(1f, 1f), Vec2(-1f, 1f)),
        inlierCount = 120,
    )

    /** Modelo de 1 m de lado na maior dimensão, apoiado no plano z = 0. */
    private fun modelOf(largestDimension: Float = 1f): PreparedModel {
        val half = largestDimension / 2f
        return PreparedModel(
            loadFile = File("modelo.glb"),
            bounds = ModelBounds(
                min = Vec3(-half, 0f, -half),
                max = Vec3(half, largestDimension, half),
            ),
            meshCount = 1,
            triangleCount = 12,
            converted = true,
        )
    }

    @Test
    fun `sem marcador rastreado nao ha matriz de mundo`() {
        val paused = RenderScene(marker = markerOf(TrackingState.PAUSED), model = modelOf())

        assertFalse(paused.isTracking)
        assertNull(paused.worldMatrix, "com o rastreio pausado o modelo não pode ser desenhado")
    }

    @Test
    fun `sem modelo carregado nao ha matriz de mundo`() {
        val scene = RenderScene(marker = markerOf(), model = null)

        assertTrue(scene.isTracking, "o marcador está rastreado, mas não há modelo para ancorar")
        assertNull(scene.worldMatrix)
        assertTrue(scene.isEmpty, "sem fundo e sem modelo, não há o que desenhar")
    }

    @Test
    fun `com marcador rastreado e modelo a matriz sai do ModelPlacement`() {
        val marker = markerOf()
        val model = modelOf()
        val scene = RenderScene(marker = marker, model = model, sizeMeters = 0.3f)

        val expected = ModelPlacement.worldMatrix(
            markerPose = marker.centerPose,
            metrics = model.bounds.toMetrics(),
            sizeMeters = 0.3f,
            rotationDegrees = Vec3.ZERO,
            elevationMeters = 0f,
        )

        val actual = assertNotNull(scene.worldMatrix)
        assertEquals(expected.toList(), actual.toList())
        assertFalse(scene.isEmpty)
    }

    @Test
    fun `os ajustes do painel mudam a matriz do modelo`() {
        val base = RenderScene(marker = markerOf(), model = modelOf())
        val bigger = RenderScene(marker = markerOf(), model = modelOf(), sizeMeters = 0.5f)
        val rotated = RenderScene(
            marker = markerOf(),
            model = modelOf(),
            rotationDegrees = Vec3(90f, 0f, 0f),
        )
        val raised = RenderScene(marker = markerOf(), model = modelOf(), elevationMeters = 0.1f)

        // Escala, giro e elevação têm de aparecer na matriz — é o que faz os sliders
        // funcionarem, e é a diferença que um renderizador ligado no lugar errado não
        // mostraria.
        val baseMatrix = assertNotNull(base.worldMatrix).toList()
        assertTrue(baseMatrix != assertNotNull(bigger.worldMatrix).toList(), "escala")
        assertTrue(baseMatrix != assertNotNull(rotated.worldMatrix).toList(), "rotação")
        assertTrue(baseMatrix != assertNotNull(raised.worldMatrix).toList(), "elevação")
        assertEquals(0.2f, RenderScene.DEFAULT_SIZE_METERS, "o padrão do Android é 0,2 m")
    }

    @Test
    fun `a proporcao do fundo vem das dimensoes do quadro`() {
        val frame = BackgroundFrame(ByteArray(640 * 480 * 4), width = 640, height = 480)

        assertEquals(640 * 4, frame.rowBytes)
        assertEquals(640f / 480f, frame.aspectRatio, 1e-6f)
    }

    @Test
    fun `um quadro de fundo recusa pixels de menos`() {
        val failure = runCatching { BackgroundFrame(ByteArray(10), width = 4, height = 4) }

        assertTrue(failure.isFailure, "meia imagem na tela é pior do que uma exceção")
    }

    @Test
    fun `inverter as linhas duas vezes devolve o quadro original`() {
        val frame = checkeredFrame(width = 3, height = 2)

        val roundTrip = frame.flippedVertically().flippedVertically()

        assertTrue(frame.rgba.contentEquals(roundTrip.rgba), "ida e volta tem de ser neutra")
    }

    @Test
    fun `a inversao troca a primeira linha pela ultima`() {
        val frame = checkeredFrame(width = 3, height = 2)

        val flipped = frame.flippedVertically()

        // O `readPixels` do Filament devolve de baixo para cima; o Compose desenha de cima
        // para baixo. Este teste é o que garante que o quadro não sai de cabeça para baixo.
        val topRow = flipped.rgba.copyOfRange(0, frame.rowBytes)
        val originalBottomRow = frame.rgba.copyOfRange(frame.rowBytes, 2 * frame.rowBytes)
        assertTrue(topRow.contentEquals(originalBottomRow))
    }

    /** Quadro com valores distintos em cada linha, para a inversão ser verificável. */
    private fun checkeredFrame(width: Int, height: Int): SceneFrame {
        val rgba = ByteArray(width * height * 4) { index -> (index % 251).toByte() }
        return SceneFrame(rgba, width, height)
    }
}
