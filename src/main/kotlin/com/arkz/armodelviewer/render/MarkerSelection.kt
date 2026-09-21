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
import com.arkz.armodelviewer.ar.TrackingState

/**
 * Qual marcador ancora o modelo.
 *
 * No Android, a tela tinha uma lista de marcadores e uma escolha: **qualquer um** (o
 * padrão) ou **um marcador específico**. A diferença importa no dia a dia com mais de uma
 * figura impressa na mesa: com "qualquer um", o modelo pula de figura em figura conforme a
 * câmera encontra uma; com um marcador escolhido, só aquele recebe o modelo — o que é o que
 * se quer ao comparar dois modelos lado a lado ou ao filmar.
 *
 * A regra é curta e, por isso mesmo, vale ter em um lugar só (e com testes): o modelo vai
 * para o marcador pedido, **se ele estiver rastreado**. Marcador pausado não recebe modelo
 * — é a mesma regra do `RenderScene`, e é o que evita o modelo "piscar" numa figura que a
 * câmera mal reconhece.
 */
object MarkerSelection {

    /**
     * Id que significa "qualquer marcador" — o mesmo valor do `ALL_MARKERS_ID` do app
     * Android, para que as preferências salvas de um app façam sentido no outro.
     */
    const val ALL_MARKERS_ID: String = "all"

    /**
     * Escolhe o marcador da cena.
     *
     * @param markers marcadores reconhecidos no quadro atual.
     * @param selectedId [ALL_MARKERS_ID] (ou vazio) para aceitar qualquer um; qualquer
     *   outro valor casa com o **nome** do marcador (o `name` do `DetectedMarker`, que é o
     *   mesmo id do catálogo).
     * @return o marcador a usar, ou `null` se nenhum serve (nenhum rastreado, ou o
     *   escolhido não está no quadro).
     */
    fun pick(markers: List<DetectedMarker>, selectedId: String): DetectedMarker? {
        val tracking = markers.filter { it.trackingState == TrackingState.TRACKING }
        if (tracking.isEmpty()) return null

        if (selectedId.isBlank() || selectedId == ALL_MARKERS_ID) {
            // "Qualquer um": vale o primeiro rastreado (a ordem vem do detector, que
            // ordena por confiança — quem casou mais pontos vem primeiro).
            return tracking.first()
        }

        return tracking.firstOrNull { it.name == selectedId }
    }
}
