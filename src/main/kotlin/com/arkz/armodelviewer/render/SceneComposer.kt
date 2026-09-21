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

import com.arkz.armodelviewer.ar.CameraIntrinsics
import com.arkz.armodelviewer.ar.DetectedMarker
import com.arkz.armodelviewer.ar.TrackingState
import com.arkz.armodelviewer.model.Vec3
import com.arkz.armodelviewer.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Liga a captura da câmera e a detecção de marcador **ao renderizador**, fora da thread de
 * interface.
 *
 * ## O que ele resolve
 *
 * Desenhar um quadro é operação de CPU e GPU (montar a cena e ler os pixels de volta custa
 * alguns milissegundos): fazer isso na thread de interface derrubaria a taxa de quadros.
 * Aqui os quadros da câmera são entregues por [submit] — que é **barato** e pode ser
 * chamado da interface — e o desenho acontece numa corrotina própria
 * ([Dispatchers.Default]).
 *
 * ## "O último quadro vale"
 *
 * Os quadros entram por um canal **conflado** (`Channel.CONFLATED`): se o desenho estiver
 * mais lento do que a captura, os quadros intermediários são **descartados** em vez de
 * formar fila. É a mesma regra do ARCore no Android e evita o pior sintoma possível numa
 * cena de RA — a imagem na tela ficar cada vez mais atrás do mundo real.
 *
 * ## Tudo numa thread só, e por quê
 *
 * **O motor do Filament não aceita chamadas concorrentes.** A criação do renderizador, o
 * ajuste de tamanho, a troca de intrínsecos e o desenho acontecem todos numa **thread
 * própria** ([renderThread]) — e não no conjunto de threads do `Dispatchers.Default`, em
 * que duas tarefas podem cair em threads diferentes e chamar o motor ao mesmo tempo. Um
 * `resize` (que destrói e recria o `SwapChain`) executado no meio de um `beginFrame` de
 * outra thread **derruba o processo com asserção nativa**, sem exceção e sem mensagem na
 * interface: foi exatamente esse o defeito que o primeiro teste com a janela de verdade
 * revelou.
 *
 * ## Por que o renderizador entra por uma fábrica
 *
 * Nos testes entra um renderizador de mentira, e toda a lógica daqui — o laço, o descarte
 * de quadros, os ajustes do painel, o tratamento de falha — roda num teste comum, sem GPU
 * nenhuma. Em produção entra o [FilamentRenderer].
 *
 * ## Ciclo de vida
 *
 * [start] cria o renderizador na primeira execução (operação cara, por isso fora da
 * interface) e [close] para o laço e libera o renderizador. Se o renderizador não puder
 * ser criado, a falha fica em [error] — a interface mostra a mensagem em vez de morrer.
 */
class SceneComposer(
    private val rendererFactory: () -> SceneRenderer = { FilamentRenderer() },
    private val onLog: (String) -> Unit = { message -> AppLog.info(message) },
) : AutoCloseable {

    /** Um quadro da câmera com os marcadores reconhecidos nele. */
    class Input(
        val background: BackgroundFrame,
        val markers: List<DetectedMarker>,
    )

    private val inputs = Channel<Input>(Channel.CONFLATED)

    private val _frame = MutableStateFlow<SceneFrame?>(null)
    private val _error = MutableStateFlow<String?>(null)
    private val _backend = MutableStateFlow<String?>(null)

    /** Último quadro desenhado (`null` enquanto o primeiro não sai). */
    val frame: StateFlow<SceneFrame?> = _frame.asStateFlow()

    /** Falha ao criar ou ao usar o renderizador (`null` = tudo bem). */
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Nome do backend em uso — aparece no diagnóstico copiável. */
    val backend: StateFlow<String?> = _backend.asStateFlow()

    // Ajustes do painel: escritos pela interface, lidos pelo laço. `@Volatile` basta —
    // são valores de 32 bits ou referências imutáveis, e cada leitura já quer o último.
    @Volatile
    private var model: PreparedModel? = null

    @Volatile
    private var selectedMarkerId: String = MarkerSelection.ALL_MARKERS_ID

    /** Últimos marcadores reconhecidos — é o que a escala automática mede. */
    @Volatile
    private var lastMarkers: List<DetectedMarker> = emptyList()

    // Os ajustes moram aqui, e não na interface: assim o slider, a escala automática e o
    // desenho leem e escrevem no MESMO lugar (sem dois estados para manter em sincronia —
    // o defeito clássico em que o slider mostra um valor e a cena usa outro).
    private val _sizeMeters = MutableStateFlow(RenderScene.DEFAULT_SIZE_METERS)
    private val _rotationDegrees = MutableStateFlow(Vec3.ZERO)
    private val _elevationMeters = MutableStateFlow(0f)
    private val _offsetMeters = MutableStateFlow(Vec3.ZERO)

    /** Tamanho atual do modelo (maior dimensão, em metros). */
    val sizeMeters: StateFlow<Float> = _sizeMeters.asStateFlow()

    /** Rotação atual do painel, em graus, nos eixos do referencial do marcador. */
    val rotationDegrees: StateFlow<Vec3> = _rotationDegrees.asStateFlow()

    /** "Elevação Z" atual, em metros. */
    val elevationMeters: StateFlow<Float> = _elevationMeters.asStateFlow()

    /** Deslocamento atual no plano da figura (o arrasto do "modo livre"), em metros. */
    val offsetMeters: StateFlow<Vec3> = _offsetMeters.asStateFlow()

    private var requestedWidth = 0
    private var requestedHeight = 0
    private var requestedIntrinsics: CameraIntrinsics? = null
    private val requests = Mutex()

    /** O renderizador em uso (criado dentro do laço; `null` antes disso). */
    @Volatile
    private var renderer: SceneRenderer? = null

    private val job = SupervisorJob()

    /**
     * Thread única do renderizador.
     *
     * É `daemon` para que um encerramento abrupto da janela não fique preso esperando esta
     * thread, e tem nome próprio para aparecer no log e no depurador.
     */
    private val renderThread = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "arkz-render").apply { isDaemon = true }
    }

    private val scope = CoroutineScope(job + renderThread.asCoroutineDispatcher())
    private val started = AtomicBoolean(false)

    /** Inicia o laço de desenho (idempotente: chamar duas vezes não cria dois laços). */
    fun start() {
        if (started.compareAndSet(false, true)) {
            scope.launch { runLoop() }
        }
    }

    /**
     * Entrega o quadro atual da câmera e os marcadores reconhecidos.
     *
     * Não bloqueia: se o desenho estiver ocupado, o quadro ainda não desenhado é
     * substituído por este.
     */
    fun submit(background: BackgroundFrame, markers: List<DetectedMarker>) {
        inputs.trySend(Input(background, markers))
    }

    /** Modelo a ancorar (`null` = nenhum; a cena mostra só o vídeo da câmera). */
    fun setModel(prepared: PreparedModel?) {
        model = prepared
    }

    /**
     * Tamanho da maior dimensão do modelo, em metros.
     *
     * O valor é limitado à faixa do painel ([RenderScene.MIN_SIZE_METERS] a
     * [RenderScene.MAX_SIZE_METERS]) — o mesmo limite que a escala automática respeita, o
     * que garante que o slider sempre consegue mostrar o valor que a cena está usando.
     */
    fun setSizeMeters(meters: Float) {
        _sizeMeters.value = meters.coerceIn(
            RenderScene.MIN_SIZE_METERS,
            RenderScene.MAX_SIZE_METERS,
        )
    }

    /** Rotação do painel de ajustes, em graus, nos eixos do referencial do marcador. */
    fun setRotation(degrees: Vec3) {
        _rotationDegrees.value = degrees
    }

    /** Deslocamento na normal do marcador ("Elevação Z"), em metros. */
    fun setElevationMeters(meters: Float) {
        _elevationMeters.value = meters
    }

    /**
     * Deslocamento no **plano da figura** (o arrasto do "modo livre"), em metros.
     *
     * **X (largura)** e **Z (altura NA imagem)** são limitados à faixa do arrasto
     * ([InteractiveInput.MAX_PAN_METERS]): dá para pôr o modelo ao lado ou à frente da figura,
     * mas não para perdê-lo de vista. O **Y é a normal** do marcador e fica sempre em **zero**:
     * o arrasto nunca tira o modelo do papel (decisão 34).
     */
    fun setOffsetMeters(offset: Vec3) {
        _offsetMeters.value = Vec3(
            x = offset.x.coerceIn(-InteractiveInput.MAX_PAN_METERS, InteractiveInput.MAX_PAN_METERS),
            y = 0f,
            z = offset.z.coerceIn(-InteractiveInput.MAX_PAN_METERS, InteractiveInput.MAX_PAN_METERS),
        )
    }

    /**
     * Qual marcador ancora o modelo: [MarkerSelection.ALL_MARKERS_ID] (o padrão) aceita
     * qualquer um; qualquer outro valor casa com o **nome** do marcador.
     */
    fun setSelectedMarkerId(id: String) {
        selectedMarkerId = id.ifBlank { MarkerSelection.ALL_MARKERS_ID }
    }

    /**
     * "Escala automática": ajusta o modelo à **largura real do marcador** reconhecido.
     *
     * Usa o último quadro analisado (o mesmo que está na tela), o que faz o ajuste valer
     * para a figura que o usuário tem à frente — e é por isso que ele não precisa escolher
     * um tamanho no slider.
     *
     * @return o tamanho aplicado, ou `null` quando não há marcador com largura utilizável
     *   (a interface avisa e mantém o tamanho atual).
     */
    fun autoScale(): Float? {
        val marker = MarkerSelection.pick(lastMarkers, selectedMarkerId) ?: return null
        val size = AutoScale.sizeMetersFor(marker) ?: return null
        setSizeMeters(size)
        return size
    }

    /** Tamanho do quadro desenhado (o do painel da interface). */
    fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return

        scope.launch {
            requests.withLock {
                requestedWidth = width
                requestedHeight = height
                renderer?.resize(width, height)
            }
        }
    }

    /**
     * Intrínsecos da câmera de vídeo.
     *
     * Vêm do tamanho REAL do quadro que a webcam entrega — foi com eles que a detecção
     * calculou a pose, então é com eles que a cena tem de projetar (veja
     * [CameraProjection]).
     */
    fun setIntrinsics(intrinsics: CameraIntrinsics) {
        scope.launch {
            requests.withLock {
                requestedIntrinsics = intrinsics
                renderer?.updateIntrinsics(intrinsics)
            }
        }
    }

    override fun close() {
        inputs.close()
        scope.cancel()
        renderThread.shutdown()
        _frame.value = null
    }

    // ------------------------------------------------------------------ o laço

    private suspend fun runLoop() {
        // Espera os pedidos em voo (tamanho e intrínsecos) antes de criar o renderizador:
        // a interface pede o tamanho no primeiro quadro da câmera, e o Filament precisa
        // nascer já com a projeção certa — senão o vídeo aparece esticado no começo.
        requests.withLock { }

        val created = try {
            rendererFactory()
        } catch (error: Throwable) {
            val message = "Não foi possível iniciar a renderização: " +
                (error.message ?: error.javaClass.simpleName)
            onLog(message)
            _error.value = message
            return
        }

        renderer = created
        applyRequests(created)
        _backend.value = created.backend
        onLog("Renderizador iniciado: ${created.backend}")

        try {
            for (input in inputs) {
                lastMarkers = input.markers

                val scene = RenderScene(
                    background = input.background,
                    // Só o marcador RASTREADO recebe o modelo — e, se o usuário escolheu um
                    // marcador específico, só ele (veja `MarkerSelection`).
                    marker = MarkerSelection.pick(input.markers, selectedMarkerId),
                    model = model,
                    sizeMeters = _sizeMeters.value,
                    rotationDegrees = _rotationDegrees.value,
                    elevationMeters = _elevationMeters.value,
                    offsetMeters = _offsetMeters.value,
                )

                val drawn = created.render(scene)
                if (drawn != null) _frame.value = drawn
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val message = "A renderização parou: " + (error.message ?: error.javaClass.simpleName)
            onLog(message)
            _error.value = message
        } finally {
            runCatching { created.close() }
            renderer = null
        }
    }

    /**
     * Aplica tamanho e intrínsecos pedidos **antes** de o renderizador existir.
     *
     * Sem isto, os primeiros quadros sairiam com o tamanho padrão do renderizador e com a
     * projeção errada (o vídeo apareceria esticado por um instante, e um modelo já
     * carregado poderia ser desenhado fora do lugar).
     */
    private fun applyRequests(renderer: SceneRenderer) {
        if (requestedWidth > 0 && requestedHeight > 0) {
            renderer.resize(requestedWidth, requestedHeight)
        }
        requestedIntrinsics?.let { renderer.updateIntrinsics(it) }
    }
}
