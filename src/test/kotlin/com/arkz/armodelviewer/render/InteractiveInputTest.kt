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

import com.arkz.armodelviewer.ar.Pose
import com.arkz.armodelviewer.model.ModelMetrics
import com.arkz.armodelviewer.model.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes da conversão de gestos em valores da cena.
 *
 * É a parte da etapa 6 que dá para conferir sem tela — e é justamente onde o erro aparece como
 * "o zoom não para de crescer", "arrasto para cima move para baixo" ou "o arrasto vertical sobe
 * e desce o modelo em vez de levá-lo para a frente e para trás" (o relato da 1.0.6, medido no
 * teste `o arrasto vertical leva o modelo para a frente e para tras…`). A montagem dos gestos na
 * interface (roda, teclado, arrasto, pinça) fica com a conferência de uso.
 */
class InteractiveInputTest {

    @Test
    fun `um passo de zoom aumenta o tamanho em dez por cento`() {
        val zoomed = InteractiveInput.zoomedSize(currentMeters = 0.2f, steps = 1f)

        assertEquals(0.2f * InteractiveInput.ZOOM_STEP, zoomed, 1e-6f)
    }

    @Test
    fun `aproximar e afastar voltam ao mesmo tamanho`() {
        val original = 0.2f

        val there = InteractiveInput.zoomedSize(original, steps = 3f)
        val back = InteractiveInput.zoomedSize(there, steps = -3f)

        assertEquals(original, back, 1e-5f, "o zoom é multiplicativo: ida e volta se cancelam")
    }

    @Test
    fun `o zoom respeita a faixa do painel`() {
        // Muitos passos para os dois lados: o tamanho não pode sair da faixa que os sliders
        // e a escala automática usam.
        val maior = InteractiveInput.zoomedSize(0.2f, steps = 100f)
        val menor = InteractiveInput.zoomedSize(0.2f, steps = -100f)

        assertEquals(RenderScene.MAX_SIZE_METERS, maior)
        assertEquals(RenderScene.MIN_SIZE_METERS, menor)
    }

    @Test
    fun `a faixa inteira e alcancavel em poucos passos`() {
        // De 2 cm a 2 m: cem vezes. A 10% por passo, são ~49 passos — a conferir que a
        // sensibilidade é utilizável e não uma eternidade de roda.
        var size = RenderScene.MIN_SIZE_METERS
        var steps = 0
        while (size < RenderScene.MAX_SIZE_METERS && steps < 200) {
            size = InteractiveInput.zoomedSize(size, steps = 1f)
            steps++
        }

        assertTrue(steps in 40..60, "passos para atravessar a faixa: $steps")
    }

    @Test
    fun `o fator da pinca vira passos de zoom`() {
        // Um passo exato (10%) é um passo; afastar os dedos 10% é a mesma coisa da roda.
        assertEquals(1f, InteractiveInput.stepsFor(InteractiveInput.ZOOM_STEP), 1e-5f)
        assertEquals(-1f, InteractiveInput.stepsFor(1f / InteractiveInput.ZOOM_STEP), 1e-5f)
        assertEquals(0f, InteractiveInput.stepsFor(1f), 1e-5f, "sem movimento, sem zoom")
        assertEquals(0f, InteractiveInput.stepsFor(0f), 1e-5f, "fator inválido é ignorado")
    }

    @Test
    fun `a pinca e a roda chegam ao mesmo tamanho`() {
        val original = 0.3f
        val fator = 1.25f

        val pelaPinca = InteractiveInput.zoomedSize(
            original,
            InteractiveInput.stepsFor(fator),
        )
        val pelaRoda = original * fator

        assertEquals(pelaRoda, pelaPinca, 1e-5f, "o gesto e a roda têm de dar o mesmo resultado")
    }

    @Test
    fun `arrastar para a direita move o modelo para a direita na largura da figura`() {
        val offset = InteractiveInput.panOffset(
            current = Vec3.ZERO,
            dxPixels = 100f,
            dyPixels = 0f,
            sizeMeters = 0.5f,
        )

        // 100 px com um modelo de 50 cm: 100 × 0,5 × 0,002 = 10 cm.
        assertEquals(0.1f, offset.x, 1e-6f, "positivo = para a direita")
        assertEquals(0f, offset.y, 1e-6f, "um arrasto horizontal não anda no frente–trás")
        assertEquals(0f, offset.z, 1e-6f, "nem na altura NA imagem")
    }

    @Test
    fun `o arrasto vertical anda no frente-tras, e nao na altura da imagem`() {
        // O arrasto vertical anda no **Y do marcador**, que é a NORMAL — o frente–trás, o eixo
        // que sai do papel na direção de quem olha. O Z (a "altura NA imagem") fica intocado: era
        // ele que fazia o arrasto subir e descer o modelo pelo mundo (decisão 36).
        //
        // O sinal é o da tela, como no resto do aplicativo: arrastar para BAIXO **traz** o modelo
        // para a frente (na direção de quem olha), e para cima o afasta — o mesmo sentido do
        // cursor de Elevação.
        val paraBaixo = InteractiveInput.panOffset(
            current = Vec3.ZERO,
            dxPixels = 0f,
            dyPixels = 100f, // arrasto para baixo
            sizeMeters = 0.5f,
        )
        val paraCima = InteractiveInput.panOffset(
            current = Vec3.ZERO,
            dxPixels = 0f,
            dyPixels = -100f, // arrasto para cima
            sizeMeters = 0.5f,
        )

        assertEquals(0.1f, paraBaixo.y, 1e-6f, "para baixo = para a frente (10 cm)")
        assertEquals(-0.1f, paraCima.y, 1e-6f, "para cima = para trás")
        assertEquals(0f, paraBaixo.x, 1e-6f)
        assertEquals(0f, paraCima.x, 1e-6f)
        assertEquals(0f, paraBaixo.z, 1e-6f, "o arrasto não mexe na altura NA imagem")
        assertEquals(0f, paraCima.z, 1e-6f, "o arrasto não mexe na altura NA imagem")
    }

    @Test
    fun `o arrasto vertical leva o modelo para a frente e para tras, e nao para cima e para baixo`() {
        // A conta que a correção pediu, medida com a pose de um caso real: o "Marcador A"
        // impresso **de frente para o usuário**, a 21 cm da câmera — é o caso do relato em campo
        // ("impresso e virado para ele"). A pose está em [FRENTE_POSE], medida pelo detector de
        // verdade a partir de um quadro sintetizado (`MarkerDetectorTest` refaz essa medição).
        //
        // Nesta pose, o Y do marcador é a **normal** (aqui, quase o eixo da visão) e o Z é a
        // altura NA imagem — que aponta para o **chão do mundo**. Escrever o arrasto vertical no Z
        // (o que o aplicativo fazia até a 1.0.5) andava 92 mm na vertical do mundo e só 38 mm em
        // profundidade: era esse o "o modelo sobe e desce" do relato. Escrito no Y, os números se
        // invertem — 92 mm para a frente, 38 mm na vertical.
        val metrics = ModelMetrics(center = Vec3(0f, 10f, 0f), halfExtent = Vec3(10f, 10f, 5f))

        /** Deslocamento do centro da base do modelo, no mundo, para um arrasto de [pixels]. */
        fun deslocamento(pixels: Float): Vec3 {
            val base = Vec3(
                metrics.center.x,
                metrics.center.y - metrics.halfExtent.y,
                metrics.center.z,
            )
            val semArrasto = ModelPlacement.worldMatrix(
                markerPose = FRENTE_POSE,
                metrics = metrics,
                sizeMeters = 0.5f,
                rotationDegrees = RenderScene.DEFAULT_ROTATION_DEGREES,
                elevationMeters = 0f,
            )
            val comArrasto = ModelPlacement.worldMatrix(
                markerPose = FRENTE_POSE,
                metrics = metrics,
                sizeMeters = 0.5f,
                rotationDegrees = RenderScene.DEFAULT_ROTATION_DEGREES,
                elevationMeters = 0f,
                offsetMeters = InteractiveInput.panOffset(
                    current = Vec3.ZERO,
                    dxPixels = 0f,
                    dyPixels = pixels,
                    sizeMeters = 0.5f,
                ),
            )

            val antes = ModelPlacement.apply(semArrasto, base)
            val depois = ModelPlacement.apply(comArrasto, base)
            return Vec3(depois.x - antes.x, depois.y - antes.y, depois.z - antes.z)
        }

        val delta = deslocamento(100f)

        // A câmera olha para -Z, então "para a frente" é o Z do mundo crescendo (o modelo se
        // aproxima). O arrasto de 100 px num modelo de 50 cm vale 10 cm.
        assertTrue(
            delta.z > 0.08f,
            "arrastar para baixo deveria trazer o modelo para a frente: dz=${delta.z}",
        )
        assertTrue(
            abs(delta.y) < 0.05f,
            "e não deveria subir nem descer o modelo: dy=${delta.y}",
        )
        assertTrue(
            delta.z > abs(delta.y),
            "o frente–trás tem de mandar no arrasto vertical (frente=${delta.z}, vertical=${delta.y})",
        )
    }

    @Test
    fun `o arrasto acumula e para no limite`() {
        var offset = Vec3.ZERO
        repeat(50) {
            offset = InteractiveInput.panOffset(offset, dxPixels = 50f, dyPixels = 0f, sizeMeters = 0.5f)
        }

        assertEquals(
            InteractiveInput.MAX_PAN_METERS,
            offset.x,
            1e-6f,
            "arrastar sem parar não pode levar o modelo para longe da figura",
        )
    }

    @Test
    fun `um modelo maior se move mais por pixel arrastado`() {
        val small = InteractiveInput.panOffset(Vec3.ZERO, 100f, 0f, sizeMeters = 0.1f)
        val big = InteractiveInput.panOffset(Vec3.ZERO, 100f, 0f, sizeMeters = 1f)

        assertTrue(
            big.x > small.x,
            "o arrasto acompanha o tamanho: 100 px movem mais um modelo grande (${big.x} × ${small.x})",
        )
    }

    private companion object {

        /**
         * Pose do "Marcador A" **de frente para a câmera**, a 21 cm — a única pose em que o Z do
         * marcador (a "altura NA imagem") cai no eixo vertical do mundo, que é o caso do relato
         * em campo ("impresso e virado para ele").
         *
         * É uma medida, não uma suposição: sai do detector de verdade sobre um quadro sintetizado
         * com a figura real de 0,15 m deformada para um quadrilátero de ~400 px. As três colunas
         * são a imagem dos eixos do marcador no mundo e mostram o referencial que a detecção
         * entrega — **X = largura** (para a direita), **Y = a NORMAL** (aqui quase o eixo da
         * visão: aponta para a câmera) e **Z = altura NA imagem** (aqui apontando para o chão do
         * mundo, o que é o miolo do defeito da 1.0.5).
         *
         * `MarkerDetectorTest` refaz esta medição a cada execução da suíte: se a detecção mudar de
         * referencial, é lá que o teste quebra primeiro — e esta pose aqui deixa de valer.
         */
        val FRENTE_POSE = Pose(
            rotation = doubleArrayOf(
                0.9920177, -0.12609203, -0.0013289266,
                -0.049721323, -0.38144967, -0.9230514,
                0.1158825, 0.9157494, -0.38467428,
            ),
            translation = Vec3(-0.003f, -0.013f, -0.212f),
        )
    }
}
