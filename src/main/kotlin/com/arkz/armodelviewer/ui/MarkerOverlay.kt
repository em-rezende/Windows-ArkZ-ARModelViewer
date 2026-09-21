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

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.arkz.armodelviewer.ar.DetectedMarker
import com.arkz.armodelviewer.ar.TrackingState
import kotlin.math.min

/**
 * A **caixa ciano** sobre o marcador reconhecido.
 *
 * É o mesmo feedback visual do app Android: sem modelo carregado, o usuário precisa
 * de alguma coisa que diga "a detecção está funcionando" — e no Android isso era um
 * cubo ciano sobre a figura. Aqui é o contorno exato dos quatro cantos encontrados,
 * com o rótulo e a distância, que é ainda mais informativo (e, na etapa 4, o modelo
 * entra por cima deste contorno).
 *
 * O desenho é sobreposto à imagem da câmera e **não** faz parte dela: a captura de
 * tela (etapa 7) compõe a cena sem a interface, exatamente como no Android.
 *
 * As coordenadas dos cantos estão em pixels do **quadro da câmera**, então aqui elas
 * são convertidas para o espaço da tela com a mesma regra do `ContentScale.Fit` usado
 * no `Image` (escala uniforme + centralização) — sem isso a caixa apareceria
 * deslocada sempre que a janela não tivesse a mesma proporção da câmera.
 */
@Composable
fun MarkerOverlay(
    markers: List<DetectedMarker>,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier,
) {
    if (markers.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val scale = min(size.width / imageWidth, size.height / imageHeight)
        val offsetX = (size.width - imageWidth * scale) / 2f
        val offsetY = (size.height - imageHeight * scale) / 2f

        // Espessura proporcional ao quadro, com limites: fina demais não se vê,
        // grossa demais cobre o próprio marcador.
        val strokeWidth = (min(size.width, size.height) / 240f).coerceIn(2f, 6f)
        val labelOffset = strokeWidth + 6f

        // Só os marcadores **rastreados neste quadro** ganham caixa. Antes, os que estavam em
        // PAUSED também eram desenhados, com o rótulo "… · última pose" — e com dois marcadores
        // na mesa (um rastreado e outro pausado) apareciam DUAS caixas sobrepostas, uma com a
        // medida e a outra com "última pose". Foi assim que o defeito apareceu no teste em
        // campo: "o que está sendo colocado são dois retângulos, um sobre o outro". A caixa
        // ciano é o aviso de que a detecção está funcionando; uma pose já perdida não avisa
        // nada — é ruído sobre a figura.
        markers
            .filter { it.trackingState == TrackingState.TRACKING }
            .forEach { marker ->
                val color = AccentColor

            val points = marker.corners.map { corner ->
                Offset(offsetX + corner.x * scale, offsetY + corner.y * scale)
            }
            val outline = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { point -> lineTo(point.x, point.y) }
                close()
            }
            drawPath(outline, color = color, style = Stroke(width = strokeWidth))

            // Um quadradinho em cada canto: além de reforçar o alinhamento, deixa
            // claro qual canto é qual quando a perspectiva é forte.
            points.forEach { point ->
                drawPath(
                    Path().apply {
                        moveTo(point.x, point.y)
                        lineTo(point.x, point.y)
                    },
                    color = color,
                    style = Stroke(width = strokeWidth * 2f),
                )
            }

            drawMarkerLabel(
                textMeasurer = textMeasurer,
                text = labelOf(marker),
                anchor = points[0],
                color = color,
                labelOffset = labelOffset,
            )
        }
    }
}

/** Texto do rótulo: nome do marcador (ou do arquivo) e a distância medida. */
private fun labelOf(marker: DetectedMarker): String {
    val distance = "%.2f m".format(marker.centerPose.distanceMeters)
    return "${marker.label} · $distance"
}

/**
 * Desenha o rótulo acima do canto superior-esquerdo da caixa, com um fundo escuro
 * para continuar legível sobre qualquer imagem da câmera.
 */
private fun DrawScope.drawMarkerLabel(
    textMeasurer: TextMeasurer,
    text: String,
    anchor: Offset,
    color: Color,
    labelOffset: Float,
) {
    val style = TextStyle(color = color, fontSize = 13.sp)
    val measured = textMeasurer.measure(text, style)
    val topLeft = Offset(anchor.x, (anchor.y - measured.size.height - labelOffset).coerceAtLeast(0f))

    drawRect(
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = Offset(topLeft.x - 4f, topLeft.y - 2f),
        size = androidx.compose.ui.geometry.Size(
            measured.size.width + 8f,
            measured.size.height + 4f,
        ),
    )
    drawText(textMeasurer, text, topLeft = topLeft, style = style)
}

/** Cor de destaque da interface (ciano), a mesma do app Android. */
internal val AccentColor = Color(0xFF00E5FF)
