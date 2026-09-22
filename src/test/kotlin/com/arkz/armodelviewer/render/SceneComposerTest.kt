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
import com.arkz.armodelviewer.ar.Pose
import com.arkz.armodelviewer.ar.TrackingMethod
import com.arkz.armodelviewer.ar.TrackingState
import com.arkz.armodelviewer.ar.Vec2
import com.arkz.armodelviewer.model.Vec3
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes do [SceneComposer] — a ligação entre a câmera e o renderizador.
 *
 * Usam um renderizador **de mentira**: o que está em teste aqui é a lógica da ligação (o
 * laço, o descarte de quadros atrasados, os ajustes do painel, a escolha do marcador, o
 * tratamento de falha e o ciclo de vida). Nenhum teste desta classe abre GPU — os que
 * desenham de verdade estão em [FilamentRendererTest].
 */
class SceneComposerTest {

    /** Renderizador de mentira: anota o que recebeu e devolve um quadro sintético. */
    private class FakeRenderer(
        private val backendName: String = "falso",
        failOnCreate: Boolean = false,
    ) : SceneRenderer {

        val scenes = mutableListOf<RenderScene>()
        val resizes = mutableListOf<Pair<Int, Int>>()
        var intrinsics: CameraIntrinsics? = null
        var closed = false
        var renderCount = 0

        override val backend: String get() = backendName

        override var width: Int = 0
            private set

        override var height: Int = 0
            private set

        init {
            if (failOnCreate) error("sem GPU nesta máquina de mentira")
        }

        override fun resize(width: Int, height: Int) {
            resizes += width to height
            this.width = width
            this.height = height
        }

        override fun updateIntrinsics(intrinsics: CameraIntrinsics) {
            this.intrinsics = intrinsics
        }

        override fun render(scene: RenderScene): SceneFrame {
            scenes += scene
            renderCount++
            val w = width.coerceAtLeast(1)
            val h = height.coerceAtLeast(1)
            return SceneFrame(ByteArray(w * h * 4), w, h)
        }

        override fun close() {
            closed = true
        }
    }

    private fun background(size: Int = 8) = BackgroundFrame(ByteArray(size * size * 4), size, size)

    private fun marker(
        state: TrackingState,
        z: Float = -0.5f,
        name: String = "marker_02",
        extentX: Float = 0.15f,
    ) = DetectedMarker(
        index = 1,
        name = name,
        label = "Marcador $name",
        trackingState = state,
        trackingMethod = TrackingMethod.FULL_TRACKING,
        centerPose = Pose(
            rotation = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 1.0, 0.0),
            translation = Vec3(0f, 0f, z),
        ),
        extentX = extentX,
        extentZ = extentX,
        corners = listOf(Vec2(-1f, -1f), Vec2(1f, -1f), Vec2(1f, 1f), Vec2(-1f, 1f)),
        inlierCount = 100,
    )

    private fun model() = PreparedModel(
        loadFile = File("modelo.glb"),
        bounds = ModelBounds(Vec3(-0.5f, 0f, -0.5f), Vec3(0.5f, 1f, 0.5f)),
        meshCount = 1,
        triangleCount = 12,
        converted = false,
    )

    /** Espera [condition] virar verdadeira, falhando o teste se demorar demais. */
    private suspend fun await(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        withTimeout(timeoutMillis) {
            while (!condition()) delay(5)
        }
    }

    // ------------------------------------------------------------------ testes

    @Test
    fun `desenha o quadro enviado e publica a saida`() = runBlocking {
        val renderer = FakeRenderer()
        val composer = SceneComposer(rendererFactory = { renderer }, onLog = {})

        try {
            composer.resize(32, 24)
            composer.setIntrinsics(CameraIntrinsics.forFrame(32, 24))
            composer.start()
            composer.submit(background(), listOf(marker(TrackingState.TRACKING)))

            await { composer.frame.value != null }

            assertEquals(32, renderer.width, "o tamanho pedido vale desde o primeiro quadro")
            assertEquals(32, renderer.intrinsics?.width, "os intrínsecos também")
            assertEquals(1, renderer.scenes.size)
            assertNotNull(renderer.scenes.first().background, "o fundo enviado tem de chegar")
            assertEquals("falso", composer.backend.value)
            assertNull(composer.error.value)
        } finally {
            composer.close()
        }
    }

    @Test
    fun `so o ultimo quadro vale quando o desenho esta atrasado`() = runBlocking {
        val renderer = FakeRenderer()
        val composer = SceneComposer(rendererFactory = { renderer }, onLog = {})

        try {
            // Três quadros entregues ANTES de o laço existir: o canal conflado guarda só o
            // último. É a situação de um desenho mais lento do que a captura, e o que se
            // quer é que a cena nunca fique atrás do mundo real.
            repeat(3) { index ->
                composer.submit(
                    background(),
                    listOf(marker(TrackingState.TRACKING, z = -0.1f * (index + 1))),
                )
            }
            composer.start()

            await { renderer.renderCount >= 1 }
            delay(150) // tempo de um segundo quadro aparecer, se estivesse formando fila

            assertEquals(1, renderer.renderCount, "os quadros atrasados têm de ser descartados")
        } finally {
            composer.close()
        }
    }

    @Test
    fun `os ajustes do painel chegam ao renderizador`() = runBlocking {
        val renderer = FakeRenderer()
        val composer = SceneComposer(rendererFactory = { renderer }, onLog = {})

        try {
            composer.resize(16, 16)
            composer.setModel(model())
            composer.setSizeMeters(0.5f)
            composer.setRotation(Vec3(90f, 0f, 45f))
            composer.setElevationMeters(0.1f)
            composer.start()
            composer.submit(background(), listOf(marker(TrackingState.TRACKING)))

            await { composer.frame.value != null }

            val scene = assertNotNull(renderer.scenes.lastOrNull())
            assertEquals(0.5f, scene.sizeMeters)
            assertEquals(Vec3(90f, 0f, 45f), scene.rotationDegrees)
            assertEquals(0.1f, scene.elevationMeters)
            assertNotNull(scene.model)
            assertTrue(scene.isTracking)

            // `assertNotNull` DEVOLVE o valor conferido, e um teste de JUnit que devolve
            // valor é **ignorado** (não executa e nem aparece como falha): por isso a
            // matriz vai para uma variável, e a última afirmação do teste devolve `Unit`.
            val matrix = assertNotNull(
                scene.worldMatrix,
                "com modelo e marcador rastreado há matriz de mundo",
            )
            assertTrue(matrix.all { it.isFinite() }, "a matriz não pode ter NaN nem infinito")
        } finally {
            composer.close()
        }
    }

    @Test
    fun `o modelo so e ancorado no marcador rastreado`() = runBlocking {
        val renderer = FakeRenderer()
        val composer = SceneComposer(rendererFactory = { renderer }, onLog = {})

        try {
            composer.resize(16, 16)
            composer.setModel(model())
            composer.start()

            // Dois marcadores no quadro: um pausado (primeiro na lista) e um rastreado.
            composer.submit(
                background(),
                listOf(marker(TrackingState.PAUSED), marker(TrackingState.TRACKING)),
            )
            await { renderer.scenes.isNotEmpty() }

            val withTracking = assertNotNull(renderer.scenes.last())
            assertEquals(TrackingState.TRACKING, withTracking.marker?.trackingState)
            assertNotNull(withTracking.worldMatrix, "o modelo vai para o marcador RASTREADO")

            // Nenhum rastreado: o modelo fica fora da cena.
            composer.submit(background(), listOf(marker(TrackingState.PAUSED)))
            await { renderer.scenes.size >= 2 }

            val withoutTracking = assertNotNull(renderer.scenes.last())
            assertNull(withoutTracking.marker)
            assertNull(withoutTracking.worldMatrix, "sem rastreio não há modelo a desenhar")
        } finally {
            composer.close()
        }
    }

    @Test
    fun `a falha ao criar o renderizador vira mensagem em vez de quebrar`() = runBlocking {
        val messages = mutableListOf<String>()
        val composer = SceneComposer(
            rendererFactory = { FakeRenderer(failOnCreate = true) },
            onLog = { messages += it },
        )

        try {
            composer.start()

            await { composer.error.value != null }

            assertTrue(
                composer.error.value.orEmpty().contains("sem GPU nesta máquina de mentira"),
                "a mensagem técnica tem de chegar à interface: ${composer.error.value}",
            )
            assertTrue(messages.isNotEmpty(), "a falha também vai para o log")
            assertNull(composer.frame.value)
        } finally {
            composer.close()
        }
    }

    @Test
    fun `fechar para o laco e libera o renderizador`() = runBlocking {
        val renderer = FakeRenderer()
        val composer = SceneComposer(rendererFactory = { renderer }, onLog = {})

        composer.resize(16, 16)
        composer.start()
        composer.submit(background(), listOf(marker(TrackingState.TRACKING)))
        await { composer.frame.value != null }

        composer.close()

        await { renderer.closed }
        assertTrue(renderer.closed, "o renderizador tem de ser liberado ao sair da tela")
        assertNull(composer.frame.value, "não há quadro depois de fechar")
    }

    @Test
    fun `a escala automatica ajusta o modelo a largura do marcador`() = runBlocking {
        val renderer = FakeRenderer()
        val composer = SceneComposer(rendererFactory = { renderer }, onLog = {})

        try {
            composer.resize(16, 16)
            composer.setModel(model())
            composer.start()
            composer.submit(
                background(),
                listOf(marker(TrackingState.TRACKING, extentX = 0.15f)),
            )
            await { renderer.scenes.isNotEmpty() }

            // Sem marcador escolhido na interface, o tamanho é o padrão.
            assertEquals(RenderScene.DEFAULT_SIZE_METERS, composer.sizeMeters.value)

            val applied = composer.autoScale()

            assertEquals(0.15f, applied, "o tamanho passa a ser a largura real do marcador")
            assertEquals(0.15f, composer.sizeMeters.value, "o painel passa a mostrar esse valor")

            // E o valor novo chega ao desenho no quadro seguinte.
            composer.submit(background(), listOf(marker(TrackingState.TRACKING, extentX = 0.15f)))
            await { renderer.scenes.size >= 2 }
            assertEquals(0.15f, renderer.scenes.last().sizeMeters)
        } finally {
            composer.close()
        }
    }

    @Test
    fun `sem marcador a escala automatica nao muda nada`() = runBlocking {
        val renderer = FakeRenderer()
        val composer = SceneComposer(rendererFactory = { renderer }, onLog = {})

        try {
            composer.resize(16, 16)
            composer.setModel(model())
            composer.setSizeMeters(0.4f)
            composer.start()
            composer.submit(background(), listOf(marker(TrackingState.PAUSED)))

            assertNull(composer.autoScale(), "sem marcador rastreado não há largura a medir")
            assertEquals(0.4f, composer.sizeMeters.value, "o tamanho do usuário é preservado")
        } finally {
            composer.close()
        }
    }

    @Test
    fun `o arrasto chega ao renderizador e e limitado`() = runBlocking {
        val renderer = FakeRenderer()
        val composer = SceneComposer(rendererFactory = { renderer }, onLog = {})

        try {
            composer.resize(16, 16)
            composer.setModel(model())
            composer.start()

            // Arrasto dentro do limite: chega como está nos dois eixos do arrasto — X (a largura
            // da figura) e Y (a normal, isto é, o frente–trás). O Z, a "altura NA imagem", fica em
            // zero: era ele que fazia o arrasto vertical subir e descer o modelo (decisão 36).
            composer.setOffsetMeters(Vec3(0.2f, -0.1f, -0.3f))
            composer.submit(background(), listOf(marker(TrackingState.TRACKING)))
            await { renderer.scenes.isNotEmpty() }

            val scene = assertNotNull(renderer.scenes.last())
            assertEquals(0.2f, scene.offsetMeters.x, 1e-6f)
            assertEquals(-0.1f, scene.offsetMeters.y, 1e-6f, "o arrasto vertical anda no frente–trás")
            assertEquals(0f, scene.offsetMeters.z, 1e-6f, "o arrasto não mexe na altura NA imagem")

            // Arrasto além do limite: o modelo não pode sair de perto da figura.
            composer.setOffsetMeters(Vec3(9f, 9f, 5f))
            composer.submit(background(), listOf(marker(TrackingState.TRACKING)))
            await { renderer.scenes.size >= 2 }

            val clamped = assertNotNull(renderer.scenes.last())
            assertEquals(InteractiveInput.MAX_PAN_METERS, clamped.offsetMeters.x, 1e-6f)
            assertEquals(InteractiveInput.MAX_PAN_METERS, clamped.offsetMeters.y, 1e-6f)
            assertEquals(0f, clamped.offsetMeters.z, 1e-6f)
        } finally {
            composer.close()
        }
    }

    @Test
    fun `o tamanho pedido e limitado a faixa do painel`() {
        val composer = SceneComposer(rendererFactory = { FakeRenderer() }, onLog = {})
        try {
            composer.setSizeMeters(99f)
            assertEquals(RenderScene.MAX_SIZE_METERS, composer.sizeMeters.value)

            composer.setSizeMeters(0f)
            assertEquals(RenderScene.MIN_SIZE_METERS, composer.sizeMeters.value)
        } finally {
            composer.close()
        }
    }

    @Test
    fun `a escolha de marcador decide quem ancora o modelo`() = runBlocking {
        val renderer = FakeRenderer()
        val composer = SceneComposer(rendererFactory = { renderer }, onLog = {})

        try {
            composer.resize(16, 16)
            composer.setModel(model())
            composer.start()

            val twoMarkers = listOf(
                marker(TrackingState.TRACKING, name = "marker_b"),
                marker(TrackingState.TRACKING, name = "marker_a"),
            )

            // Padrão: qualquer marcador (o primeiro rastreado da lista).
            composer.submit(background(), twoMarkers)
            await { renderer.scenes.isNotEmpty() }
            assertEquals("marker_b", renderer.scenes.last().marker?.name)

            // Escolhendo um marcador específico, só ele recebe o modelo.
            composer.setSelectedMarkerId("marker_a")
            composer.submit(background(), twoMarkers)
            await { renderer.scenes.size >= 2 }
            assertEquals("marker_a", renderer.scenes.last().marker?.name)

            // E o marcador escolhido, se sair do quadro, deixa a cena sem modelo — em vez
            // de cair no marcador que está na frente.
            composer.submit(background(), listOf(marker(TrackingState.TRACKING, name = "marker_b")))
            await { renderer.scenes.size >= 3 }
            assertNull(renderer.scenes.last().marker)
        } finally {
            composer.close()
        }
    }

    @Test
    fun `os cursores de rotacao abrem como o modelo carrega`() {
        // **Zero** — e não os 90° em X da 1.0.5/1.0.6: com a correspondência de eixos da decisão 38
        // (o plano XY do arquivo é o plano da figura e o +Z do arquivo é a normal), o modelo carrega
        // **de pé e com a face virada para quem olha** já com os cursores em zero — e é com eles em
        // zero que girar a folha impressa gira o modelo no eixo correspondente (o +Z do arquivo). O
        // 90° em X era o *apoio* que a 1.0.5 embutiu supondo os arquivos Z-up, e que **deitava** o
        // modelo no plano da folha — as medições estão nas decisões 37 e 38 do roadmap.
        //
        // O "Redefinir" volta para o mesmo valor (veja [RenderScene.DEFAULT_ROTATION_DEGREES]).
        val composer = SceneComposer(rendererFactory = { FakeRenderer() }, onLog = {})

        try {
            assertEquals(
                Vec3.ZERO,
                composer.rotationDegrees.value,
                "os cursores devem abrir como o modelo carrega: em zero",
            )
            assertEquals(
                RenderScene.DEFAULT_ROTATION_DEGREES,
                composer.rotationDegrees.value,
                "e o valor inicial é o mesmo a que o Redefinir volta",
            )
        } finally {
            composer.close()
        }
    }
}
