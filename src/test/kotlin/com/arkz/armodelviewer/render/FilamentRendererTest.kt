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
 * | leitura **e** textura (não uma só) | **modelo espelhado na vertical**: o vídeo "certo" e o modelo de cabeça para baixo — o defeito da decisão 39 |
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
            val background = solidFrame(96, 96, red = 40, green = 40, blue = 40)
            val marker = markerOf(TrackingState.TRACKING)

            // A **casa de referência** (`House.glb`, exportado do Blender em **+Y para cima**: o
            // chão em y = 0, o telhado no topo do Y (+4,5) e a porta vermelha na face do +Z), com
            // os cursores em zero. Com a correspondência de eixos do `ModelPlacement` (decisão 38)
            // ela aparece **de pé e de frente**, preenchendo a área central — é o quadro que mede a
            // ancoragem, a luz, a projeção e a vertical do modelo.
            val house = prepareHouseModel()
            val facing = assertNotNull(
                renderer.render(
                    RenderScene(
                        background = background,
                        marker = marker,
                        model = house,
                    ),
                ),
                "o quadro com a casa de frente tem de sair",
            )

            val painted = countPaintedPixels(facing, background)
            assertTrue(
                painted > 20,
                "o modelo tem de aparecer sobre o fundo (pixels desenhados: $painted)",
            )
            assertTrue(
                brightestChannel(facing) > 40 + 25,
                "o modelo tem de estar ILUMINADO (maior canal: ${brightestChannel(facing)})",
            )

            // E a casa tem de estar **de pé**: a **porta vermelha** — a face da frente, que ocupa
            // a **metade de baixo** da casa (do chão ao meio da parede, no +Z do arquivo) — tem de
            // aparecer **ABAIXO** do centro do modelo. Com o quadro lido invertido (o defeito da
            // decisão 39) ela apareceria acima, como o teste em campo mostrou ("de cabeça para
            // baixo"). A porta voltou a ser quem mede a vertical quando o `House.glb` foi
            // exportado com a porta na face do **+Z** — antes disso ela ficava na face do X, fora
            // do campo da câmera de frente, e a medida era feita pelo cubo verde do telhado
            // (medido com esta exportação: porta na linha 61,1 contra 50,3 do modelo, num quadro
            // de 96 × 96).
            val porta = assertNotNull(
                centroidOf(facing, background) { r, g, b -> r > g + 20 && r > b + 20 },
                "a porta vermelha (a face da frente, na metade de baixo da casa) tem de aparecer " +
                    "no quadro",
            )
            val modelCentroid = assertNotNull(centroidOf(facing, background))
            assertTrue(
                porta.second > modelCentroid.second + 3.0,
                "a porta tem de ficar ABAIXO do centro do modelo (porta em ${porta.second}, " +
                    "modelo em ${modelCentroid.second} linhas)",
            )

            // **Chapa fina, e o caminho da conversão:** o `.stl` do logotipo do repositório (o
            // Assimp o converte para GLB) tem a espessura no **Y** — com a correspondência do app,
            // que supõe o +Y para cima, ele fica de perfil. Com 90° no cursor **X** ele mostra a
            // face e tem de desenhar ALGO: é este o caso que pega um modelo que "carrega e não
            // aparece" (o defeito de campo da primeira versão).
            val plate = prepareLogoModel()
            val plateFacing = assertNotNull(
                renderer.render(
                    RenderScene(
                        background = background,
                        marker = marker,
                        model = plate,
                        rotationDegrees = Vec3(90f, 0f, 0f),
                    ),
                ),
                "o quadro com a chapa de face tem de sair",
            )
            val platePainted = countPaintedPixels(plateFacing, background)
            assertTrue(
                platePainted > 0,
                "a chapa de face aparece com poucos pixels, mas tem de desenhar ALGO: $platePainted",
            )

            // A mesma casa, com o marcador apenas PAUSADO: nada pode ser desenhado (e o caso é o do
            // modelo **de frente**, o mais visível — um defeito aqui apareceria na hora).
            val paused = assertNotNull(
                renderer.render(
                    RenderScene(
                        background = background,
                        marker = markerOf(TrackingState.PAUSED),
                        model = house,
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
    fun `os eixos do marcador caem no quadro do mesmo lado que o video mostra`() {
        // O defeito que este teste guarda (decisão 39): o quadro era lido **invertido** e as
        // coordenadas de textura do plano de fundo compensavam a inversão — o **vídeo** aparecia
        // certo e o **modelo**, ancorado no mundo, aparecia **espelhado na vertical** (de cabeça
        // para baixo). Com a leitura como o motor entrega, os dois ficam no mesmo sentido, e é o
        // que se mede aqui, com deslocamentos conhecidos nos eixos DO MARCADOR: o **X** (a largura)
        // leva o modelo para a DIREITA e o **Z** (a "altura NA imagem") o leva para BAIXO.
        val renderer = newRenderer(96, 96)
        try {
            val background = solidFrame(96, 96, red = 40, green = 40, blue = 40)
            val model = prepareHouseModel()
            val marker = markerOf(TrackingState.TRACKING)

            fun quadro(offset: Vec3): SceneFrame = assertNotNull(
                renderer.render(
                    RenderScene(
                        background = background,
                        marker = marker,
                        model = model,
                        offsetMeters = offset,
                    ),
                ),
                "o quadro com deslocamento $offset tem de sair",
            )

            fun centro(frame: SceneFrame): Pair<Double, Double> = assertNotNull(
                centroidOf(frame, background),
                "o modelo tem de aparecer no quadro",
            )

            val semDeslocamento = quadro(Vec3.ZERO)
            val paraADireita = quadro(Vec3(0.1f, 0f, 0f))
            val paraBaixo = quadro(Vec3(0f, 0f, 0.1f))
            val paraAFrente = quadro(Vec3(0f, 0.1f, 0f))

            assertTrue(
                centro(paraADireita).first > centro(semDeslocamento).first + 10.0,
                "o X do marcador tem de levar o modelo para a DIREITA: " +
                    "${centro(semDeslocamento)} → ${centro(paraADireita)}",
            )
            assertTrue(
                centro(paraBaixo).second > centro(semDeslocamento).second + 10.0,
                "o Z do marcador tem de levar o modelo para BAIXO: " +
                    "${centro(semDeslocamento)} → ${centro(paraBaixo)}",
            )

            // E o Y do marcador (a NORMAL) é o frente–trás: um deslocamento nele **aproxima** o
            // modelo — mais pixels desenhados, porque a projeção é maior. É o sentido que a decisão
            // 36 fixou para o arrasto ("para baixo traz o modelo para a frente").
            val pixelsBase = countPaintedPixels(semDeslocamento, background)
            val pixelsFrente = countPaintedPixels(paraAFrente, background)
            assertTrue(
                pixelsFrente > pixelsBase + 100,
                "o Y do marcador tem de trazer o modelo PARA A FRENTE (pixels: $pixelsBase → " +
                    "$pixelsFrente)",
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
     * (para fora da tela, em direção à câmera) e o +Z dele ao −Y do mundo — são as
     * **colunas** da matriz (o `Pose` guarda a rotação em ordem de linha e a aplica como
     * `mundo = R · ponto`):
     *
     * ```
     * X → (1, 0, 0)      Y → (0, 0, 1)      Z → (0, −1, 0)
     * ```
     *
     * Com a rotação de identidade, o marcador ficaria **de perfil** para a câmera (o plano
     * dele contém a direção de visão) e o modelo apareceria de fio, com zero pixel. Foi
     * exatamente esse o defeito que este teste pegou: a pose precisa ser fisicamente possível.
     *
     * Com esta pose o modelo carrega **de pé e de frente** para a câmera (o plano XY do arquivo
     * é o plano da figura, e o +Z do arquivo — a face — é a normal): é a situação dos quadros
     * deste teste, e é a mesma do relato de campo, com o marcador virado para a webcam.
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

    /** Prepara a **casa de referência** do repositório (`3d_models/House.glb`) numa cópia temporária. */
    private fun prepareHouseModel(): PreparedModel {
        val source = File("3d_models", "House.glb")
        assertTrue(source.isFile, "modelo de teste ausente: ${source.absolutePath}")

        val copy = tempDir.resolve("House.glb").toFile()
        source.copyTo(copy, overwrite = true)
        return AssimpModelLoader.prepare(copy).getOrThrow()
    }

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

    /**
     * Centro (coluna, linha) dos pixels **desenhados** que passam no [filter] — a média das
     * posições no quadro.
     *
     * "Desenhado" é o mesmo critério de [countPaintedPixels] (longe do fundo); o [filter] escolhe
     * *qual* parte do modelo interessa — a porta vermelha do `House.glb` (dominância de canal) ou o
     * modelo inteiro (o filtro padrão, que aceita tudo). É esta a medida que diz **onde** o modelo
     * (ou uma face dele) caiu no quadro, e não só que ele apareceu. Devolve `null` quando nada
     * passou.
     */
    private fun centroidOf(
        frame: SceneFrame,
        background: BackgroundFrame,
        filter: (Int, Int, Int) -> Boolean = { _, _, _ -> true },
    ): Pair<Double, Double>? {
        val reference = pixelAt(background, 0, 0)
        var sumX = 0.0
        var sumY = 0.0
        var count = 0
        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                val pixel = pixelAt(frame, x, y)
                val distance = (0 until 3).maxOf { kotlin.math.abs(pixel[it] - reference[it]) }
                if (distance <= PAINT_THRESHOLD) continue
                if (!filter(pixel[0], pixel[1], pixel[2])) continue
                sumX += x
                sumY += y
                count++
            }
        }
        return if (count == 0) null else sumX / count to sumY / count
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
