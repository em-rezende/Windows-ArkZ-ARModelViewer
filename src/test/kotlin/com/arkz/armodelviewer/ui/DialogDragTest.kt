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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Testes do limite do arrasto dos diálogos.
 *
 * É o que garante a promessa feita ao usuário quando o cartão arrastável entrou: o diálogo
 * **sai do caminho** (para o usuário ver o modelo ancorado atrás dele), mas **não se perde**
 * fora da janela. Os números dos testes são calculados à mão a partir da margem de 8 px, para
 * que uma mudança na fórmula apareça aqui.
 */
class DialogDragTest {

    private val window = Size(1000f, 700f)
    private val card = Size(400f, 300f)

    @Test
    fun `um arrasto curto move o cartao exatamente o quanto foi arrastado`() {
        val moved = clampDialogDrag(Offset.Zero, Offset(40f, -25f), card, window)

        assertEquals(40f, moved.x, 1e-3f)
        assertEquals(-25f, moved.y, 1e-3f)
    }

    @Test
    fun `o cartao para na margem, e nao sai da janela`() {
        val moved = clampDialogDrag(Offset.Zero, Offset(10_000f, 10_000f), card, window)

        // x: (1000 − 400) / 2 − 8 = 292 · y: (700 − 300) / 2 − 8 = 192
        assertEquals(292f, moved.x, 1e-3f)
        assertEquals(192f, moved.y, 1e-3f)
    }

    @Test
    fun `um cartao maior que a janela nao se move`() {
        val moved = clampDialogDrag(Offset.Zero, Offset(100f, 100f), Size(1200f, 900f), window)

        assertEquals(0f, moved.x, 1e-3f)
        assertEquals(0f, moved.y, 1e-3f)
    }

    @Test
    fun `o arrasto acumula, para no limite e volta ao outro lado`() {
        var drag = Offset.Zero
        repeat(20) { drag = clampDialogDrag(drag, Offset(50f, 0f), card, window) }
        assertEquals(292f, drag.x, 1e-3f, "20 arrastos de 50 px passam do limite e param nele")

        repeat(40) { drag = clampDialogDrag(drag, Offset(-50f, 0f), card, window) }
        assertEquals(-292f, drag.x, 1e-3f, "e voltam a parar no limite oposto, sem perder o cartão")
    }

    @Test
    fun `o arrasto nao interage com o eixo que nao foi usado`() {
        val moved = clampDialogDrag(Offset(10f, 20f), Offset(0f, 0f), card, window)

        assertEquals(10f, moved.x, 1e-3f)
        assertEquals(20f, moved.y, 1e-3f)
    }
}
