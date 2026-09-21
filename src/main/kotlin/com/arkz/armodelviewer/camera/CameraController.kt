/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.camera

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bytedeco.opencv.global.opencv_videoio.CAP_MSMF
import org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_HEIGHT
import org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_WIDTH
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_videoio.VideoCapture
import java.awt.image.BufferedImage
import java.util.Date

/** Um quadro capturado da webcam, já convertido para a interface. */
data class CameraFrame(
    val deviceIndex: Int,
    val image: BufferedImage,
    val capturedAt: Date,
)

/** Estado da captura — é o que a barra de status da interface informa. */
sealed interface CameraStatus {

    /** Nada aberto (estado inicial e depois de [CameraController.stop]). */
    data object Stopped : CameraStatus

    /** Abrindo o dispositivo. */
    data object Starting : CameraStatus

    /** Entregando quadros. */
    data class Running(
        val deviceIndex: Int,
        val width: Int,
        val height: Int,
    ) : CameraStatus

    /** Não foi possível capturar — a mensagem explica o motivo técnico. */
    data class Failed(val message: String) : CameraStatus
}

/**
 * Controle da webcam.
 *
 * Equivale, no desktop, ao que o `ARSceneView` fazia no Android: manter a câmera
 * aberta e entregar quadros continuamente. As diferenças de plataforma são
 * justamente as que este arquivo isola do resto do app:
 *
 *  * a captura roda numa **thread própria** (o ARCore tinha a dele) e nunca na
 *    thread de interface — converter um quadro de 1280×720 custa alguns
 *    milissegundos, e fazer isso na UI derrubaria a taxa de quadros;
 *  * o `VideoCapture` é um recurso **nativo**: precisa ser liberado
 *    explicitamente, inclusive quando a janela fecha no meio de uma captura —
 *    o mesmo cuidado que o app Android documenta com o `ImageReader` da captura
 *    de tela;
 *  * ao abrir, o app tenta **mais de um backend** (MSMF → DirectShow → qualquer),
 *    porque webcams baratas recusam o Media Foundation com frequência.
 *
 * @property deviceIndex índice do dispositivo (veja [CameraDevices.enumerate]).
 * @property backend primeiro backend a tentar; [CameraDevices.backends] traz a
 *   lista completa, tentada em sequência.
 * @property preferredWidth/preferredHeight resolução **pedida** ao dispositivo.
 *   1280×720 é o alvo porque a detecção de marcador (etapa 3) trabalha com pontos
 *   de interesse, que dependem de pixels: a 640×480 um marcador impresso visto a
 *   1 m ocupa poucos pixels para um casamento confiável. O valor pedido é apenas
 *   uma sugestão — vale o que a câmera entregar, e é o quadro real que o app
 *   reporta na barra de status.
 * @property maxFramesPerSecond limite do laço de captura (30 é o que o app usa; a
 *   detecção de marcador da etapa 3 também roda a 30 quadros por segundo).
 */
class CameraController(
    private val deviceIndex: Int = 0,
    private val backend: Int = CAP_MSMF,
    private val preferredWidth: Int = DEFAULT_PREFERRED_WIDTH,
    private val preferredHeight: Int = DEFAULT_PREFERRED_HEIGHT,
    private val maxFramesPerSecond: Int = 30,
    private val onLog: (String) -> Unit = {},
) : AutoCloseable {

    private val _frames = MutableStateFlow<CameraFrame?>(null)

    /** Último quadro capturado (`null` enquanto nada chegou). */
    val frames: StateFlow<CameraFrame?> = _frames.asStateFlow()

    private val _status = MutableStateFlow<CameraStatus>(CameraStatus.Stopped)

    /** Estado atual da captura. */
    val status: StateFlow<CameraStatus> = _status.asStateFlow()

    @Volatile
    private var capture: VideoCapture? = null

    @Volatile
    private var running = false

    private var thread: Thread? = null

    /**
     * Abre o dispositivo e começa a capturar.
     *
     * A abertura é **síncrona**: quando ela falha, o erro já volta por aqui e a
     * interface pode mostrar "câmera não encontrada" sem esperar por um quadro que
     * nunca viria.
     */
    fun start(): Result<Unit> {
        if (running) return Result.success(Unit)

        _status.value = CameraStatus.Starting

        val opened = openDevice()
        if (opened == null) {
            val message = "Nenhum backend conseguiu abrir a câmera $deviceIndex."
            onLog(message)
            _status.value = CameraStatus.Failed(message)
            return Result.failure(IllegalStateException(message))
        }

        val probe = Mat()
        val hasFrame = runCatching { opened.read(probe) && !probe.empty() }.getOrDefault(false)
        val width = if (hasFrame) probe.cols() else 0
        val height = if (hasFrame) probe.rows() else 0
        probe.release()

        if (!hasFrame) {
            runCatching { opened.release() }
            val message = "A câmera $deviceIndex abriu, mas não entregou nenhum quadro " +
                "(outro programa pode estar usando a webcam)."
            onLog(message)
            _status.value = CameraStatus.Failed(message)
            return Result.failure(IllegalStateException(message))
        }

        _status.value = CameraStatus.Running(deviceIndex, width, height)
        capture = opened
        running = true
        thread = Thread({ captureLoop(opened) }, "arkz-camera-$deviceIndex").apply {
            isDaemon = true
            start()
        }

        return Result.success(Unit)
    }

    /** Fecha a câmera e encerra a thread de captura. Seguro de chamar várias vezes. */
    fun stop() {
        running = false
        thread?.join(JOIN_TIMEOUT_MILLIS)
        thread = null

        runCatching { capture?.release() }
        capture = null
        _frames.value = null
        _status.value = CameraStatus.Stopped
    }

    override fun close() = stop()

    /** Abre o dispositivo tentando todos os backends conhecidos, em ordem. */
    private fun openDevice(): VideoCapture? {
        val backends = listOf(backend) + CameraDevices.backends.filterNot { it == backend }

        backends.forEach { candidate ->
            val opened = VideoCapture()
            val success = runCatching { opened.open(deviceIndex, candidate) && opened.isOpened() }
                .getOrDefault(false)
            if (success) {
                onLog("Câmera $deviceIndex aberta com o backend $candidate.")
                applyPreferredResolution(opened)
                return opened
            }
            runCatching { opened.release() }
        }

        return null
    }

    /**
     * Pede a resolução preferida ao dispositivo.
     *
     * A webcam pode ignorar o pedido (não suportar o formato, ou estar com o
     * formato fixado por outro programa) — e não há problema: quem manda é o quadro
     * que ela entregar, e é o tamanho real dele que aparece na barra de status.
     */
    private fun applyPreferredResolution(opened: VideoCapture) {
        runCatching {
            opened.set(CAP_PROP_FRAME_WIDTH, preferredWidth.toDouble())
            opened.set(CAP_PROP_FRAME_HEIGHT, preferredHeight.toDouble())
        }
    }

    /**
     * Laço de captura: lê um quadro, publica e espera o intervalo do alvo de FPS.
     *
     * A espera é pelo tempo RESTANTE do intervalo (e não um `sleep` fixo): com
     * webcams lentas, um `sleep` fixo somaria o tempo de captura ao tempo de espera
     * e os 30 fps virariam 10.
     */
    private fun captureLoop(opened: VideoCapture) {
        val frame = Mat()
        val frameIntervalMillis = 1_000L / maxFramesPerSecond.coerceAtLeast(1)
        var consecutiveFailures = 0

        try {
            while (running) {
                val startedAt = System.nanoTime()
                val read = runCatching { opened.read(frame) && !frame.empty() }.getOrDefault(false)

                if (read) {
                    consecutiveFailures = 0
                    _frames.value = CameraFrame(
                        deviceIndex = deviceIndex,
                        image = frame.toArgbImage(),
                        capturedAt = Date(),
                    )
                } else {
                    consecutiveFailures++
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        val message = "A câmera $deviceIndex parou de entregar quadros " +
                            "(desconectada ou ocupada por outro programa)."
                        onLog(message)
                        _status.value = CameraStatus.Failed(message)
                        running = false
                    }
                }

                val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
                val remaining = frameIntervalMillis - elapsedMillis
                if (remaining > 0) {
                    Thread.sleep(remaining)
                }
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            onLog(
                "Falha na captura da câmera $deviceIndex: " +
                    (error.message ?: error.javaClass.simpleName),
            )
            _status.value = CameraStatus.Failed(error.message ?: error.javaClass.simpleName)
        } finally {
            frame.release()
            runCatching { opened.release() }
        }
    }

    private companion object {
        /** Falhas seguidas antes de considerar a câmera perdida (~0,3 s a 30 fps). */
        const val MAX_CONSECUTIVE_FAILURES = 10

        /** Tempo máximo esperando a thread de captura terminar ao fechar. */
        const val JOIN_TIMEOUT_MILLIS = 1_000L

        /** Resolução pedida por padrão (a detecção de marcador ganha com pixels). */
        const val DEFAULT_PREFERRED_WIDTH = 1280
        const val DEFAULT_PREFERRED_HEIGHT = 720
    }
}
