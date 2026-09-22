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

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.arkz.armodelviewer.ar.CameraIntrinsics
import com.arkz.armodelviewer.markers.CustomMarkerStore
import com.arkz.armodelviewer.markers.MarkerCatalog
import com.arkz.armodelviewer.markers.MarkerDefinition
import com.arkz.armodelviewer.markers.MarkerGenerator
import com.arkz.armodelviewer.markers.labelFor
import com.arkz.armodelviewer.model.Vec3
import com.arkz.armodelviewer.render.InteractiveInput
import com.arkz.armodelviewer.render.MarkerSelection
import com.arkz.armodelviewer.render.PreparedModel
import com.arkz.armodelviewer.render.RenderScene
import com.arkz.armodelviewer.render.SceneComposer
import com.arkz.armodelviewer.util.AppLog
import com.arkz.armodelviewer.util.AppPreferences
import com.arkz.armodelviewer.util.GallerySaver
import com.arkz.armodelviewer.util.MarkerPrintSheet
import com.arkz.armodelviewer.util.NativeLibraries
import com.arkz.armodelviewer.util.ScreenCapture
import com.arkz.armodelviewer.util.copyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit

/**
 * A tela de realidade aumentada — a versão desktop do `ARViewScreen` do app Android.
 *
 * [CameraSection] é dona da câmera e da detecção, e entrega o quadro **e** os marcadores
 * reconhecidos nele. O [SceneComposer] desenha a cena fora da thread de interface e é a
 * **fonte única** dos ajustes: o painel lê e escreve nele, e a escala automática escreve no
 * mesmo lugar — sem dois estados para manter em sincronia.
 *
 * As utilidades portadas na etapa 1 fazem o trabalho de verdade: [MarkerGenerator] cria a
 * figura, [CustomMarkerStore] guarda, [MarkerPrintSheet] monta a folha de impressão,
 * [GallerySaver] e [ScreenCapture] salvam os PNGs.
 *
 * O compositor (e o motor gráfico, dentro dele) nasce no primeiro quadro da câmera —
 * quando o tamanho real do quadro e os intrínsecos da detecção são conhecidos — e é
 * liberado no `DisposableEffect`, que é o único lugar em que a liberação acontece em
 * qualquer caminho de saída.
 *
 * @param languageTag idioma escolhido (`null` = idioma do Windows).
 * @param onLanguageChange troca o idioma (a preferência é gravada por quem chama).
 * @param onExit fecha o aplicativo (o botão "Sair" da barra).
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun ArSceneSection(
    languageTag: String?,
    onLanguageChange: (String?) -> Unit,
    onExit: () -> Unit,
    /** Mostrar o rodapé de estado — escondido quando a janela está maximizada. */
    showStatus: Boolean = true,
    /** Modo tela cheia: só a câmera com o modelo; os comandos sobrepõem a imagem. */
    fullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val preferences = remember { AppPreferences() }
    val customMarkers = remember { CustomMarkerStore() }

    val composer = remember { SceneComposer() }
    DisposableEffect(composer) {
        onDispose { composer.close() }
    }

    // Fonte única dos ajustes: o que o desenho está usando é o que o painel mostra.
    val sceneFrame by composer.frame.collectAsState()
    val renderError by composer.error.collectAsState()
    val sizeMeters by composer.sizeMeters.collectAsState()
    val rotation by composer.rotationDegrees.collectAsState()
    val elevationMeters by composer.elevationMeters.collectAsState()

    var prepared by remember { mutableStateOf<PreparedModel?>(null) }
    var displayName by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // O estado do painel de ajustes é o próprio `dialog` (`ArDialog.AdjustModel`): um só lugar
    // decide o que está aberto, e não um sinalizador por painel.
    var menuExpanded by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ArDialog?>(null) }
    var captureMaxLongSide by remember { mutableStateOf(preferences.captureMaxLongSide) }
    var selectedMarkerId by remember { mutableStateOf(MarkerSelection.ALL_MARKERS_ID) }
    var markers by remember { mutableStateOf<List<MarkerDefinition>>(emptyList()) }
    var markerRefresh by remember { mutableStateOf(0) }

    // Nomes reconhecidos agora: o diálogo de marcador usa isso para mostrar qual figura a
    // câmera está lendo. Só é reescrito quando MUDA — escrever a cada quadro recomporia a
    // tela 30 vezes por segundo à toa.
    var detectedNames by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Marca se a roda já foi registrada no log (uma vez por execução basta).
    var loggedScroll by remember { mutableStateOf(false) }

    // Aviso passageiro sobre a imagem (por ora, o da escala automática aplicada). O texto fica
    // guardado à parte porque, quando o aviso "acaba", o alfa ainda está caindo: sem isso o
    // texto sumiria de uma vez, no meio do fade.
    var toast by remember { mutableStateOf<String?>(null) }
    var lastToast by remember { mutableStateOf("") }
    val toastAlpha by animateFloatAsState(
        targetValue = if (toast != null) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "aviso sobre a imagem",
    )

    LaunchedEffect(toast) {
        val current = toast ?: return@LaunchedEffect
        lastToast = current
        delay(TOAST_MILLIS)
        toast = null
    }

    // Tela cheia: a barra de comandos aparece ao entrar no modo e some depois de alguns
    // segundos sem o mouse passar sobre ela; qualquer movimento sobre a faixa a traz de volta.
    var controlsVisible by remember { mutableStateOf(true) }
    var lastControlsInteraction by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(fullscreen) {
        if (fullscreen) {
            controlsVisible = true
            lastControlsInteraction = System.currentTimeMillis()
        }
    }

    // O relógio da barra: enquanto ela está visível, a cada meio segundo verifica se o mouse
    // parou de passar por cima.
    LaunchedEffect(fullscreen, controlsVisible) {
        if (!fullscreen || !controlsVisible) return@LaunchedEffect
        // Não há `Enter`/`Exit` nesta faixa de propósito: o único sinal confiável é "houve
        // movimento", que é também o que o usuário entende por "estou mexendo aqui".
        while (true) {
            delay(CONTROLS_IDLE_MILLIS)
            val idle = System.currentTimeMillis() - lastControlsInteraction
            if (idle >= CONTROLS_IDLE_MILLIS) {
                controlsVisible = false
                break
            }
        }
    }

    // Foco do teclado para os atalhos de zoom (`+` e `-`). Pedido na primeira composição
    // para que as teclas funcionem sem o usuário precisar clicar na área do vídeo.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    /**
     * Zoom por gesto: [steps] passos de 10%.
     *
     * O valor de partida é lido do **compositor** (e não do estado da composição) para que
     * uma sequência rápida de passos — uma roda que gira vários cliques num quadro — não
     * perca nenhum: cada passo parte do tamanho que já está valendo na cena.
     */
    fun zoomBy(steps: Float) {
        composer.setSizeMeters(
            InteractiveInput.zoomedSize(currentMeters = composer.sizeMeters.value, steps = steps),
        )
    }

    /**
     * Teclas de zoom.
     *
     * `=` entra junto do `+` de propósito: num teclado brasileiro (ABNT2) o `+` é
     * `Shift`+`=`, e o sistema pode entregar a tecla como `Equals` — sem isso o atalho de
     * aproximar simplesmente não funcionaria nesta máquina.
     */
    fun handleZoomKeys(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        return when (event.key) {
            Key.Plus, Key.Equals, Key.NumPadAdd -> {
                zoomBy(1f)
                true
            }
            Key.Minus, Key.NumPadSubtract -> {
                zoomBy(-1f)
                true
            }
            else -> false
        }
    }

    /**
     * Arrasto do "modo livre": move o modelo na largura da figura (X) e no frente–trás (Y) —
     * veja `InteractiveInput.panOffset`.
     */
    fun panBy(dxPixels: Float, dyPixels: Float) {
        composer.setOffsetMeters(
            InteractiveInput.panOffset(
                current = composer.offsetMeters.value,
                dxPixels = dxPixels,
                dyPixels = dyPixels,
                sizeMeters = composer.sizeMeters.value,
            ),
        )
    }

    // A lista de marcadores (catálogo + personalizados) é lida do disco fora da thread de
    // interface, e relida quando um marcador é criado ou carregado. O mesmo `markerRefresh`
    // alimenta a detecção: sem isso, um marcador recém-criado só seria reconhecido ao
    // reabrir o aplicativo.
    LaunchedEffect(markerRefresh) {
        markers = withContext(Dispatchers.IO) {
            MarkerCatalog.loadBundled() + customMarkers.loadDefinitions()
        }
    }

    // A ajuda abre sozinha na primeira execução, como no app Android.
    LaunchedEffect(Unit) {
        if (!preferences.helpShown) {
            dialog = ArDialog.Help
            preferences.helpShown = true
        }
    }

    // Mensagens de resultado somem sozinhas: 8 s é o tempo que o app Android usa.
    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_MILLIS)
            message = null
        }
    }

    val sceneImageCache = remember { SceneImageCache() }
    val lastFrameSize = remember { IntArray(2) }

    /** Rótulo do marcador escolhido — usado na barra de status e no diagnóstico. */
    fun selectedMarkerLabel(): String =
        markers.firstOrNull { it.id == selectedMarkerId }?.label ?: strings["marker_all"]

    fun selectMarker(id: String) {
        selectedMarkerId = id
        composer.setSelectedMarkerId(id)
        message = strings.format("status_scan_marker", selectedMarkerLabel())
    }

    /**
     * Gera a folha de impressão do marcador e salva o PNG.
     *
     * A folha traz a figura no tamanho certo (a largura física cadastrada) com as instruções
     * — é o caminho entre "criei um marcador" e "tenho o papel na mão".
     */
    fun exportPrintSheet(marker: MarkerDefinition) {
        scope.launch {
            val note = strings.format(
                "print_sheet_note",
                "%.0f".format(marker.physicalWidthMeters * 100f),
            )

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val sheet = MarkerPrintSheet.build(marker, note)
                    GallerySaver.saveMarkerImage(sheet, "marcador_${marker.id}.png").getOrThrow()
                }
            }

            message = result.fold(
                { strings.format("print_sheet_saved", it) },
                { strings.format("print_sheet_failed", it.message ?: "") },
            )
        }
    }

    /**
     * Cria um marcador novo com a figura desenhada pelo aplicativo.
     *
     * O fluxo é o que o app Android documenta: o nome vira o rótulo, a figura é gerada
     * ([MarkerGenerator]), guardada ([CustomMarkerStore]) e exportada como PNG para
     * impressão em 15 cm — com a mensagem dizendo onde o arquivo ficou.
     */
    fun createMarker(name: String) {
        val label = name.trim()
        if (label.isEmpty()) {
            message = strings["status_marker_name_required"]
            return
        }

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val image = MarkerGenerator.generate(label)
                    val definition = customMarkers.addImage(image, label).getOrThrow()
                    val exported = GallerySaver.saveMarkerImage(image, "marcador_${definition.id}.png")
                    definition to exported
                }
            }

            message = result.fold(
                { (definition, exported) ->
                    exported.fold(
                        { strings.format("status_marker_generated", definition.label, it) },
                        {
                            strings.format(
                                "status_marker_created_no_export",
                                definition.label,
                                it.message ?: "",
                            )
                        },
                    )
                },
                { strings.format("status_marker_create_failed") + ": ${it.message}" },
            )

            // O marcador novo passa a valer na detecção e já fica escolhido — é o que o
            // usuário quer logo depois de criá-lo.
            markerRefresh++
            result.onSuccess { (definition, _) -> selectMarker(definition.id) }
        }
    }

    /** Carrega um modelo escolhido no diálogo nativo, preparando-o fora da interface. */
    fun pickModel() {
        scope.launch {
            val chosen = ModelPicker.choose(strings["model_dialog_title"]) ?: return@launch

            loading = true
            message = strings.format("status_loading_model", chosen.name)

            // Modelo grande: ao passar 20 s o texto avisa que vale esperar (é o mesmo tempo
            // e o mesmo texto do app Android).
            val slowHint = launch {
                delay(SLOW_LOAD_HINT_MILLIS)
                message = strings.format("status_loading_model_slow", chosen.name)
            }

            val startedAt = System.currentTimeMillis()
            val result = ModelPicker.prepare(chosen)
            slowHint.cancel()
            loading = false

            result.onSuccess { model ->
                prepared = model
                displayName = chosen.name
                val seconds = (System.currentTimeMillis() - startedAt) / 1_000.0
                message = strings.format(
                    "status_model_loaded",
                    chosen.name,
                    "%.1f".format(seconds),
                    "%.1f".format(model.bounds.largestDimension),
                )
            }.onFailure { error ->
                // A mensagem do carregador já é a explicação útil ("o formato .dae não é
                // suportado…"), e é ela que o app Android mostrava. Quando o que falha é uma
                // **biblioteca nativa**, porém, ela chega só como o invólucro do JVM ("Could not
                // initialize class …"): aí vale traduzir a causa profunda e dizer o que fazer —
                // foi o caso do notebook sem o runtime do Visual C++ (`NativeLibraries`).
                message = NativeLibraries.describe(error, NativeLibraries.missingVisualCppRuntime())
                AppLog.warn("Falha ao carregar o modelo: ${error.message}", error)
            }
        }
    }

    /** Volta todos os ajustes ao padrão (o "Reiniciar" e o "Redefinir configurações"). */
    fun resetSettings() {
        composer.setRotation(RenderScene.DEFAULT_ROTATION_DEGREES)
        composer.setSizeMeters(RenderScene.DEFAULT_SIZE_METERS)
        composer.setElevationMeters(0f)
        composer.setOffsetMeters(Vec3.ZERO)
        message = strings["status_reset_done"]
    }

    /**
     * Carrega um marcador a partir de uma imagem que já está no computador.
     *
     * É o caminho alternativo do app Android: em vez de o aplicativo desenhar a figura, o
     * usuário usa uma foto, um QR code ou um print de tela. O rótulo vem do nome do arquivo.
     *
     * Declarada **antes** das ações e dos diálogos porque função local em Kotlin precisa
     * existir na linha em que é usada — não há içamento.
     */
    fun loadMarkerFromFile() {
        scope.launch {
            val file = ModelPicker.chooseImage(strings["marker_load_confirm"]) ?: return@launch

            val result = withContext(Dispatchers.IO) { customMarkers.addFromFile(file) }

            message = result.fold(
                { definition -> strings.format("status_scan_marker", definition.label) },
                { strings.format("status_marker_create_failed") + ": ${it.message}" },
            )

            markerRefresh++
            result.onSuccess { definition -> selectMarker(definition.id) }
        }
    }

    val actions = ArMenuActions(
        onLoadModel = { pickModel() },
        onAdjustModel = { dialog = ArDialog.AdjustModel },
        onAutoScale = {
            val applied = composer.autoScale()
            if (applied == null) {
                message = strings["status_marker_tracking_no_model"]
            } else {
                message = strings.format("status_auto_scale", "%.2f".format(applied))
                // O aviso sobre a imagem responde "o botão funcionou?" sem o usuário ter de
                // procurar a linha certa no rodapé; o rodapé continua com a **medida** aplicada,
                // que é o dado que ele quer conferir depois.
                toast = strings["status_auto_scale_applied"]
            }
        },
        onResetSettings = { resetSettings() },
        // "Reiniciar" (menu Câmera) e "Redefinir configurações" (menu Modelo) fazem a mesma
        // coisa: descartar as detecções e voltar rotação, tamanho, elevação e arrasto ao
        // padrão. É o que o texto de ajuda do app descreve para o botão de reiniciar.
        onRestartDetection = { resetSettings() },
        onCapture = {
            val frame = sceneFrame
            if (frame == null) {
                AppLog.warn("Captura pedida antes do primeiro quadro desenhado.")
            } else {
                scope.launch {
                    val image = sceneImageCache.toBufferedImage(frame)
                    val result = withContext(Dispatchers.IO) {
                        ScreenCapture.saveCapture(image = image, maxLongSide = captureMaxLongSide)
                    }

                    // O app Android toca o som de disparo da câmera; o Windows não tem um som
                    // padrão de obturador, então o aviso sonoro do sistema dá o retorno
                    // imediato de que a captura aconteceu.
                    runCatching { Toolkit.getDefaultToolkit().beep() }

                    message = result.fold(
                        { path ->
                            // Registrado no log de propósito: a mensagem na tela some em 8 s,
                            // e "onde foi parar a imagem?" é a primeira pergunta quando o
                            // usuário não a encontra.
                            AppLog.info("Captura salva em $path")
                            strings.format("status_marker_exported", path)
                        },
                        { error ->
                            AppLog.warn("Falha ao salvar a captura: ${error.message}")
                            error.message ?: strings["status_diagnostics_failed"]
                        },
                    )
                }
            }
        },
        onManageMarkers = { dialog = ArDialog.Markers },
        onCreateMarker = { dialog = ArDialog.CreateMarker },
        onLoadMarkerFile = { loadMarkerFromFile() },
        onHelp = { dialog = ArDialog.Help },
        onAbout = { dialog = ArDialog.About },
        onCaptureQuality = { dialog = ArDialog.CaptureQuality },
        onPrintSheet = {
            // A folha de impressão é de UM marcador: com "todos os marcadores" escolhido não
            // há como saber qual figura o usuário quer no papel.
            val marker = markers.firstOrNull { it.id == selectedMarkerId }
            if (marker == null) {
                message = strings["status_print_sheet_needs_marker"]
            } else {
                exportPrintSheet(marker)
            }
        },
        onDiagnostics = {
            val text = diagnosticsText(
                strings = strings,
                modelName = displayName,
                markerCount = markers.size,
                selectedMarkerLabel = selectedMarkerLabel(),
                status = composer.backend.value ?: "",
            )
            message = if (copyToClipboard(text)) {
                strings["status_diagnostics_copied"]
            } else {
                strings["status_diagnostics_failed"]
            }
        },
        // A escolha de idioma deixou de ser diálogo: agora é o próprio menu, com a lista.
        onLanguage = { tag -> onLanguageChange(tag) },
        onExit = onExit,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Na tela cheia a barra de menus sai de cena: o modo existe para ver **só a câmera com o
        // modelo**, e a saída é a tecla `Esc` (ou o próprio botão "Tela cheia").
        if (!fullscreen) {
        ArMenuBar(
            actions = actions,
            languageTag = languageTag,
            adjusting = dialog == ArDialog.AdjustModel,
            modelLoaded = prepared != null,
            // A barra de menus desenha por cima do vídeo (`zIndex`): maximizada, o vídeo cresce
            // e, sem isto, uma sobreposição de desenho poderia cobrir os menus — foi o que o
            // teste em campo relatou ("ao maximizar, os menus desaparecem").
            modifier = Modifier.zIndex(1f),
        )
        }

        // Os gestos da etapa 6 ficam na **área do vídeo** (e não na tela toda): arrastar sobre
        // a imagem move o modelo na largura da figura e no frente–trás (veja `InteractiveInput`),
        // e a roda com `Ctrl` dá o zoom. É o mesmo lugar em que o app Android recebia a pinça.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // O vídeo ocupa todo o espaço que sobra entre a barra de menus e o rodapé.
                .weight(1f)
                // `clipToBounds` garante que nada do vídeo (nem a sobreposição da caixa)
                // pinte fora desta área: é o que impede o vídeo de cobrir a barra de menus
                // quando a janela é maximizada.
                .clipToBounds()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    // `Esc` sai da tela cheia — a tecla que o usuário espera. Quando o foco está
                    // nesta área, é aqui que a tecla é tratada; quando está num botão, quem
                    // trata é a janela (Main). Como cada caminho só age se for o que recebeu a
                    // tecla, nunca há dois toggles para a mesma tecla.
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && fullscreen) {
                        onToggleFullscreen()
                        true
                    } else {
                        handleZoomKeys(event)
                    }
                }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    // Registrado UMA vez: se o zoom não acontecer, o log diz se o evento
                    // chegou e se o `Ctrl` foi reconhecido — as duas hipóteses possíveis —
                    // em vez de deixar o defeito invisível.
                    if (!loggedScroll) {
                        loggedScroll = true
                        AppLog.info(
                            "Roda recebida sobre o vídeo " +
                                "(ctrl=${event.keyboardModifiers.isCtrlPressed}).",
                        )
                    }

                    // A pinça de touchpad de precisão chega ao aplicativo como `Ctrl`+roda (é
                    // assim que o Windows a entrega): os dois gestos são o mesmo caminho de
                    // código — veja `InteractiveInput`.
                    if (!event.keyboardModifiers.isCtrlPressed) return@onPointerEvent

                    // Rolar para cima (delta negativo) aproxima, como em qualquer programa.
                    val notches = -event.changes.first().scrollDelta.y
                    if (notches != 0f) {
                        zoomBy(notches)
                        // Consumir o evento é o que impede a página de rolar junto com o
                        // zoom quando o ponteiro está sobre o vídeo.
                        event.changes.forEach { change -> change.consume() }
                    }
                }
                .pointerInput(Unit) {
                    // ------------------------------------------------ o que está tocando a imagem
                    // Registra **só quando muda** o que está em contato com o vídeo: quantos
                    // ponteiros e de que tipo. É a medição que responde "a pinça de dois dedos
                    // deveria funcionar nesta máquina?" — e a resposta, no Windows, é que a tela
                    // sensível ao toque entrega **um** ponteiro só (o sistema converte o toque em
                    // mouse, e a camada de entrada do Compose Desktop não escuta eventos de
                    // toque). Veja a decisão 31 do roadmap: por isso o zoom por toque tem botões.
                    var ultimaLinha: String? = null

                    awaitPointerEventScope {
                        while (true) {
                            // Passagem `Initial`: observar **antes** de qualquer consumo, para não
                            // interferir nos gestos que vêm logo abaixo nesta mesma cadeia.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val emContato = event.changes.filter { change -> change.pressed }
                            val tipos = emContato.map { change -> change.type.toString() }
                                .distinct()
                                .sorted()
                                .joinToString(", ")
                            val linha = "${emContato.size} ($tipos)"

                            if (linha != ultimaLinha) {
                                ultimaLinha = linha
                                AppLog.info("Pontos em contato com o video: $linha.")
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // Um bloco cobre dois gestos: a **pinça** de tela sensível ao toque
                        // (zoom ≠ 1) e o **arrasto** com o mouse (pan ≠ 0).
                        if (zoom != 1f) zoomBy(InteractiveInput.stepsFor(zoom))
                        if (pan != Offset.Zero) panBy(pan.x, pan.y)
                    }
                },
        ) {
        CameraSection(
            sceneFrame = sceneFrame,
            hasModel = prepared != null,
            markersRefreshToken = markerRefresh,
            onFrame = { cameraFrame, detected ->
                val width = cameraFrame.image.width
                val height = cameraFrame.image.height

                // O renderizador nasce (e só depois se reajusta) quando o tamanho REAL do
                // quadro é conhecido: é dele que saem a projeção e o plano de fundo.
                if (width != lastFrameSize[0] || height != lastFrameSize[1]) {
                    lastFrameSize[0] = width
                    lastFrameSize[1] = height
                    composer.resize(width, height)
                    composer.setIntrinsics(CameraIntrinsics.forFrame(width, height))
                    composer.start()
                }

                val names = detected.map { it.name }.toSet()
                if (names != detectedNames) detectedNames = names

                composer.submit(sceneImageCache.toBackgroundFrame(cameraFrame.image), detected)
            },
        )

        // ---------------------------------------- aviso passageiro sobre a imagem
        // O aviso (por ora, o da escala automática aplicada) fica no **alto** da imagem, no mesmo
        // desenho de "slot" dos botões do projeto — e longe da faixa de comandos da tela cheia,
        // que vive no rodapé, para os dois nunca competirem.
        if (toastAlpha > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
            ) {
                Text(
                    text = lastToast,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .alpha(toastAlpha)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        // ---------------------------------------- barra de comandos sobreposta (tela cheia)
        // Na tela cheia a imagem ocupa a janela toda, e os comandos **sobrepõem** o vídeo com
        // fade: aparecem ao entrar no modo, somem depois de alguns segundos sem o mouse passar
        // sobre a faixa, e voltam assim que ele passa por ali. É o que permite capturar a tela
        // com a imagem limpa — e é também por isso que os comandos não são escondidos de vez:
        // "tirar uma captura no modo tela cheia" é uma das razões de o modo existir.
        //
        // A faixa é mais alta que os botões (84 dp) de propósito: para acordar a barra não é
        // preciso acertar o mouse exatamente sobre um botão.
        if (fullscreen) {
            // `Modifier.alpha` com `animateFloatAsState`, e **não** `AnimatedVisibility`: a barra
            // vive no escopo de uma `Column` desta tela, e ali o `AnimatedVisibility` de
            // `ColumnScope` vence a resolução da sobrecarga (a versão sem escopo exigiria um
            // receptor explícito). O fade é o mesmo, e há um ganho: quando o alfa chega a zero a
            // barra sai da composição — botão invisível que ainda recebe clique é armadilha.
            val controlsAlpha by animateFloatAsState(
                targetValue = if (controlsVisible) 1f else 0f,
                animationSpec = tween(durationMillis = 400),
                label = "barra de comandos",
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(84.dp)
                    .onPointerEvent(PointerEventType.Move) {
                        lastControlsInteraction = System.currentTimeMillis()
                        controlsVisible = true
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (controlsAlpha > 0f) {
                    ModelActionButtons(
                        strings = strings,
                        loading = loading,
                        hasModel = prepared != null,
                        onPickModel = { pickModel() },
                        onAdjust = { dialog = ArDialog.AdjustModel },
                        onAutoScale = actions.onAutoScale,
                        onCapture = actions.onCapture,
                        onToggleFullscreen = onToggleFullscreen,
                        onZoom = { steps -> zoomBy(steps) },
                        modifier = Modifier
                            .alpha(controlsAlpha)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
        }

        // O modelo é entregue ao compositor assim que muda (`null` tira o modelo da cena).
        LaunchedEffect(prepared) {
            composer.setModel(prepared)
        }

        // Fora da tela cheia, os comandos ficam abaixo do vídeo, como sempre estiveram. Na tela
        // cheia eles **sobrepõem** a imagem, com fade — veja a barra sobreposta, dentro da área
        // do vídeo.
        if (!fullscreen) {
            ModelActionButtons(
                strings = strings,
                loading = loading,
                hasModel = prepared != null,
                onPickModel = { pickModel() },
                onAdjust = { dialog = ArDialog.AdjustModel },
                onAutoScale = actions.onAutoScale,
                onCapture = actions.onCapture,
                onToggleFullscreen = onToggleFullscreen,
                onZoom = { steps -> zoomBy(steps) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (dialog == ArDialog.AdjustModel) {
            // O painel de ajustes é **diálogo**, e não uma faixa abaixo do vídeo (correção
            // pedida no teste em campo, decisão 21): aberto embaixo, ele empurrava a área do
            // modelo para fora da janela — o usuário via "o menu principal sumir". Como
            // diálogo, a cena continua visível atrás e os controles ficam por cima dela.
        }

        // ---------------------------------------------------------------- rodapé
        // Três linhas de estado: o programa e o ambiente, o modelo carregado (com a última
        // mensagem no lugar dela) e o marcador escolhido. Elas **somem quando a janela está
        // maximizada**: com a tela toda, o espaço é melhor usado pelo vídeo, e as informações
        // de estado deixam de ser necessárias (pedido do teste em campo).
        if (showStatus && !fullscreen) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            val subtle = Color.White.copy(alpha = 0.55f)

            // A linha de ambiente (nome, versão, sistema, idioma e renderizador) saiu do rodapé
            // a pedido do teste em campo: é informação de diagnóstico, e ficava competindo com o
            // que interessa no uso — o modelo carregado e o marcador. O "Copiar diagnóstico"
            // (menu Ajuda) continua levando esses dados a quem for investigar um problema.
            //
            // No lugar dela entra o aviso de leitura, que antes ficava em ciano **sobre a
            // imagem**: estado pertence ao rodapé, não à figura que o usuário está olhando.
            val labelsReading = detectedNames
                .map { id -> markers.labelFor(id) }
                .sorted()
                .joinToString(", ")

            if (labelsReading.isNotEmpty()) {
                Text(
                    text = strings.format(
                        if (prepared != null) {
                            "status_marker_tracking"
                        } else {
                            "status_marker_tracking_no_model"
                        },
                        labelsReading,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentColor,
                )
            }

            Text(
                text = renderError ?: message ?: statusOf(strings, displayName),
                style = MaterialTheme.typography.bodySmall,
                color = if (renderError != null) ErrorColor else Color.White.copy(alpha = 0.85f),
            )

            // O "●" é o mesmo sinal do diálogo de marcador: a figura que a câmera está lendo
            // neste instante. Sem ele, o rodapé diria apenas qual marcador foi ESCOLHIDO.
            Text(
                text = strings.format(
                    "diagnostics_selected_marker",
                    if (selectedMarkerId in detectedNames) {
                        "● ${selectedMarkerLabel()}"
                    } else {
                        selectedMarkerLabel()
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (selectedMarkerId in detectedNames) AccentColor else subtle,
            )
        }
        }
    }

    // ------------------------------------------------------------------ diálogos
    when (dialog) {
        ArDialog.AdjustModel -> AdjustModelDialog(
            strings = strings,
            sizeMeters = sizeMeters,
            onSize = { composer.setSizeMeters(it) },
            rotation = rotation,
            onRotation = { composer.setRotation(it) },
            elevationMeters = elevationMeters,
            onElevation = { composer.setElevationMeters(it) },
            onReset = { resetSettings() },
            onDismiss = { dialog = null },
        )

        ArDialog.Help -> InfoDialog(
            title = strings["menu_help"],
            lines = listOf(strings["help_body"]),
            onDismiss = { dialog = null },
        )

        ArDialog.About -> InfoDialog(
            title = strings["menu_about"],
            lines = aboutLines(strings),
            onDismiss = { dialog = null },
        )

        ArDialog.CreateMarker -> CreateMarkerDialog(
            onCreate = { name ->
                createMarker(name)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        ArDialog.CaptureQuality -> CaptureQualityDialog(
            current = captureMaxLongSide,
            onSelect = { quality ->
                captureMaxLongSide = quality
                preferences.captureMaxLongSide = quality
                message = strings.format(
                    "capture_quality_current",
                    strings[captureQualityLabelKey(quality)],
                )
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        ArDialog.Markers -> MarkerDialog(
            markers = markers,
            recognizedNames = detectedNames,
            selectedId = selectedMarkerId,
            onSelect = { id ->
                selectMarker(id)
                dialog = null
            },
            onCreate = { name ->
                createMarker(name)
                dialog = null
            },
            onLoadFile = {
                dialog = null
                loadMarkerFromFile()
            },
            onPrintSheet = { marker ->
                exportPrintSheet(marker)
                dialog = null
            },
            onRemove = { marker ->
                customMarkers.remove(marker.id)
                // Se era o marcador escolhido, a cena volta a aceitar qualquer um — não
                // faria sentido continuar apontando para um marcador que não existe mais.
                if (selectedMarkerId == marker.id) {
                    selectMarker(MarkerSelection.ALL_MARKERS_ID)
                }
                markerRefresh++
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

/**
 * Painel de ajustes do modelo — os mesmos controles e as mesmas faixas do app Android.
 *
 * As faixas vêm de lá de propósito: o tamanho (2 cm a 2 m) cobre de uma miniatura a um
 * móvel em escala real; as rotações (−180° a 180°) bastam para deitar, girar ou rolar um
 * modelo exportado com outro eixo para cima; e a elevação vai de −50 cm a +50 cm em passos
 * de 1 cm, que é o que tira o modelo de dentro do plano do marcador.
 *
 * O painel **não guarda estado**: ele mostra o que o [SceneComposer] está usando e escreve
 * de volta nele. É o que faz a escala automática mover o slider, em vez de deixar o painel
 * dizendo um valor e a cena usando outro.
 */
@Composable
private fun AdjustModelDialog(
    strings: Strings,
    sizeMeters: Float,
    onSize: (Float) -> Unit,
    rotation: Vec3,
    onRotation: (Vec3) -> Unit,
    elevationMeters: Float,
    onElevation: (Float) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ArDialogCard(
        title = strings["adjust_title"],
        onDismiss = onDismiss,
        // "Redefinir" fica com borda e o "OK" preenchido: quem abriu o diálogo para mexer nos
        // ajustes raramente quer "cancelar" — quer voltar ao padrão. E os dois botões se leem
        // como botões parados, sem precisar do mouse em cima (o pedido do teste em campo).
        buttons = {
            OutlinedButton(onClick = onReset) { Text(strings["adjust_reset"]) }
            Button(onClick = onDismiss) { Text("OK") }
        },
    ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AdjustSlider(
                    label = strings["adjust_size"],
                    value = sizeMeters,
                    range = RenderScene.MIN_SIZE_METERS..RenderScene.MAX_SIZE_METERS,
                    valueText = "%.2f m".format(sizeMeters),
                    onValue = onSize,
                )
                AdjustSlider(
                    label = strings["adjust_rotation_x"],
                    value = rotation.x,
                    range = -180f..180f,
                    valueText = "%.0f°".format(rotation.x),
                    onValue = { onRotation(Vec3(it, rotation.y, rotation.z)) },
                )
                AdjustSlider(
                    label = strings["adjust_rotation_y"],
                    value = rotation.y,
                    range = -180f..180f,
                    valueText = "%.0f°".format(rotation.y),
                    onValue = { onRotation(Vec3(rotation.x, it, rotation.z)) },
                )
                AdjustSlider(
                    label = strings["adjust_rotation_z"],
                    value = rotation.z,
                    range = -180f..180f,
                    valueText = "%.0f°".format(rotation.z),
                    onValue = { onRotation(Vec3(rotation.x, rotation.y, it)) },
                )
                AdjustSlider(
                    label = strings["adjust_elevation_z"],
                    value = elevationMeters,
                    range = MIN_ELEVATION_METERS..MAX_ELEVATION_METERS,
                    valueText = "%.2f m".format(elevationMeters),
                    steps = ELEVATION_STEPS,
                    onValue = onElevation,
                )
            }
    }
}

/** Rótulo, valor e deslizador — o mesmo desenho do painel do app Android. */
@Composable
private fun AdjustSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValue: (Float) -> Unit,
    steps: Int = 0,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = AccentColor)
        }
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Os comandos do modelo — a fileira que fica abaixo do vídeo e, na tela cheia, sobreposta a ele.
 *
 * Os rótulos são os textos do app Android (que já se explicam entre parênteses: "Ajustes do
 * modelo (rotação e tamanho)"), mais "Tela cheia", que é desta versão.
 *
 * `FlowRow`, e não uma fila que rola: com os rótulos longos, numa janela estreita os botões
 * passam para a linha de baixo em vez de ficarem fora de vista. O teste em campo mostrou que a
 * rolagem horizontal **não é descoberta** — ninguém adivinha que há botão escondido à direita,
 * e um botão que não se vê é um botão que não existe.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelActionButtons(
    strings: Strings,
    loading: Boolean,
    hasModel: Boolean,
    onPickModel: () -> Unit,
    onAdjust: () -> Unit,
    onAutoScale: () -> Unit,
    onCapture: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Button(enabled = !loading, onClick = onPickModel) {
            Text(strings["action_pick_model"])
        }
        OutlinedButton(enabled = hasModel, onClick = onAdjust) {
            Text(strings["action_adjust_model"])
        }
        OutlinedButton(enabled = hasModel, onClick = onAutoScale) {
            Text(strings["action_auto_scale"])
        }
        OutlinedButton(onClick = onCapture) {
            Text(strings["action_capture"])
        }
        // O botão de tela cheia **entra e sai** do modo: na tela cheia ele é o caminho de volta
        // para quem não lembra do `Esc` (a saída principal, e a que o app anuncia na Ajuda).
        OutlinedButton(onClick = onToggleFullscreen) {
            Text(strings["action_fullscreen"])
        }
        // Zoom com **um dedo só** (decisão 31): no Windows, o aplicativo recebe apenas um
        // ponteiro por vez — a pinça de dois dedos numa tela sensível ao toque nunca chega ao
        // código, e sem estes dois botões o usuário de tablet não teria como ampliar o modelo.
        // São o mesmo caminho de código da roda e das teclas (`InteractiveInput.zoomedSize`),
        // e a roda continua funcionando com o mouse. O texto é só o sinal: não há o que traduzir.
        OutlinedButton(enabled = hasModel, onClick = { onZoom(-1f) }) {
            Text("−")
        }
        OutlinedButton(enabled = hasModel, onClick = { onZoom(1f) }) {
            Text("+")
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
        }
    }
}

/**
 * Tempo sem movimento do mouse sobre a barra até ela sumir, em milissegundos (na tela cheia).
 *
 * Três segundos é o suficiente para ler os botões e clicar, e curto o bastante para a imagem
 * ficar limpa antes de uma captura.
 */
private const val CONTROLS_IDLE_MILLIS = 3000L

/**
 * Tempo que um aviso sobre a imagem fica na tela, em milissegundos.
 *
 * Dois segundos e meio: tempo de ler uma frase curta e ver o modelo se ajustar, e curto o
 * bastante para não ficar no caminho do que o usuário quer ver.
 */
private const val TOAST_MILLIS = 2500L

/** Linha permanente de estado do modelo (ou o aviso de que não há nenhum). */
private fun statusOf(strings: Strings, modelName: String?): String =
    if (modelName == null) {
        strings["status_no_model"]
    } else {
        strings.format("diagnostics_model", modelName)
    }

/** Cor das mensagens de erro (a mesma do resto da interface). */
private val ErrorColor = Color(0xFFFF8A80)

/** Faixas da elevação; o tamanho usa as de `RenderScene`, que a escala automática respeita. */
private const val MIN_ELEVATION_METERS = -0.5f
private const val MAX_ELEVATION_METERS = 0.5f

/** Elevação em passos de 1 cm entre −50 cm e +50 cm. */
private const val ELEVATION_STEPS = 99

/** Tempo que uma mensagem de resultado fica visível (o mesmo do app Android). */
private const val MESSAGE_MILLIS = 8_000L

/** Depois deste tempo, o carregamento é rotulado como "modelo grande" (como no Android). */
private const val SLOW_LOAD_HINT_MILLIS = 20_000L

/** Um diálogo por vez: são telas secundárias, e duas abertas não fariam sentido. */
private enum class ArDialog { AdjustModel, Help, About, CaptureQuality, Markers, CreateMarker }
