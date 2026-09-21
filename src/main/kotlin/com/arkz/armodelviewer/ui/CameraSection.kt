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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.arkz.armodelviewer.ar.CameraIntrinsics
import com.arkz.armodelviewer.ar.DetectedMarker
import com.arkz.armodelviewer.ar.MarkerDetector
import com.arkz.armodelviewer.camera.CameraController
import com.arkz.armodelviewer.camera.CameraDevice
import com.arkz.armodelviewer.camera.CameraDevices
import com.arkz.armodelviewer.camera.CameraFrame
import com.arkz.armodelviewer.camera.CameraStatus
import com.arkz.armodelviewer.markers.CustomMarkerStore
import com.arkz.armodelviewer.markers.MarkerCatalog
import com.arkz.armodelviewer.markers.MarkerDefinition
import com.arkz.armodelviewer.render.SceneFrame
import com.arkz.armodelviewer.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Seção de câmera: descobre os dispositivos, abre o escolhido e mostra o vídeo ao
 * vivo.
 *
 * É o equivalente desktop do que o `ARSceneView` fazia no Android (abrir a câmera
 * e entregar quadros). A diferença é que aqui existe **escolha de dispositivo**,
 * porque num computador pode haver mais de uma webcam — e a do notebook pode
 * estar coberta pela tampa.
 *
 * O estado da captura é publicado por `StateFlow` e consumido com
 * `collectAsState`: os quadros chegam de uma thread própria (nunca da thread de
 * interface) e a recomposição acontece a cada quadro publicado.
 *
 * @param onFrame recebe cada quadro capturado — é por aqui que a detecção de
 *   marcador (etapa 3) vai se ligar, sem que esta seção precise saber dela.
 */
@Composable
fun CameraSection(
    modifier: Modifier = Modifier,
    onDetections: ((List<DetectedMarker>) -> Unit)? = null,
    /** Cada quadro capturado, com os marcadores reconhecidos nele (usado pela etapa 4). */
    onFrame: ((CameraFrame, List<DetectedMarker>) -> Unit)? = null,
    /** Quadro já desenhado pelo renderizador; quando existe, é ele que aparece. */
    sceneFrame: SceneFrame? = null,
    /** Há modelo carregado? Com modelo, a caixa ciano não é desenhada (o modelo já diz o mesmo). */
    hasModel: Boolean = false,
    /**
     * Muda quando a lista de marcadores cadastrados muda (um marcador criado ou carregado,
     * por exemplo): a detecção é refeita com a lista nova. Sem isso, um marcador recém-criado
     * só passaria a ser reconhecido ao reabrir o aplicativo.
     */
    markersRefreshToken: Int = 0,
) {
    val strings = LocalStrings.current

    var devices by remember { mutableStateOf<List<CameraDevice>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var refreshToken by remember { mutableStateOf(0) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    // Marcadores cadastrados (embutidos + personalizados do usuário). A leitura do
    // disco fica fora da thread de interface.
    var markers by remember { mutableStateOf<List<MarkerDefinition>>(emptyList()) }
    LaunchedEffect(refreshToken, markersRefreshToken) {
        markers = withContext(Dispatchers.IO) {
            MarkerCatalog.loadBundled() + CustomMarkerStore().loadDefinitions()
        }
    }

    // A enumeração abre cada dispositivo (centenas de milissegundos): fora da
    // thread de interface, e refeita quando o usuário pede "procurar novamente".
    LaunchedEffect(refreshToken) {
        scanning = true
        val found = withContext(Dispatchers.IO) { CameraDevices.enumerate() }
        devices = found
        selectedIndex = CameraDevices.preferred(found, selectedIndex)?.index
        scanning = false
    }

    val controller = remember(selectedIndex) {
        selectedIndex?.let { index ->
            CameraController(deviceIndex = index, onLog = AppLog::info)
        }
    }
    val frame by (controller?.frames ?: remember { EmptyCameraFlow.frames }).collectAsState()
    val status by (controller?.status ?: remember { EmptyCameraFlow.status }).collectAsState()

    DisposableEffect(controller) {
        controller?.start()
        onDispose { controller?.stop() }
    }

    // Intrínsecos e criação do detector dependem do tamanho REAL do quadro.
    val frameWidth = (status as? CameraStatus.Running)?.width ?: 0
    val frameHeight = (status as? CameraStatus.Running)?.height ?: 0
    var detections by remember { mutableStateOf<List<DetectedMarker>>(emptyList()) }

    // O detector vive **dentro** desta corrotina: é criado aqui, usado enquanto o
    // fluxo de quadros durar e fechado no `finally` — garantidamente depois que a
    // última detecção terminou.
    //
    // Criar e fechar o detector "por fora" (num `DisposableEffect`, por exemplo)
    // permite que as matrizes nativas dos descritores sejam liberadas **no meio** de
    // uma detecção: o `BFMatcher` receberia então uma matriz vazia no lugar da
    // referência, e o OpenCV responde a isso com uma **asserção nativa** que
    // interrompe o processo (`batchDistance: type == src2.type() && src1.cols ==
    // src2.cols ...`) em vez de devolver um erro tratável.
    LaunchedEffect(markers, frameWidth, frameHeight, controller) {
        val camera = controller ?: return@LaunchedEffect
        if (markers.isEmpty() || frameWidth <= 0 || frameHeight <= 0) return@LaunchedEffect

        AppLog.info(
            "Preparando a detecção: ${markers.size} marcador(es) para quadros de " +
                "${frameWidth}×$frameHeight.",
        )
        val detector = withContext(Dispatchers.Default) {
            MarkerDetector(markers, CameraIntrinsics.forFrame(frameWidth, frameHeight))
        }

        try {
            // O `StateFlow` da captura é **conflado** de propósito: se a detecção
            // demorar mais que o intervalo entre quadros, os intermediários são
            // descartados em vez de formar fila (o mesmo "último quadro vale" do
            // ARCore).
            camera.frames.collect { cameraFrame ->
                val image = cameraFrame?.image ?: return@collect
                val found = withContext(Dispatchers.Default) { detector.detect(image) }
                detections = found
                onDetections?.invoke(found)
                // A cena de RA se liga aqui: o MESMO quadro que foi analisado vai para o
                // renderizador, junto com quem foi reconhecido nele. Assim a pose e a
                // imagem que a originou são sempre do mesmo instante.
                onFrame?.invoke(cameraFrame, found)
            }
        } finally {
            // `NonCancellable`: o fechamento não pode ser interrompido no meio da
            // liberação dos recursos nativos.
            withContext(NonCancellable) { detector.close() }
        }
    }

    // Diagnóstico pelo log: registra as MUDANÇAS de estado da detecção e avisa uma
    // única vez quando nada é reconhecido — é o que permite saber, sem adivinhação, se o
    // problema é "o marcador não é encontrado".
    var everDetected by remember { mutableStateOf(false) }
    var lastSignature by remember { mutableStateOf("") }

    LaunchedEffect(detections) {
        // A assinatura considera apenas QUEM está visível e em que estado. Distância e
        // número de pontos variam a cada quadro: usá-los aqui transformaria o log num
        // dilúvio (foram 371 linhas em 30 segundos antes deste ajuste).
        val signature = detections.sortedBy { it.index }
            .joinToString(", ") { "${it.name}:${it.trackingState}" }

        if (signature != lastSignature) {
            lastSignature = signature

            if (signature.isEmpty()) {
                AppLog.info("Nenhum marcador reconhecido no quadro atual.")
            } else {
                everDetected = true
                AppLog.info(
                    "Detecção: " + detections.joinToString(", ") { detected ->
                        "${detected.name} em ${detected.trackingState} a " +
                            "%.2f m (${detected.inlierCount} pontos)".format(
                                detected.centerPose.distanceMeters,
                            )
                    },
                )
            }
        }
    }

    LaunchedEffect(frameWidth, frameHeight) {
        if (frameWidth <= 0 || frameHeight <= 0) return@LaunchedEffect
        delay(NOTHING_DETECTED_HINT_MILLIS)
        if (!everDetected) {
            AppLog.warn(
                "Nenhum marcador reconhecido em ${NOTHING_DETECTED_HINT_MILLIS / 1_000} s de " +
                    "captura. Confira: a figura está impressa com ~15 cm de largura, a " +
                    "30–60 cm da câmera, com boa iluminação e ocupando boa parte do quadro?",
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CameraSectionHeader()
        CameraSectionBody(
            scanning = scanning,
            devices = devices,
            selectedIndex = selectedIndex,
            frame = frame,
            detections = detections,
            sceneFrame = sceneFrame,
            hasModel = hasModel,
            statusText = statusText(status, strings),
            onSelect = { index -> selectedIndex = index },
            onRescan = { refreshToken++ },
        )
    }
}

@Composable
private fun CameraSectionHeader() {
    Text(
        text = LocalStrings.current["marker_preview_description"],
        style = MaterialTheme.typography.titleMedium,
    )
}

/**
 * Texto de estado da captura (o "Câmera 1 · 1280×720" ou o motivo da falha).
 *
 * A mensagem de falha vem do próprio controlador, em linguagem técnica, porque ela
 * descreve o que aconteceu com o dispositivo — o texto de ajuda traduzido
 * (`permission_*`) aparece ao lado.
 */
private fun statusText(status: CameraStatus, strings: Strings): String = when (status) {
    is CameraStatus.Running -> strings.format(
        "camera_status_running",
        status.deviceIndex + 1,
        status.width,
        status.height,
    )
    is CameraStatus.Failed -> status.message
    CameraStatus.Starting -> strings["status_starting_camera"]
    CameraStatus.Stopped -> ""
}

/** Fluxo vazio usado enquanto nenhum dispositivo foi escolhido. */
private object EmptyCameraFlow {
    val frames = kotlinx.coroutines.flow.MutableStateFlow<CameraFrame?>(null)
    val status = kotlinx.coroutines.flow.MutableStateFlow(
        com.arkz.armodelviewer.camera.CameraStatus.Stopped,
    )
}

/** Depois deste tempo sem nenhum reconhecimento, o log traz uma dica do que conferir. */
private const val NOTHING_DETECTED_HINT_MILLIS = 15_000L

@Composable
private fun CameraSectionBody(
    scanning: Boolean,
    devices: List<CameraDevice>,
    selectedIndex: Int?,
    frame: CameraFrame?,
    detections: List<DetectedMarker>,
    sceneFrame: SceneFrame?,
    hasModel: Boolean,
    statusText: String,
    onSelect: (Int) -> Unit,
    onRescan: () -> Unit,
) {
    val strings = LocalStrings.current

    when {
        scanning -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp))
            Text(strings["status_starting_camera"], style = MaterialTheme.typography.bodySmall)
        }

        devices.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(strings["permission_title"], style = MaterialTheme.typography.titleSmall)
            Text(strings["permission_rationale"], style = MaterialTheme.typography.bodySmall)
            Text(strings["permission_denied_hint"], style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRescan) {
                Text(strings["permission_grant"])
            }
        }

        else -> {
            if (devices.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    devices.forEach { device ->
                        TextButton(onClick = { onSelect(device.index) }) {
                            Text(
                                text = device.label,
                                color = if (device.index == selectedIndex) {
                                    AccentColor
                                } else {
                                    Color.White
                                },
                            )
                        }
                    }
                }
            }

            val image = frame?.image
            if (image != null) {
                // Os caches são reaproveitados a cada quadro: sem eles, cada quadro
                // criaria arranjos novos (pixels e bytes) e o vídeo engasgaria.
                val cache = remember { CameraBitmapCache() }
                val sceneCache = remember { SceneImageCache() }

                // Com um modelo carregado, quem aparece é a CENA desenhada (o vídeo da
                // câmera com o modelo ancorado) — é o quadro que o renderizador produziu.
                // Sem modelo, o vídeo cru, que é o mesmo caminho de antes da etapa 4.
                val bitmap = sceneFrame?.let { sceneCache.toImageBitmap(it) }
                    ?: cache.toImageBitmap(image)
                val width = sceneFrame?.width ?: image.width
                val height = sceneFrame?.height ?: image.height

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(width.toFloat() / height.toFloat())
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // A caixa ciano fica sobre o vídeo e **não** faz parte dele: a
                    // captura de tela (etapa 7) compõe a cena sem a interface.
                    //
                    // Com o modelo carregado, a caixa **desaparece** (pedido do teste em
                    // campo): ela avisa que a detecção está funcionando enquanto não há nada
                    // ancorado; depois disso, o modelo ancorado já diz o mesmo, e a caixa só
                    // polui a figura que o usuário quer ver.
                    if (!hasModel) {
                        MarkerOverlay(
                            markers = detections,
                            imageWidth = width,
                            imageHeight = height,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
            }

            // Aqui fica apenas o estado da **câmera** (pronta, aberta, sem permissão…). As
            // mensagens de marcador ("Marcador carregado: …") saíram da área do vídeo para a
            // barra de status, a pedido do teste em campo: sobre a imagem elas competiam com o
            // modelo, e o lugar do estado é o rodapé.
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

