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

import com.arkz.armodelviewer.model.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes da conversão de gestos em valores da cena.
 *
 * É a parte da etapa 6 que dá para conferir sem tela — e é justamente onde o erro aparece como
 * "o zoom não para de crescer" ou "arrasto para cima move para baixo". A montagem dos gestos
 * na interface (roda, teclado, arrasto, pinça) fica com a conferência de uso.
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
    fun `arrastar para a direita move o modelo para a direita no plano`() {
        val offset = InteractiveInput.panOffset(
            current = Vec3.ZERO,
            dxPixels = 100f,
            dyPixels = 0f,
            sizeMeters = 0.5f,
        )

        // 100 px com um modelo de 50 cm: 100 × 0,5 × 0,002 = 10 cm.
        assertEquals(0.1f, offset.x, 1e-6f, "positivo = para a direita")
        assertEquals(0f, offset.y, 1e-6f)
        assertEquals(0f, offset.z, 1e-6f, "o arrasto é no plano: a normal não se mexe")
    }

    @Test
    fun `arrastar para cima traz o modelo para a frente na figura`() {
        // O sentido deste teste foi corrigido pelo **teste em campo** (decisão 21). Com o sinal
        // da primeira versão, arrastar para cima dava +Y — e o usuário relatou justamente o
        // oposto: o modelo ia "para trás". Então +Y é "para trás" e −Y é "para a frente", e
        // arrastar para cima tem de dar −Y.
        val offset = InteractiveInput.panOffset(
            current = Vec3.ZERO,
            dxPixels = 0f,
            dyPixels = -100f, // arrasto para cima
            sizeMeters = 0.5f,
        )

        assertTrue(offset.y < 0f, "arrastar para cima traz o modelo para a frente: ${offset.y}")
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
}
