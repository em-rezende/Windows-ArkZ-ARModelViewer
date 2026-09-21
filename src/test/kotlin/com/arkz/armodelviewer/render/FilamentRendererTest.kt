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
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Testes **de verdade na GPU** do renderizador Filament.
 *
 * ## O que estes testes são capazes de pegar
 *
 * Eles desenham quadros de verdade (motor gráfico, material compilado, textura, geometria,
 * leitura de pixels) e conferem os **pixels resultantes**. Isso cobre a cadeia inteira, e
 * cada elo tem um defeito clássico que apareceria num teste destes, e não num teste de
 * lógica:
 *
 * | Elo | Defeito que apareceria |
 * |---|---|
 * | textura da câmera | vídeo preto, ou vermelho e azul trocados |
 * | coordenadas de textura e leitura | **vídeo de cabeça para baixo** |
 * | tamanho do plano de fundo | faixa preta sobrando ou vídeo cortado |
 * | carga do modelo (gltfio) | modelo não aparece |
 * | matriz de ancoragem | modelo aparece longe do marcador |
 * | iluminação | modelo preto, sem relevo |
 *
 * Os quadros são de 64×64 pixels de propósito: o que está em teste é a **cadeia**, não a
 * qualidade da imagem — e quadros pequenos mantêm a suíte rápida.
 *
 * Nada aqui depende de webcam: o quadro da câmera é sintetizado, com cores conhecidas.
 */
class FilamentRendererTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `o quadro da camera aparece no fundo, com a orientacao certa`() {
        val renderer = newRenderer(64, 64)
        try {
            val frame = assertNotNull(
                renderer.render(RenderScene(background = splitFrame(64, 64))),
                "o primeiro quadro tem de sair",
            )

            // Metade de cima vermelha, metade de baixo azul, no QUADRO DA CÂMERA. Se a
            // textura viesse de cabeça para baixo (ou as coordenadas de textura estivessem
            // invertidas), o teste veria azul em cima. A cor é comparada por **dominância
            // de canal** (e não por valor exato) porque o caminho sRGB do Filament não
            // promete o byte exato de volta — mas promete a ordem dos canais.
            val top = pixelAt(frame, x = 32, y = 2)
            val bottom = pixelAt(frame, x = 32, y = 61)

            assertTrue(top[0] > top[2] + 40, "o topo tem de ser vermelho, veio ${top.toList()}")
            assertTrue(bottom[2] > bottom[0] + 40, "a base tem de ser azul, veio ${bottom.toList()}")
        } finally {
            renderer.close()
        }
    }

    @Test
    fun `o modelo aparece ancorado no marcador e some sem rastreio`() {
        val renderer = newRenderer(96, 96)
        try {
            val model = prepareLogoModel()
            val background = solidFrame(96, 96, red = 40, green = 40, blue = 40)

            // Em pé (a rotação padrão agora é "em pé"): o logo é uma chapa fina e fica de
            // fio para a câmera. Ele tem de APARECER, mesmo que com poucos pixels.
            val standing = assertNotNull(
                renderer.render(
                    RenderScene(
                        background = background,
                        marker = markerOf(TrackingState.TRACKING),
                        model = model,
                    ),
                ),
                "o quadro com o modelo em pé tem de sair",
            )
            val standingPainted = countPaintedPixels(standing, background)
            assertTrue(
                standingPainted > 0,
                "o modelo em pé aparece de fio, mas tem de desenhar ALGO: $standingPainted",
            )

            // Deitado (90° em X, como o usuário faz para expor a face), a chapa enche a
            // área central — é o caso que mede a ancoragem, a luz e a projeção.
            val facing = assertNotNull(
                renderer.render(
                    RenderScene(
                        background = background,
                        marker = markerOf(TrackingState.TRACKING),
                        model = model,
                        rotationDegrees = Vec3(90f, 0f, 0f),
                    ),
                ),
                "o quadro com a face do modelo tem de sair",
            )

            val painted = countPaintedPixels(facing, background)
            assertTrue(
                painted > 20,
                "a face do modelo tem de aparecer sobre o fundo (pixels desenhados: $painted)",
            )
            assertTrue(
                brightestChannel(facing) > 40 + 25,
                "o modelo tem de estar ILUMINADO (maior canal: ${brightestChannel(facing)})",
            )

            // O mesmo modelo, com o marcador apenas PAUSADO: nada pode ser desenhado.
            val paused = assertNotNull(
                renderer.render(
                    RenderScene(
                        background = background,
                        marker = markerOf(TrackingState.PAUSED),
                        model = model,
                        rotationDegrees = Vec3(90f, 0f, 0f),
                    ),
                ),
                "o quadro sem rastreio tem de sair",
            )

            assertEquals(
                0,
                countPaintedPixels(paused, background),
                "sem rastreio o modelo não pode aparecer no quadro",
            )
        } finally {
            renderer.close()
        }
    }

    @Test
    fun `o tamanho do quadro pode mudar e a troca de modelo e aceita`() {
        val renderer = newRenderer(64, 64)
        try {
            val first = assertNotNull(
                renderer.render(RenderScene(background = solidFrame(64, 64, 40, 40, 40))),
            )
            assertEquals(64, first.width)
            assertEquals(64, first.height)

            // A janela foi redimensionada (com proporção diferente, inclusive): o quadro
            // seguinte tem de sair no tamanho novo, sem estourar nada.
            renderer.resize(48, 80)
            val resized = assertNotNull(
                renderer.render(RenderScene(background = solidFrame(48, 80, 40, 40, 40))),
            )
            assertEquals(48, resized.width)
            assertEquals(80, resized.height)
            assertEquals(48 * 4, resized.rowBytes)

            // Trocar de modelo (e voltar a nenhum) não pode deixar o renderizador num
            // estado inválido: é o caminho que o usuário percorre ao escolher arquivos.
            val model = prepareLogoModel()
            assertNotNull(
                renderer.render(
                    RenderScene(
                        background = solidFrame(48, 80, 40, 40, 40),
                        marker = markerOf(TrackingState.TRACKING),
                        model = model,
                    ),
                ),
            )
            assertNotNull(
                renderer.render(RenderScene(background = solidFrame(48, 80, 40, 40, 40))),
            )
        } finally {
            renderer.close()
        }
    }

    // ------------------------------------------------------------------ apoio

    private fun newRenderer(width: Int, height: Int) = FilamentRenderer(
        width = width,
        height = height,
        intrinsics = CameraIntrinsics.forFrame(width, height),
    )

    /**
     * Marcador rastreado **50 cm à frente** da câmera e **de frente para ela**.
     *
     * ## Por que a rotação não é a identidade
     *
     * No referencial de imagem do ARCore (o que o app usa, e o que o `ModelPlacement`
     * documenta): **+X** é a largura da imagem, **+Y** é a **normal** (sai do plano do
     * marcador, na direção de quem olha) e **+Z** é a altura NA imagem. Um marcador de
     * frente para a câmera, portanto, tem a rotação que leva o +Y dele ao **+Z** do mundo
     * (para fora da tela, em direção à câmera) e o +Z dele ao −Y do mundo:
     *
     * ```
     * X → (1, 0, 0)      Y → (0, 0, 1)      Z → (0, −1, 0)
     * ```
     *
     * Com a rotação de identidade, o marcador ficaria **de perfil** para a câmera (o plano
     * dele contém a direção de visão) e o modelo — que fica deitado no plano do marcador —
     * apareceria de fio, com zero pixel. Foi exatamente esse o defeito que este teste
     * pegou: a pose precisa ser fisicamente possível.
     */
    private fun markerOf(state: TrackingState) = DetectedMarker(
        index = 1,
        name = "marker_02",
        label = "Marcador 2",
        trackingState = state,
        trackingMethod = TrackingMethod.FULL_TRACKING,
        centerPose = Pose(
            rotation = doubleArrayOf(
                1.0, 0.0, 0.0,
                0.0, 0.0, -1.0,
                0.0, 1.0, 0.0,
            ),
            translation = Vec3(0f, 0f, -0.5f),
        ),
        extentX = 0.15f,
        extentZ = 0.15f,
        corners = listOf(Vec2(-1f, -1f), Vec2(1f, -1f), Vec2(1f, 1f), Vec2(-1f, 1f)),
        inlierCount = 120,
    )

    /** Prepara o logo do repositório (STL → GLB) numa cópia temporária. */
    private fun prepareLogoModel(): PreparedModel {
        val source = File("3d_models", "ArkZ_logo.stl")
        assertTrue(source.isFile, "modelo de teste ausente: ${source.absolutePath}")

        val copy = tempDir.resolve("logo.stl").toFile()
        source.copyTo(copy, overwrite = true)
        return AssimpModelLoader.prepare(copy).getOrThrow()
    }

    /** Quadro de câmera sintético: metade de cima vermelha, metade de baixo azul. */
    private fun splitFrame(width: Int, height: Int): BackgroundFrame =
        frameOf(width, height) { y -> if (y < height / 2) RED else BLUE }

    /** Quadro de câmera sintético de cor única. */
    private fun solidFrame(
        width: Int,
        height: Int,
        red: Int,
        green: Int,
        blue: Int,
    ): BackgroundFrame = frameOf(width, height) { intArrayOf(red, green, blue) }

    private fun frameOf(width: Int, height: Int, colorAt: (Int) -> IntArray): BackgroundFrame {
        val rgba = ByteArray(width * height * 4)
        for (y in 0 until height) {
            val color = colorAt(y)
            for (x in 0 until width) {
                val index = (y * width + x) * 4
                rgba[index] = color[0].toByte()
                rgba[index + 1] = color[1].toByte()
                rgba[index + 2] = color[2].toByte()
                rgba[index + 3] = 0xFF.toByte()
            }
        }
        return BackgroundFrame(rgba, width, height)
    }

    /** Cor do pixel (x, y) do quadro desenhado, como três canais de 0 a 255. */
    private fun pixelAt(frame: SceneFrame, x: Int, y: Int): IntArray =
        pixelAt(frame.rgba, frame.rowBytes, x, y)

    /** Cor do pixel (x, y) do quadro da câmera, como três canais de 0 a 255. */
    private fun pixelAt(frame: BackgroundFrame, x: Int, y: Int): IntArray =
        pixelAt(frame.rgba, frame.rowBytes, x, y)

    private fun pixelAt(rgba: ByteArray, rowBytes: Int, x: Int, y: Int): IntArray {
        val index = y * rowBytes + x * 4
        return intArrayOf(
            rgba[index].toInt() and 0xFF,
            rgba[index + 1].toInt() and 0xFF,
            rgba[index + 2].toInt() and 0xFF,
        )
    }

    /**
     * Quantos pixels do quadro estão **longe** do fundo sintético.
     *
     * A folga de [PAINT_THRESHOLD] por canal absorve a conversão sRGB do caminho do
     * Filament (o byte de volta não precisa ser idêntico ao de ida) sem deixar passar um
     * modelo desenhado — que muda os canais em dezenas.
     */
    private fun countPaintedPixels(frame: SceneFrame, background: BackgroundFrame): Int {
        val reference = pixelAt(background, 0, 0)
        var painted = 0
        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                val pixel = pixelAt(frame, x, y)
                val distance = (0 until 3).maxOf { kotlin.math.abs(pixel[it] - reference[it]) }
                if (distance > PAINT_THRESHOLD) painted++
            }
        }
        return painted
    }

    /** Maior valor de canal no quadro — serve para saber se há luz na cena. */
    private fun brightestChannel(frame: SceneFrame): Int {
        var brightest = 0
        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                brightest = maxOf(brightest, pixelAt(frame, x, y).max())
            }
        }
        return brightest
    }

    private companion object {
        val RED = intArrayOf(220, 30, 30)
        val BLUE = intArrayOf(30, 30, 220)

        /** Diferença mínima (por canal) para considerar um pixel "desenhado". */
        const val PAINT_THRESHOLD = 25
    }
}
