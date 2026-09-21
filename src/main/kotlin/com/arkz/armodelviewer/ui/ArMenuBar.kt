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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Todas as ações do aplicativo, reunidas para os menus.
 *
 * Ter um objeto de ações (e não uma dúzia de parâmetros soltos) é o que permite montar os
 * menus como **dados**: cada item declara um rótulo e uma ação, e o desenho do menu é um só.
 */
class ArMenuActions(
    val onLoadModel: () -> Unit,
    val onAdjustModel: () -> Unit,
    val onAutoScale: () -> Unit,
    val onResetSettings: () -> Unit,
    val onRestartDetection: () -> Unit,
    val onCapture: () -> Unit,
    val onManageMarkers: () -> Unit,
    val onCreateMarker: () -> Unit,
    val onLoadMarkerFile: () -> Unit,
    val onHelp: () -> Unit,
    val onAbout: () -> Unit,
    val onCaptureQuality: () -> Unit,
    val onPrintSheet: () -> Unit,
    val onDiagnostics: () -> Unit,
    val onLanguage: (String?) -> Unit,
    val onExit: () -> Unit,
)

/** Um item de menu: rótulo, ação e os estados que o usuário precisa enxergar. */
class ArMenuItem(
    val label: String,
    val enabled: Boolean = true,
    /** Ligado no momento (o painel de ajustes aberto, o idioma em uso). */
    val highlighted: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Barra de menus — os assuntos separados, como pedido.
 *
 * | Menu | Itens |
 * |---|---|
 * | **Idioma** | Sistema (idioma do Windows) + os oito idiomas do projeto |
 * | **Modelo** | Carregar modelo · Ajustes do modelo · Escala automática · Redefinir configurações |
 * | **Câmera** | Reiniciar · Capturar tela |
 * | **Escolher marcador** | Gerenciar marcador · Criar marcador · Carregar marcador |
 * | **Ajuda** | Ajuda · Sobre · Qualidade da captura · Folha de impressão · Copiar diagnóstico |
 * | **Sair** | ação direta (um menu de um item só seria um clique a mais) |
 *
 * A ordem na barra é **Modelo, Câmera, Escolher marcador, Idioma, Ajuda, Sair** (decisão 22):
 * os assuntos do trabalho primeiro, depois o idioma da interface, a ajuda e o sair.
 *
 * Os rótulos são os textos do app Android, sem tradução inventada: o que mudou foi a
 * **organização** — antes as ações eram uma fila de botões mais um menu ⋮, misturando
 * assuntos (zoom ao lado de exportar a folha de impressão). Agrupadas por assunto, o usuário
 * acha "onde fica o quê" sem procurar, e a escolha de idioma deixou de ser um diálogo para
 * ser o próprio menu.
 */
@Composable
fun ArMenuBar(
    actions: ArMenuActions,
    languageTag: String?,
    adjusting: Boolean,
    modelLoaded: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuButton(
            title = strings["section_model"],
            items = listOf(
                ArMenuItem(strings["action_pick_model"], onClick = actions.onLoadModel),
                ArMenuItem(
                    label = strings["action_adjust_model"],
                    enabled = modelLoaded,
                    highlighted = adjusting,
                    onClick = actions.onAdjustModel,
                ),
                ArMenuItem(
                    label = strings["action_auto_scale"],
                    enabled = modelLoaded,
                    onClick = actions.onAutoScale,
                ),
                ArMenuItem(strings["adjust_reset"], onClick = actions.onResetSettings),
            ),
        )

        MenuButton(
            title = strings["camera_title"],
            items = listOf(
                ArMenuItem(strings["action_reset"], onClick = actions.onRestartDetection),
                ArMenuItem(strings["action_capture"], onClick = actions.onCapture),
            ),
        )

        MenuButton(
            title = strings["action_choose_marker"],
            items = listOf(
                ArMenuItem(strings["marker_dialog_title"], onClick = actions.onManageMarkers),
                ArMenuItem(strings["marker_create_confirm"], onClick = actions.onCreateMarker),
                ArMenuItem(strings["marker_load_confirm"], onClick = actions.onLoadMarkerFile),
            ),
        )

        // A ordem é a pedida no teste em campo: assuntos primeiro (Modelo, Câmera, Marcador),
        // depois o idioma da interface, a ajuda e o sair.
        MenuButton(
            title = strings["menu_language"],
            items = appLanguages.map { language ->
                ArMenuItem(
                    label = strings[language.labelKey],
                    highlighted = language.tag == languageTag,
                    onClick = { actions.onLanguage(language.tag) },
                )
            },
        )

        MenuButton(
            title = strings["menu_help"],
            items = listOf(
                ArMenuItem(strings["menu_help"], onClick = actions.onHelp),
                ArMenuItem(strings["menu_about"], onClick = actions.onAbout),
                ArMenuItem(strings["menu_capture_quality"], onClick = actions.onCaptureQuality),
                ArMenuItem(strings["menu_print_sheet"], onClick = actions.onPrintSheet),
                ArMenuItem(strings["menu_copy_diagnostics"], onClick = actions.onDiagnostics),
            ),
        )

        TextButton(onClick = actions.onExit) {
            Text(strings["action_exit"], style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Um menu suspenso: título, e a lista de itens montada a partir de [ArMenuItem].
 *
 * Cada menu guarda o próprio estado de aberto/fechado, o que faz abrir um fechar o outro
 * sem nenhuma coordenação entre eles.
 */
@Composable
private fun MenuButton(title: String, items: List<ArMenuItem>) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                !item.enabled -> Color.White.copy(alpha = 0.35f)
                                item.highlighted -> AccentColor
                                else -> Color.Unspecified
                            },
                        )
                    },
                    enabled = item.enabled,
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                )
            }
        }
    }
}
