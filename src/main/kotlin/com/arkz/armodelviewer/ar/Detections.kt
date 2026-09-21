/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.ar

/** Ponto em pixels na imagem da câmera. */
data class Vec2(val x: Float, val y: Float)

/**
 * Estado de rastreio de um marcador — os MESMOS nomes do `TrackingState` do ARCore,
 * de propósito: a lógica da tela de RA foi escrita contra eles no app Android
 * (`PAUSED` continua visível, `STOPPED` sai da cena) e assim é portada sem tradução
 * mental.
 */
enum class TrackingState {

    /** Marcador visto agora. */
    TRACKING,

    /**
     * Marcador visto há pouco e não encontrado no quadro atual.
     *
     * É o caso comum de uma webcam: o marcador sai de foco, o usuário passa a mão
     * na frente, a luz muda. O ARCore chamava isso de `PAUSED` com
     * `LAST_KNOWN_POSE`; aqui o mesmo: o conteúdo continua na última pose conhecida
     * por um curto período (veja `MarkerDetector.PAUSE_FRAMES`) em vez de piscar.
     */
    PAUSED,

    /** Marcador perdido: sai da cena. */
    STOPPED,
}

/** Como a pose foi obtida — os nomes do `TrackingMethod` do ARCore. */
enum class TrackingMethod {

    /** Pose medida neste quadro. */
    FULL_TRACKING,

    /** Pose mantida do último quadro em que o marcador foi visto. */
    LAST_KNOWN_POSE,

    /** Sem pose. */
    NOT_TRACKING,
}

/**
 * Um marcador reconhecido no quadro atual da câmera.
 *
 * É o equivalente desktop do `AugmentedImage` do ARCore — mesmos conceitos, mesmos
 * nomes de campo — com uma diferença: o ARCore devolvia a imagem detectada e o app
 * consultava `centerPose`/`extentX` a cada quadro; aqui a detecção é **por quadro**,
 * então tudo já vem calculado.
 *
 * @property index posição do marcador na lista cadastrada (o `AugmentedImage.index`).
 * @property name identificador do marcador (o `AugmentedImage.name`).
 * @property label rótulo já traduzido/legível, para a interface.
 * @property trackingState estado do rastreio.
 * @property trackingMethod como a pose foi obtida.
 * @property centerPose pose do marcador em relação à câmera.
 * @property extentX largura real do marcador, em metros (`physicalWidthMeters`).
 * @property extentZ altura real NA imagem, em metros. Os marcadores do app são
 *   quadrados, então é igual a [extentX] — o campo existe para manter a mesma
 *   semântica do ARCore.
 * @property corners os quatro cantos do marcador **no quadro**, em pixels, na ordem
 *   superior-esquerdo, superior-direito, inferior-direito, inferior-esquerdo — é o
 *   que desenha a caixa ciano.
 * @property inlierCount quantos pontos de interesse sustentaram a homografia
 *   (diagnóstico: número baixo é detecção instável).
 */
data class DetectedMarker(
    val index: Int,
    val name: String,
    val label: String,
    val trackingState: TrackingState,
    val trackingMethod: TrackingMethod,
    val centerPose: Pose,
    val extentX: Float,
    val extentZ: Float,
    val corners: List<Vec2>,
    val inlierCount: Int,
) {
    /** `true` quando o conteúdo ancorado deve ser exibido neste quadro. */
    val isVisible: Boolean get() = trackingState != TrackingState.STOPPED
}
