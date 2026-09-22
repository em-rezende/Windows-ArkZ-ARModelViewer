/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.ar

import com.arkz.armodelviewer.camera.toGrayBytes
import com.arkz.armodelviewer.markers.MarkerCatalog
import com.arkz.armodelviewer.markers.MarkerDefinition
import com.arkz.armodelviewer.model.Vec3
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.indexer.UByteIndexer
import org.bytedeco.opencv.global.opencv_core.BORDER_TRANSPARENT
import org.bytedeco.opencv.global.opencv_core.CV_8UC1
import org.bytedeco.opencv.global.opencv_imgproc.INTER_LINEAR
import org.bytedeco.opencv.global.opencv_imgproc.getPerspectiveTransform
import org.bytedeco.opencv.global.opencv_imgproc.warpPerspective
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Scalar
import org.bytedeco.opencv.opencv_core.Size
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes da detecção de marcador.
 *
 * Em vez de depender de uma webcam, o quadro é **sintetizado**: a imagem do marcador
 * é deformada por uma homografia conhecida para dentro de um quadro de 640×480, e o
 * teste cobra que o detector devolva exatamente aqueles quatro cantos, a pose
 * coerente e o estado de rastreio certo. É o mesmo caminho de um caso real, sem
 * hardware e sem depender de iluminação — e sem isso não haveria como afirmar que a
 * detecção funciona antes de alguém apontar uma câmera.
 */
class MarkerDetectorTest {

    private val canvasWidth = 640
    private val canvasHeight = 480

    /** Quadrado do marcador dentro do quadro: deslocado e em perspectiva leve. */
    private val quad = listOf(
        Vec2(110f, 40f),
        Vec2(530f, 60f),
        Vec2(520f, 440f),
        Vec2(100f, 420f),
    )

    /**
     * A mesma figura **de frente** para a câmera — o "impresso e virado para ele" do relato em
     * campo, que é o caso em que o Z do marcador cai no eixo vertical do mundo (decisão 36).
     */
    private val frenteQuad = listOf(
        Vec2(120f, 60f),
        Vec2(520f, 80f),
        Vec2(510f, 440f),
        Vec2(130f, 420f),
    )

    private val marker: MarkerDefinition
        get() = MarkerCatalog.loadBundled().first { it.id == "marker_a" }

    private fun detector(markers: List<MarkerDefinition> = listOf(marker)): MarkerDetector =
        MarkerDetector(markers, CameraIntrinsics.forFrame(canvasWidth, canvasHeight))

    @Test
    fun `encontra o marcador A desenhado no quadro e devolve a pose`() {
        detector().use { detector ->
            val detections = detector.detect(renderWarped(marker.image, quad))

            assertEquals(1, detections.size, "esperado exatamente um marcador detectado")
            val detected = detections.single()

            assertEquals("marker_a", detected.name)
            assertEquals(0, detected.index)
            assertEquals(TrackingState.TRACKING, detected.trackingState)
            assertEquals(TrackingMethod.FULL_TRACKING, detected.trackingMethod)
            assertEquals(0.15f, detected.extentX)
            assertEquals(0.15f, detected.extentZ)
            assertTrue(detected.inlierCount >= 12, "pontos casados: ${detected.inlierCount}")
        }
    }

    @Test
    fun `os quatro cantos batem com o desenho do quadro`() {
        detector().use { detector ->
            val detected = detector.detect(renderWarped(marker.image, quad)).single()

            quad.zip(detected.corners).forEachIndexed { index, (expected, actual) ->
                val distance = hypot(
                    (actual.x - expected.x).toDouble(),
                    (actual.y - expected.y).toDouble(),
                )
                assertTrue(
                    distance < CORNER_TOLERANCE_PIXELS,
                    "canto $index a ${"%.1f".format(distance)} px do esperado " +
                        "(esperado $expected, veio $actual)",
                )
            }
        }
    }

    @Test
    fun `a pose fica a frente da camera e na distancia esperada`() {
        detector().use { detector ->
            val detected = detector.detect(renderWarped(marker.image, quad)).single()
            val pose = detected.centerPose

            // No referencial do app (Y para cima, Z saindo da tela) a câmera olha
            // para -Z, como no ARCore: um marcador à frente tem Z negativo.
            assertTrue(pose.tz < 0f, "esperado marcador à frente da câmera, veio tz=${pose.tz}")
            assertTrue(
                abs(pose.tx) < 0.05f,
                "o marcador está no centro do quadro, então tx deveria ser ~0: ${pose.tx}",
            )

            // Geometria: f = 640 / (2·tan 30°) ≈ 554 px e o marcador ocupa ~400 px
            // de 0,15 m — a distância sai daí.
            val expectedDistance = 554.256 * 0.15 / 400.0
            assertTrue(
                abs(pose.distanceMeters - expectedDistance) < 0.08f,
                "distância ${pose.distanceMeters} m, esperado ≈ $expectedDistance m",
            )

            assertTrue(pose.isRotationOrthonormal(1e-3), "a rotação deveria ser ortonormal")
        }
    }

    @Test
    fun `de frente para a camera a pose entrega a normal no Y e a altura na imagem no Z`() {
        // A medição que fixou a decisão 36 (a correção do arrasto vertical), e o que separa
        // "o código *pretende* andar no plano" de "o código anda no plano". O referencial do
        // marcador é o do ARCore — X = largura, Y = NORMAL, Z = altura NA imagem — e é o que o
        // `solvePnP` produz a partir das quatro quinas de `markerObjectPointsMat` (nelas o Y é
        // zero). Aqui a medição é feita com o detector de verdade, num quadro sintetizado.
        //
        // Com a figura **de frente** para a câmera, as três colunas da rotação da pose no
        // referencial do mundo (X para a direita, Y para cima, câmera olhando para −Z) são:
        // a largura para a direita, a normal para a câmera e a "altura NA imagem" para BAIXO no
        // mundo — e é esta última que explica o relato: escrever o arrasto vertical no Z subia e
        // descia o modelo, em vez de levá-lo para a frente e para trás (o Y, a normal).
        detector().use { detector ->
            val pose = detector.detect(renderWarped(marker.image, frenteQuad)).single().centerPose

            fun coluna(indice: Int): Vec3 = Vec3(
                pose[0, indice].toFloat(),
                pose[1, indice].toFloat(),
                pose[2, indice].toFloat(),
            )

            val largura = coluna(0)
            val normal = coluna(1)
            val alturaNaImagem = coluna(2)

            assertTrue(largura.x > 0.9f, "o X do marcador deveria ser a largura: $largura")
            assertTrue(
                normal.z > 0.8f,
                "o Y do marcador deveria ser a normal (para a câmera): $normal",
            )
            assertTrue(
                -alturaNaImagem.y > 0.8f,
                "o Z do marcador deveria ser a altura NA imagem (para baixo no mundo): " +
                    "$alturaNaImagem",
            )
        }
    }

    @Test
    fun `nao inventa deteccao num quadro sem marcador`() {
        detector().use { detector ->
            assertEquals(emptyList(), detector.detect(blankFrame()))
        }
    }

    @Test
    fun `depois de perder o marcador ele fica em pausa e depois sai da cena`() {
        detector().use { detector ->
            val first = detector.detect(renderWarped(marker.image, quad)).single()
            assertEquals(TrackingState.TRACKING, first.trackingState)

            // Alguns quadros sem o marcador: continua na cena com a última pose, para
            // não piscar quando a webcam perde a figura por um instante.
            repeat(5) { detector.detect(blankFrame()) }
            val paused = detector.detect(blankFrame()).single()
            assertEquals(TrackingState.PAUSED, paused.trackingState)
            assertEquals(TrackingMethod.LAST_KNOWN_POSE, paused.trackingMethod)

            // Passada a janela de pausa, sai da cena.
            repeat(20) { detector.detect(blankFrame()) }
            assertTrue(
                detector.detect(blankFrame()).isEmpty(),
                "o marcador deveria ter saído da cena",
            )
        }
    }

    @Test
    fun `o botao reiniciar descarta as deteccoes`() {
        detector().use { detector ->
            detector.detect(renderWarped(marker.image, quad))
            detector.reset()

            assertTrue(detector.detect(blankFrame()).isEmpty())
        }
    }

    @Test
    fun `imagem de referencia sem pontos de interesse nao quebra o detector`() {
        // Uma imagem lisa (parede branca) não tem pontos de interesse: o marcador
        // entra na lista, mas nunca casa — e o app não pode quebrar por isso.
        val flat = MarkerDefinition(
            id = "flat",
            label = "Lisa",
            physicalWidthMeters = 0.15f,
            image = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB).apply {
                val graphics = createGraphics()
                graphics.color = Color.WHITE
                graphics.fillRect(0, 0, 64, 64)
                graphics.dispose()
            },
            isCustom = true,
        )

        detector(listOf(flat)).use { detector ->
            assertEquals(1, detector.markerCount)
            assertTrue(detector.detect(renderWarped(marker.image, quad)).isEmpty())
        }
    }

    /** Quadro em branco (nenhum ponto de interesse para o ORB). */
    private fun blankFrame(): BufferedImage {
        val canvas = Mat(canvasHeight, canvasWidth, CV_8UC1, Scalar(255.0))
        return canvas.toGrayBufferedImage().also { canvas.release() }
    }

    /**
     * Desenha a imagem do marcador dentro do quadro, deformada para o quadrilátero
     * [quad] — o que a webcam veria ao apontar para a figura impressa.
     */
    private fun renderWarped(
        markerImage: BufferedImage,
        quad: List<Vec2>,
        canvasWidth: Int = this.canvasWidth,
        canvasHeight: Int = this.canvasHeight,
    ): BufferedImage {
        val canvas = Mat(canvasHeight, canvasWidth, CV_8UC1, Scalar(255.0))
        return try {
            drawMarker(canvas, markerImage, quad)
            canvas.toGrayBufferedImage()
        } finally {
            canvas.release()
        }
    }

    /** Desenha DOIS marcadores no mesmo quadro (cena com mais de um marcador). */
    private fun renderTwo(
        firstImage: BufferedImage,
        firstQuad: List<Vec2>,
        secondImage: BufferedImage,
        secondQuad: List<Vec2>,
        canvasWidth: Int = this.canvasWidth,
        canvasHeight: Int = this.canvasHeight,
    ): BufferedImage {
        val canvas = Mat(canvasHeight, canvasWidth, CV_8UC1, Scalar(255.0))
        return try {
            drawMarker(canvas, firstImage, firstQuad)
            drawMarker(canvas, secondImage, secondQuad)
            canvas.toGrayBufferedImage()
        } finally {
            canvas.release()
        }
    }

    /** Deforma [markerImage] para dentro de [quad], dentro do quadro [canvas] já criado. */
    private fun drawMarker(canvas: Mat, markerImage: BufferedImage, quad: List<Vec2>) {
        val bytes = ByteArray(markerImage.width * markerImage.height)
        markerImage.toGrayBytes(bytes, IntArray(markerImage.width * markerImage.height))
        val pointer = BytePointer(*bytes)
        val markerGray = Mat(markerImage.height, markerImage.width, CV_8UC1, pointer)

        val source = markerCornersMat(markerImage.width, markerImage.height)
        val destination = pointsMat(quad.flatMap { listOf(it.x, it.y) }.toFloatArray(), quad.size)
        val transform = getPerspectiveTransform(source, destination)
        try {
            // `BORDER_TRANSPARENT`: os pixels que caem FORA da imagem de origem ficam
            // **intocados**. Sem isso, o segundo marcador desenhado no mesmo quadro
            // apagaria o primeiro (o `warpPerspective` preenche o destino inteiro, com
            // preto fora da origem) — o teste passaria a medir outra coisa.
            warpPerspective(
                markerGray,
                canvas,
                transform,
                Size(canvas.cols(), canvas.rows()),
                INTER_LINEAR,
                BORDER_TRANSPARENT,
                Scalar(0.0),
            )
        } finally {
            transform.release()
            source.release()
            destination.release()
            markerGray.release()
        }
    }

    /** Converte um `Mat` de tons de cinza na imagem ARGB que o detector recebe. */
    private fun Mat.toGrayBufferedImage(): BufferedImage {
        val image = BufferedImage(cols(), rows(), BufferedImage.TYPE_INT_ARGB)
        val indexer = createIndexer<UByteIndexer>()
        try {
            for (y in 0 until rows()) {
                for (x in 0 until cols()) {
                    val value = indexer.get(y.toLong(), x.toLong()).toInt() and 0xFF
                    image.setRGB(x, y, (0xFF shl 24) or (value shl 16) or (value shl 8) or value)
                }
            }
        } finally {
            indexer.release()
        }
        return image
    }

    @Test
    fun `nao quebra com quadro minusculo`() {
        detector().use { detector ->
            // Quadros assim não têm pontos de interesse; o que não pode acontecer é
            // o detector estourar (ou o OpenCV abortar) por causa do tamanho.
            assertEquals(
                emptyList(),
                detector.detect(BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB)),
            )
            assertEquals(
                emptyList(),
                detector.detect(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)),
            )
        }
    }

    @Test
    fun `usar o detector depois de fechar devolve vazio em vez de abortar o processo`() {
        val detector = detector()
        detector.detect(renderWarped(marker.image, quad))
        detector.close()

        // Sem a proteção, o `BFMatcher` receberia as matrizes nativas já liberadas e o
        // OpenCV responde com uma **asserção nativa** (`batchDistance: type ==
        // src2.type() && src1.cols == src2.cols …`), que derruba o processo em vez de
        // devolver erro. Este teste fixa o comportamento seguro.
        assertEquals(emptyList(), detector.detect(renderWarped(marker.image, quad)))

        // Fechar duas vezes também não pode ser problema.
        detector.close()
    }

    @Test
    fun `reconhece o marcador na distancia de uso tipica (30 a 60 cm)`() {
        // Este é o caso REAL de uso, e é bem mais difícil que o teste acima: com uma
        // câmera de 60° de campo de visão e um marcador de 15 cm a ~55 cm, a figura
        // ocupa cerca de 170 px num quadro de 1280×720 — enquanto a imagem de
        // referência tem 1000 px. Uma diferença de escala de ~6× está no limite do que
        // a pirâmide do ORB cobre (8 níveis ≈ 3,6×), então este teste existe para
        // impedir que a detecção "só funcione no laboratório".
        val wide = 1280
        val tall = 720
        val small = listOf(
            Vec2(560f, 280f),
            Vec2(730f, 285f),
            Vec2(728f, 455f),
            Vec2(558f, 450f),
        )

        MarkerDetector(listOf(marker), CameraIntrinsics.forFrame(wide, tall)).use { detector ->
            val detections = detector.detect(renderWarped(marker.image, small, wide, tall))

            assertTrue(
                detections.isNotEmpty(),
                "marcador não reconhecido a ~55 cm (≈170 px no quadro): " +
                    "a diferença de escala entre a referência e a imagem da câmera " +
                    "está fora do alcance da pirâmide do ORB?",
            )
            assertEquals("marker_a", detections.single().name)
        }
    }

    @Test
    fun `a mesma figura cadastrada duas vezes gera uma unica deteccao`() {
        // Cenário do app Android: o usuário cria/importa um marcador a partir de uma
        // figura igual à de um marcador embutido. Duas caixas exatamente sobrepostas
        // significariam conteúdo duplicado — e, na etapa 4, dois modelos no mesmo lugar.
        val duplicated = marker.copy(id = "marker_a_copia", label = "Cópia", isCustom = true)

        MarkerDetector(listOf(marker, duplicated), CameraIntrinsics.forFrame(canvasWidth, canvasHeight))
            .use { detector ->
                val detections = detector.detect(renderWarped(marker.image, quad))

                assertEquals(
                    1,
                    detections.size,
                    "a mesma figura não pode aparecer duas vezes: ${detections.map { it.name }}",
                )
            }
    }

    @Test
    fun `nao confunde o marcador A com a referencia B`() {
        // Marcador FÍSICO A na frente da câmera, com A e B cadastrados (era o caso real:
        // com um só marcador na vista, o log mostrava os dois nomes alternando).
        val both = MarkerCatalog.loadBundled()
        assertEquals(2, both.size, "este teste conta com os dois marcadores embutidos")

        MarkerDetector(both, CameraIntrinsics.forFrame(canvasWidth, canvasHeight)).use { detector ->
            val detections = detector.detect(renderWarped(marker.image, quad))

            assertEquals(
                1,
                detections.size,
                "esperado um marcador só: ${detections.map { it.name }}",
            )
            assertEquals("marker_a", detections.single().name)
        }
    }

    @Test
    fun `dois marcadores diferentes no quadro geram duas deteccoes`() {
        // O outro lado da deduplicação: ela não pode juntar marcadores de verdade.
        val bundled = MarkerCatalog.loadBundled()
        val first = bundled.first { it.id == "marker_a" }
        val second = bundled.first { it.id == "marker_b" }

        val wide = 1280
        val tall = 720
        val firstQuad = listOf(
            Vec2(60f, 60f), Vec2(330f, 80f), Vec2(320f, 350f), Vec2(50f, 330f),
        )
        val secondQuad = listOf(
            Vec2(900f, 360f), Vec2(1180f, 380f), Vec2(1170f, 650f), Vec2(890f, 630f),
        )

        MarkerDetector(bundled, CameraIntrinsics.forFrame(wide, tall)).use { detector ->
            val detections = detector.detect(
                renderTwo(first.image, firstQuad, second.image, secondQuad, wide, tall),
            )

            assertEquals(
                setOf("marker_a", "marker_b"),
                detections.map { it.name }.toSet(),
                "os dois marcadores deveriam aparecer: ${detections.map { it.name }}",
            )
        }
    }

    private companion object {
        /** Tolerância dos cantos, em pixels (o RANSAC usa 3 px de reprojeção). */
        const val CORNER_TOLERANCE_PIXELS = 8.0
    }
}
