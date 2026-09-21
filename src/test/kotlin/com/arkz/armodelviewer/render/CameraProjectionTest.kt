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

import com.arkz.armodelviewer.ar.CameraIntrinsics
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes da projeção da câmera de RA.
 *
 * O teste que importa é o da **concordância com o modelo pinhole da detecção**: o mesmo
 * ponto 3D projetado pela matriz tem de cair no mesmo pixel que a conta
 * `u = fx·X/Z + cx` do `solvePnP`. Se essa igualdade quebrar, o modelo aparece deslocado
 * em relação ao marcador — um defeito que só se vê na tela, e que aqui se vê na hora.
 */
class CameraProjectionTest {

    /** Intrínsecos 1280×720 com 60° horizontais (o padrão do app). */
    private val intrinsics = CameraIntrinsics.forFrame(width = 1280, height = 720)

    @Test
    fun `a projecao concorda com o modelo pinhole da deteccao`() {
        val (width, height) = 1280 to 720
        val matrix = CameraProjection.fromIntrinsics(intrinsics, width, height)

        // Pontos no referencial da câmera: à frente (z negativo), espalhados pelo quadro
        // inclusive nos cantos, onde um campo de visão aproximado mais erraria.
        val points = listOf(
            Triple(0.0, 0.0, -0.5),
            Triple(0.1, 0.05, -0.5),
            Triple(-0.15, 0.08, -0.4),
            Triple(0.19, -0.1, -0.6),
            Triple(-0.19, -0.1, -0.6),
        )

        points.forEach { (x, y, z) ->
            val pixels = assertNotNull(
                CameraProjection.projectToPixels(matrix, x, y, z, width, height),
                "ponto à frente tem de projetar",
            )

            // O modelo pinhole: profundidade positiva, Y para baixo NA IMAGEM (é o sinal
            // que o `Pose` já aplicou ao converter os eixos da câmera).
            val depth = -z
            val expectedU = intrinsics.focalLengthX * (x / depth) + intrinsics.centerX
            val expectedV = -intrinsics.focalLengthY * (y / depth) + intrinsics.centerY

            assertEquals(expectedU, pixels.first, 0.5, "u do ponto ($x, $y, $z)")
            assertEquals(expectedV, pixels.second, 0.5, "v do ponto ($x, $y, $z)")
        }
    }

    @Test
    fun `o centro da imagem projeta no ponto principal`() {
        val matrix = CameraProjection.fromIntrinsics(intrinsics, 1280, 720)

        val center = assertNotNull(
            CameraProjection.projectToPixels(matrix, 0.0, 0.0, -0.5, 1280, 720),
        )

        assertEquals(640.0, center.first, 0.5)
        assertEquals(360.0, center.second, 0.5)
    }

    @Test
    fun `a matriz acompanha o tamanho do quadro`() {
        // As intrínsecas são de 1280×720, mas a janela foi redimensionada: a projeção
        // tem de valer para o quadro novo, com as focais escaladas na mesma proporção.
        val matrix = CameraProjection.fromIntrinsics(intrinsics, 640, 360)

        // `2·fx/largura` é exatamente `1/tan(fov/2)` — e o campo de visão VERTICAL é
        // menor que o horizontal porque a imagem é 16:9 com pixels quadrados.
        val expectedHorizontal = 1.0 / tan(
            kotlin.math.atan((intrinsics.width / 2.0) / intrinsics.focalLengthX),
        )
        val expectedVertical = 1.0 / tan(
            kotlin.math.atan((intrinsics.height / 2.0) / intrinsics.focalLengthY),
        )

        assertEquals(expectedHorizontal, matrix[0], 1e-9, "2·fx/largura")
        assertEquals(expectedVertical, matrix[5], 1e-9, "2·fy/altura")
        assertEquals(0.0, matrix[8], 1e-9, "ponto principal centralizado")
        assertEquals(0.0, matrix[9], 1e-9, "ponto principal centralizado")
    }

    @Test
    fun `um ponto atras da camera nao projeta`() {
        val matrix = CameraProjection.fromIntrinsics(intrinsics, 1280, 720)

        assertNull(CameraProjection.projectToPixels(matrix, 0.0, 0.0, 0.5, 1280, 720))
    }

    @Test
    fun `o plano de fundo cobre o quadro inteiro na distancia escolhida`() {
        val (width, height) = 1280 to 720
        val distance = 50.0

        val (planeWidth, planeHeight) = CameraProjection.frameSizeAt(
            intrinsics = intrinsics,
            width = width,
            height = height,
            distanceMeters = distance,
        )

        // As bordas do plano têm de cair exatamente nas bordas do quadro: se cair para
        // dentro sobra faixa preta; se cair para fora, o vídeo é cortado.
        val matrix = CameraProjection.fromIntrinsics(intrinsics, width, height)
        val rightEdge = assertNotNull(
            CameraProjection.projectToPixels(
                matrix, planeWidth / 2.0, planeHeight / 2.0, -distance, width, height,
            ),
        )
        assertEquals(width.toDouble(), rightEdge.first, 0.5)
        assertEquals(0.0, rightEdge.second, 0.5)
    }

    @Test
    fun `os planos de recorte recusam valores impossiveis`() {
        val failure = runCatching {
            CameraProjection.fromIntrinsics(intrinsics, 1280, 720, nearMeters = 10.0, farMeters = 1.0)
        }

        assertTrue(failure.isFailure)
    }
}
