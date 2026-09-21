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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkz.armodelviewer.markers.MarkerDefinition
import com.arkz.armodelviewer.render.MarkerSelection
import com.arkz.armodelviewer.util.ScreenCapture
import com.arkz.armodelviewer.util.appVersionLabel
import com.arkz.armodelviewer.util.deviceLabel
import com.arkz.armodelviewer.util.toImageBitmap

/**
 * Diálogo de texto — serve à Ajuda (um texto longo, com quebras de linha) e ao Sobre
 * (algumas linhas curtas: versão, desenvolvedor, site e e-mail).
 *
 * O botão de fechar diz "OK" porque **não existe** chave para ele nos textos vindos do app
 * Android (as 112 chaves foram mantidas com paridade exata entre os oito idiomas), e "OK"
 * é a única palavra que se lê igual nos oito — inventar uma chave só aqui quebraria a
 * paridade que os testes verificam.
 */
@Composable
fun InfoDialog(title: String, lines: List<String>, onDismiss: () -> Unit) {
    ArDialogCard(
        title = title,
        onDismiss = onDismiss,
        // "OK" **preenchido**: é a única ação do diálogo, e o preenchimento faz o botão se ler
        // como botão sem o mouse em cima (o pedido do teste em campo).
        buttons = { Button(onClick = onDismiss) { Text("OK") } },
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            lines.forEach { line -> Text(line, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/**
 * Escolha do idioma.
 *
 * A lista é a mesma do app Android (`appLanguages`) e inclui "Sistema (idioma do Windows)",
 * que é o padrão: escolher o idioma à mão grava a preferência e sobrepõe o sistema.
 */
@Composable
fun LanguageDialog(
    currentTag: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings["language_dialog_title"]) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = strings["language_dialog_hint"],
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
                appLanguages.forEach { language ->
                    val isCurrent = language.tag == currentTag
                    TextButton(onClick = { onSelect(language.tag) }) {
                        Text(
                            text = strings[language.labelKey],
                            color = if (isCurrent) AccentColor else Color.White,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

/**
 * Qualidade da captura de tela.
 *
 * Três opções, com os mesmos valores do app Android: 1280 px, 1920 px e "máxima"
 * (`null` = o tamanho da tela). O que a escolha muda é o tamanho do arquivo salvo — a
 * captura em si é sempre feita sem a interface.
 */
@Composable
fun CaptureQualityDialog(
    current: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current

    ArDialogCard(
        title = strings["capture_quality_title"],
        onDismiss = onDismiss,
        buttons = { Button(onClick = onDismiss) { Text("OK") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = strings["capture_quality_hint"],
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            // Cada opção é um botão com **borda** — a escolhida em ciano. Sem a borda, a lista
            // de opções parecia texto (o pedido do teste em campo).
            CAPTURE_QUALITY_OPTIONS.forEach { option ->
                OutlinedButton(
                    onClick = { onSelect(option) },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (option == current) AccentColor else Color.White.copy(alpha = 0.3f),
                    ),
                ) {
                    Text(
                        text = strings[captureQualityLabelKey(option)],
                        color = if (option == current) AccentColor else Color.White,
                    )
                }
            }
        }
    }
}

/** As três qualidades do app Android, na ordem em que aparecem no diálogo. */
val CAPTURE_QUALITY_OPTIONS: List<Int?> = listOf(
    CAPTURE_QUALITY_STANDARD,
    CAPTURE_QUALITY_HIGH,
    null,
)

const val CAPTURE_QUALITY_STANDARD = 1280

/** O mesmo valor que `ScreenCapture.MAX_CAPTURE_LONG_SIDE` (1920 px). */
const val CAPTURE_QUALITY_HIGH = ScreenCapture.MAX_CAPTURE_LONG_SIDE

/** Texto da opção de qualidade (a "máxima" é `null` = resolução da tela). */
fun captureQualityLabelKey(maxLongSide: Int?): String = when (maxLongSide) {
    CAPTURE_QUALITY_STANDARD -> "capture_quality_standard"
    CAPTURE_QUALITY_HIGH -> "capture_quality_high"
    else -> "capture_quality_max"
}

/**
 * Gerenciar marcador: qual figura ancora o modelo, e como criar ou carregar uma nova.
 *
 * É o diálogo que o app Android chamava de "Gerenciar marcador". A lista traz "Todos os
 * marcadores" (o padrão) e cada figura cadastrada — as do catálogo, que vêm com o
 * aplicativo, e as personalizadas, criadas pelo usuário ou carregadas de um arquivo.
 *
 * Cada figura tem o botão de **salvar a folha de impressão** (`marker_export`): é o
 * caminho para levar a figura ao papel no tamanho certo — o mesmo botão do menu ⋮, mas
 * aqui já com o marcador escolhido, sem depender de qual está na frente da câmera.
 */
@Composable
fun MarkerDialog(
    markers: List<MarkerDefinition>,
    /** Nomes dos marcadores reconhecidos no último quadro analisado. */
    recognizedNames: Set<String>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    onLoadFile: () -> Unit,
    onPrintSheet: (MarkerDefinition) -> Unit,
    onRemove: (MarkerDefinition) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var name by remember { mutableStateOf("") }

    ArDialogCard(
        title = strings["marker_dialog_title"],
        onDismiss = onDismiss,
        // Os três botões ficam **fora** da lista que rola, e por isso nunca são cortados. Foi o
        // defeito relatado no teste em campo: com a lista cheia, "Criar marcador" e "Carregar
        // marcador" apareciam cortados na parte de baixo do diálogo — e a rolagem num diálogo
        // não é evidente, então os botões ficavam inalcançáveis.
        buttons = {
            OutlinedButton(onClick = { onCreate(name) }) {
                Text(strings["marker_create_confirm"])
            }
            OutlinedButton(onClick = onLoadFile) {
                Text(strings["marker_load_confirm"])
            }
            Button(onClick = onDismiss) { Text("OK") }
        },
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val hintColor = Color.White.copy(alpha = 0.7f)
            Text(strings["marker_dialog_hint"], style = MaterialTheme.typography.bodySmall, color = hintColor)
            Text(strings["marker_create_hint"], style = MaterialTheme.typography.bodySmall, color = hintColor)

            // "Todos os marcadores" primeiro: é o padrão e o caso mais comum.
            val allSelected = selectedId.isEmpty() || selectedId == MarkerSelectionForAllMarkers
            OutlinedButton(
                onClick = { onSelect(MarkerSelectionForAllMarkers) },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, markerRowBorder(allSelected)),
            ) {
                Text(
                    text = strings["marker_all"],
                    color = if (allSelected) AccentColor else Color.White,
                )
            }

            markers
                .sortedByDescending { it.id in recognizedNames }
                .forEach { marker ->
                    val reading = marker.id in recognizedNames
                    val selected = marker.id == selectedId

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { onSelect(marker.id) },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, markerRowBorder(selected)),
                        ) {
                            // A miniatura vem **antes do nome** (pedido do teste em campo): é o
                            // que faz reconhecer a figura de relance — o nome pode repetir
                            // ("Sala 1" em dois marcadores), a imagem não.
                            Image(
                                bitmap = remember(marker.id) { marker.image.toImageBitmap() },
                                contentDescription = null,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                // O "●" marca a figura que a câmera está LENDO agora, e essas
                                // vêm primeiro: responde "qual marcador está na frente da
                                // câmera neste instante?" — a pergunta de quem escolhe um.
                                text = if (reading) "● ${marker.label}" else marker.label,
                                color = when {
                                    selected -> AccentColor
                                    reading -> Color.White
                                    else -> Color.White.copy(alpha = 0.75f)
                                },
                            )
                        }

                        OutlinedButton(onClick = { onPrintSheet(marker) }) {
                            Text(strings["marker_export"], style = MaterialTheme.typography.bodySmall)
                        }
                        // Só os marcadores personalizados podem sair: as figuras do catálogo
                        // vêm com o aplicativo e são a base da detecção.
                        if (marker.isCustom) {
                            OutlinedButton(onClick = { onRemove(marker) }) {
                                Text(strings["marker_remove"], style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

            Text(
                text = strings["marker_create_name_label"],
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(strings["marker_create_name_hint"]) },
                singleLine = true,
            )
        }
    }
}

/** Borda de uma linha de marcador: ciano na escolhida, discreta nas outras. */
private fun markerRowBorder(selected: Boolean): Color =
    if (selected) AccentColor else Color.White.copy(alpha = 0.3f)

/**
 * Cria um marcador novo: o nome e pronto.
 *
 * É o item "Criar marcador" do menu *Escolher marcador*. Separado do diálogo de gerenciar
 * (que lista e escolhe) porque aqui só há uma decisão a tomar — o nome da figura — e o
 * aplicativo faz o resto: desenha a figura, guarda como marcador e exporta o PNG de
 * impressão em `Pictures/ArkZ ARModelViewer/Marcadores`.
 */
@Composable
fun CreateMarkerDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    var name by remember { mutableStateOf("") }

    ArDialogCard(
        title = strings["marker_create_title"],
        onDismiss = onDismiss,
        buttons = {
            OutlinedButton(onClick = onDismiss) { Text(strings["model_dialog_cancel"]) }
            Button(onClick = { onCreate(name) }) { Text(strings["marker_create_confirm"]) }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = strings["marker_create_hint"],
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = strings["marker_create_name_label"],
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(strings["marker_create_name_hint"]) },
                singleLine = true,
            )
        }
    }
}

/** O id "qualquer marcador", exposto para o diálogo sem repetir a constante. */
private val MarkerSelectionForAllMarkers = MarkerSelection.ALL_MARKERS_ID

/**
 * Linhas do diálogo "Sobre": descrição, versão, **aviso de direito autoral e licença**,
 * desenvolvedor, site e e-mail.
 *
 * As duas linhas de crédito não são enfeite: a GPL-3.0 exige que uma interface
 * interativa mostre o aviso de direito autoral e a licença (seção 5) — é o
 * equivalente desktop do "about box" que o próprio texto da licença cita.
 */
fun aboutLines(strings: Strings): List<String> = listOf(
    strings["about_description"],
    strings.format("about_version", appVersionLabel()),
    strings["about_developer"],
    strings.format("about_developer_name_label", strings["about_developer_name"]),
    strings["about_site"],
    strings["about_email"],
    strings["about_license"],
)

/**
 * Texto do diagnóstico copiável (para suporte).
 *
 * Reúne o que responde às perguntas de quem vai investigar um problema: qual modelo está
 * carregado, quantos marcadores existem, qual ancora o modelo, o último status da captura
 * e o backend de renderização em uso (que vem junto do status, por ser um dado técnico).
 */
fun diagnosticsText(
    strings: Strings,
    modelName: String?,
    markerCount: Int,
    selectedMarkerLabel: String,
    status: String,
): String = buildString {
    appendLine(strings["diagnostics_title"])
    appendLine(strings.format("diagnostics_model", modelName ?: "—"))
    appendLine(strings.format("diagnostics_markers", markerCount))
    appendLine(strings.format("diagnostics_selected_marker", selectedMarkerLabel))
    appendLine(strings.format("diagnostics_status", status))
    appendLine()
    appendLine(deviceLabel())
}
