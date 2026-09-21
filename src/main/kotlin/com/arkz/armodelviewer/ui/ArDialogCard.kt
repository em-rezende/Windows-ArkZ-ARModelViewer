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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/**
 * Posiciona o véu na origem da janela: o diálogo não é ancorado em nenhum componente, e sim
 * sobreposto à janela inteira (é o que o `Popup` permite, e é o que garante que ele fique por
 * cima da barra de menus e do rodapé, e não dentro da coluna da tela de RA).
 */
private object WindowOverlayPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

/**
 * Fixa o tamanho em **pixels**.
 *
 * O véu tem de cobrir exatamente a janela — nem um pixel a menos (apareceria a câmera na
 * borda) — e o tamanho conhecido aqui já vem em pixels; converter para dp e de volta só
 * introduziria erro de arredondamento.
 */
private fun Modifier.fixedSizePx(width: Int, height: Int): Modifier =
    layout { measurable, _ ->
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
    }

/** Fundo dos cartões de diálogo — um tom acima do da janela, para o cartão se destacar. */
private val DialogBackground = Color(0xFF1D222B)

/**
 * O cartão de diálogo do aplicativo: **arrastável** e com os botões à vista.
 *
 * Substitui o `AlertDialog` do Material em todos os diálogos do projeto, por três motivos que
 * vieram do teste em campo:
 *
 *  1. **arrastar** — os diálogos ficam por cima da imagem da câmera, e o usuário precisa
 *     empurrá-los para o lado para ver o modelo ancorado enquanto mexe nos controles. A alça é
 *     a **barra de título** (o ponteiro vira uma mão sobre ela, e o "⠿" no canto indica o
 *     gesto). O cartão nunca sai da janela: quem limita é [clampDialogDrag];
 *  2. **botões visíveis** — os botões eram `TextButton`, sem borda nem fundo: sobre o fundo do
 *     diálogo não se sabia que eram botões sem passar o mouse. O chamador escolhe, com a
 *     convenção de `Button` (preenchido) para a ação principal e `OutlinedButton` (com borda)
 *     para as demais — os dois se leem como botão parados;
 *  3. **véu leve** — o escurecimento sobre a câmera é de apenas 18%: escurecer demais
 *     anularia a razão de arrastar o diálogo. Em troca, o cartão tem borda ciano e sombra
 *     alta, para não se confundir com o vídeo.
 *
 * O véu **consome** cliques e arrastos (o modelo não se mexe atrás do diálogo aberto), mas
 * **não** fecha o diálogo: fechar por clique acidental, no meio de um ajuste, perderia o que o
 * usuário estava fazendo. Fechar é sempre um clique em botão.
 */
@Composable
fun ArDialogCard(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    // `buttons` vem **antes** de `content` de propósito: assim o lambda final da chamada é o
    // conteúdo do cartão, que é o que se escreve por último e em maior quantidade.
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A janela inteira é a referência do véu e do limite do arrasto. O cartão vive numa camada
    // acima de tudo (um `Popup`), e não dentro da coluna da tela de RA: assim ele cobre a
    // janela toda e continua se movendo mesmo por cima da barra de menus e do rodapé.
    val windowSizePx = LocalWindowInfo.current.containerSize
    val windowSize = Size(windowSizePx.width.toFloat(), windowSizePx.height.toFloat())

    Popup(
        popupPositionProvider = WindowOverlayPositionProvider,
        onDismissRequest = null,
        // `focusable` faz o véu receber os cliques e as teclas (o vídeo atrás não recebe
        // gesto), e `onDismissRequest = null` mantém o clique do lado de fora sem fechar.
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .fixedSizePx(windowSizePx.width, windowSizePx.height)
                .background(Color.Black.copy(alpha = 0.18f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* consome o clique: o vídeo atrás não recebe gesto */ },
            contentAlignment = Alignment.Center,
        ) {
            var cardSize by remember { mutableStateOf(Size.Zero) }

            // O deslocamento é lembrado POR TÍTULO: cada diálogo aparece centrado na primeira
            // vez, e dois diálogos diferentes não herdam a posição um do outro.
            var drag by remember(title) { mutableStateOf(Offset.Zero) }

            Surface(
                modifier = modifier
                    .widthIn(min = 300.dp, max = 560.dp)
                    .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
                    .onSizeChanged { cardSize = Size(it.width.toFloat(), it.height.toFloat()) },
                shape = RoundedCornerShape(10.dp),
                color = DialogBackground,
                // Sem `contentColor` explícito, o texto do cartão herda o **preto padrão** do
                // Material: o fundo do cartão não é uma cor do tema, então a resolução
                // automática devolve "sem cor" e o texto sai preto — título e corpo ilegíveis
                // sobre o cinza escuro. Foi um defeito relatado no teste em campo (os diálogos
                // e a Ajuda "quase impossíveis de ler"). O aplicativo é escuro de propósito (a
                // imagem da câmera é a superfície principal), então o conteúdo é claro.
                contentColor = Color.White,
                border = BorderStroke(1.dp, AccentColor.copy(alpha = 0.5f)),
                shadowElevation = 14.dp,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerHoverIcon(PointerIcon.Hand)
                            .pointerInput(title) {
                                detectDragGestures { _, delta ->
                                    drag = clampDialogDrag(drag, delta, cardSize, windowSize)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        // O "⠿" é o símbolo de "agarre aqui": é um símbolo, e não texto,
                        // justamente para não precisar de tradução nos oito idiomas.
                        Text("⠿", style = MaterialTheme.typography.titleMedium, color = AccentColor)
                    }

                    content()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        buttons()
                    }
                }
            }
        }
    }
}
