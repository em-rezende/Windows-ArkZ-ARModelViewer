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

/**
 * "Escala automática" — o modelo passa a medir, na maior dimensão, a **largura real do
 * marcador**.
 *
 * É o que o app Android chama de "ajustar ao marcador": em vez de o usuário procurar o
 * tamanho no slider, o tamanho do modelo passa a ser o da figura impressa — 15 cm nos
 * marcadores do app, o mesmo número que a folha de impressão usa. Serve para os dois casos
 * comuns: um móvel que precisa ter o tamanho de verdade sobre a planta, e um modelo
 * exportado em milímetros cujo tamanho na cena seria absurdo.
 *
 * A largura real vem da detecção ([DetectedMarker.extentX]), que a obteve da largura física
 * configurada para o marcador — e não de um valor fixo aqui, para que um marcador impresso
 * em outro tamanho continue funcionando.
 *
 * O resultado é limitado à faixa dos sliders ([RenderScene.MIN_SIZE_METERS] a
 * [RenderScene.MAX_SIZE_METERS]): uma detecção ruim não pode jogar o modelo para fora da
 * faixa e travar o painel de ajustes num valor que ele não sabe mostrar.
 */
object AutoScale {

    /**
     * Tamanho (em metros) que faz a maior dimensão do modelo medir a largura do marcador.
     *
     * @return `null` quando não há marcador com largura utilizável (nenhum rastreado, ou
     *   uma largura zerada/negativa vinda de uma detecção inválida).
     */
    fun sizeMetersFor(marker: DetectedMarker?): Float? {
        val width = marker?.extentX ?: return null
        if (!width.isFinite() || width <= 0f) return null
        return width.coerceIn(RenderScene.MIN_SIZE_METERS, RenderScene.MAX_SIZE_METERS)
    }
}
