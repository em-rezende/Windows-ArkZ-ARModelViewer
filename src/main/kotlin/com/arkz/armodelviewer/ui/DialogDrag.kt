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

/** Margem mínima entre o cartão e a borda da janela, em pixels. */
const val DIALOG_MARGIN_PIXELS = 8f

/**
 * Limite do arrasto de um diálogo dentro da janela.
 *
 * O diálogo arrastável existe por um pedido concreto do teste em campo: com os controles por
 * cima da imagem da câmera, o usuário quer poder **empurrá-los para o lado** para ver o modelo
 * ancorado. Para isso o cartão sai do centro por conta de um arrasto — e é aqui que ele para:
 *
 * * **nunca sai da janela**: o cartão inteiro continua visível, com uma margem de
 *   [DIALOG_MARGIN_PIXELS] pixels. Sem isso, um arrasto mais longo "perde" o diálogo fora da
 *   tela, e o usuário fica sem os controles e sem saber para onde ele foi;
 * * o limite sai do **tamanho do cartão** e do tamanho da janela, e não de um número fixo: um
 *   diálogo de ajuda (alto) e um de confirmação (baixo) têm limites diferentes, e a mesma
 *   função serve aos dois.
 *
 * Função **pura**, sem Compose, para poder ser testada — a mesma escolha de
 * `CameraProjection` e `MarkerSelection`. O deslocamento devolvido é relativo à posição
 * central (a posição inicial do cartão).
 *
 * @param current deslocamento atual em relação ao centro, em pixels.
 * @param delta arrasto deste quadro, em pixels.
 * @param cardSize tamanho medido do cartão.
 * @param windowSize tamanho da área onde ele pode se mover (a tela).
 */
fun clampDialogDrag(current: Offset, delta: Offset, cardSize: Size, windowSize: Size): Offset {
    val limitX = ((windowSize.width - cardSize.width) / 2f - DIALOG_MARGIN_PIXELS).coerceAtLeast(0f)
    val limitY = ((windowSize.height - cardSize.height) / 2f - DIALOG_MARGIN_PIXELS).coerceAtLeast(0f)

    val target = current + delta
    return Offset(
        x = target.x.coerceIn(-limitX, limitX),
        y = target.y.coerceIn(-limitY, limitY),
    )
}
