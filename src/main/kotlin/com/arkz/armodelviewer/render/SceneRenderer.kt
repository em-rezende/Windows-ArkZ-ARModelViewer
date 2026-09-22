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

/**
 * O quadro da câmera que serve de **fundo** da cena — o "vídeo" da webcam.
 *
 * Os pixels vêm em **RGBA**, de cima para baixo (a mesma ordem do Skia/Compose e do
 * `BufferedImage`), porque é o formato que a tela e a captura de tela esperam: converter
 * no renderizador e depois reverter para exibir seria fazer a conta duas vezes.
 *
 * Não é `data class` de propósito: com um arranjo de bytes dentro, o `equals` compararia
 * **referências**, e duas instâncias com os mesmos pixels diriam-se diferentes — um erro
 * silencioso num cache de quadro.
 */
class BackgroundFrame(
    val rgba: ByteArray,
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0) { "quadro sem dimensão: ${width}×$height" }
        require(rgba.size >= width * height * 4) {
            "pixels insuficientes: ${rgba.size} bytes para ${width}×$height"
        }
    }

    /** Bytes de uma linha (RGBA = 4 bytes por pixel). */
    val rowBytes: Int get() = width * 4

    /** Proporção (largura / altura), usada para ajustar a câmera sintética. */
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}

/**
 * Resultado de um quadro desenhado: os pixels lidos de volta da GPU.
 *
 * O renderizador trabalha **fora da tela** e devolve os pixels prontos para o Compose
 * desenhar (e para a captura de tela reaproveitar). Essa é a arquitetura escolhida — veja
 * o KDoc de [SceneRenderer] — e é o que faz a captura sem interface da etapa 7 sair
 * praticamente de graça.
 */
class SceneFrame(
    val rgba: ByteArray,
    val width: Int,
    val height: Int,
) {
    /** Bytes de uma linha (RGBA = 4 bytes por pixel). */
    val rowBytes: Int get() = width * 4

    /**
     * O mesmo quadro com as linhas **invertidas**.
     *
     * É uma ferramenta da convenção de linhas, e **não** um passo do caminho de desenho: o
     * `readPixels` do Filament entrega o quadro **de cima para baixo** neste backend (Vulkan —
     * medido, e guardado pelos dois sentidos em `FilamentRendererTest`: o do **vídeo** e o do
     * **modelo**), e inverter aqui deixava o **modelo de cabeça para baixo em relação ao vídeo** —
     * a decisão 39. Fica fora do backend porque a conta é a mesma em qualquer um, e porque
     * inverter duas vezes (ou nenhuma) aparece só como imagem de cabeça para baixo na tela, sem
     * erro nenhum.
     */
    fun flippedVertically(): SceneFrame {
        val flipped = ByteArray(rgba.size)
        val row = rowBytes
        for (y in 0 until height) {
            val source = y * row
            val target = (height - 1 - y) * row
            rgba.copyInto(flipped, target, source, source + row)
        }
        return SceneFrame(flipped, width, height)
    }
}

/**
 * Tudo o que define **um quadro** da cena de realidade aumentada, sem nada de GPU dentro:
 * o fundo (vídeo da câmera), o marcador reconhecido, o modelo carregado e os ajustes do
 * usuário.
 *
 * ## Por que a cena é descrita, e não desenhada aos poucos
 *
 * O renderizador recebe este objeto a cada quadro e não guarda estado de aplicação — quem
 * guarda é a interface. Isso (1) mantém o renderizador trocável (o backend Filament e o
 * alternativo consomem exatamente a mesma descrição), (2) torna a **conta de ancoragem
 * testável** sem GPU ([worldMatrix] sai daqui, do mesmo `ModelPlacement` que já tem
 * testes) e (3) elimina a classe de defeito em que a tela mostra um quadro atrás do
 * outro.
 */
class RenderScene(
    /** Quadro atual da câmera, ou `null` enquanto o vídeo não chegou. */
    val background: BackgroundFrame? = null,
    /** Marcador reconhecido no quadro, ou `null` se nenhum. */
    val marker: DetectedMarker? = null,
    /** Modelo carregado e preparado (`null` = o usuário ainda não escolheu nenhum). */
    val model: PreparedModel? = null,
    /** Tamanho da maior dimensão do modelo, em metros. */
    val sizeMeters: Float = DEFAULT_SIZE_METERS,
    /**
     * Rotação dos sliders, em graus, nos eixos do **arquivo** (X = largura, Y = "para cima",
     * Z = o "frente") — os mesmos que o `ModelPlacement.MODEL_ORIENTATION` leva ao referencial
     * do marcador. Zero = "em pé, com a face olhando para quem vê", que é como o modelo carrega.
     */
    val rotationDegrees: Vec3 = DEFAULT_ROTATION_DEGREES,
    /** "Elevação Z": deslocamento na normal do marcador, em metros. */
    val elevationMeters: Float = 0f,
    /**
     * Deslocamento do arrasto (etapa 6), em metros, no referencial do marcador: **X** é a
     * largura da figura e **Y** é a normal — o **frente–trás**. É o que o "modo livre" faz:
     * tirar o modelo do centro da figura, pô-lo ao lado (X) e trazê-lo para a frente ou
     * afastá-lo (Y), **sem** mexer na altura NA imagem (Z) — decisão 36.
     */
    val offsetMeters: Vec3 = Vec3.ZERO,
) {

    /**
     * O marcador está **rastreado** e pronto para receber o modelo?
     *
     * Só em `TRACKING` o modelo é desenhado — mesma regra do app Android, em que o nó do
     * modelo ficava visível apenas com o marcador rastreado. Com o rastreio pausado, o
     * modelo não pode piscar nem aparecer em lugar nenhum.
     */
    val isTracking: Boolean get() = marker?.trackingState == TrackingState.TRACKING

    /**
     * Matriz que leva a geometria do arquivo ao referencial do mundo, ou `null` quando não
     * há o que desenhar (sem marcador rastreado ou sem modelo).
     *
     * É a única ponte entre a detecção e o desenho, e ela passa pelo [ModelPlacement] —
     * que é testado isoladamente. O renderizador, portanto, **não faz conta de câmera**:
     * recebe a matriz pronta e a aplica.
     */
    val worldMatrix: FloatArray?
        get() {
            val tracked = marker?.takeIf { it.trackingState == TrackingState.TRACKING }
                ?: return null
            val prepared = model ?: return null

            return ModelPlacement.worldMatrix(
                markerPose = tracked.centerPose,
                metrics = prepared.bounds.toMetrics(),
                sizeMeters = sizeMeters,
                rotationDegrees = rotationDegrees,
                elevationMeters = elevationMeters,
                offsetMeters = offsetMeters,
            )
        }

    /** Nada para desenhar neste quadro (nem fundo, nem modelo ancorado). */
    val isEmpty: Boolean get() = background == null && worldMatrix == null

    companion object {
        /**
         * Tamanho inicial do modelo: 0,2 m (20 cm) na maior dimensão — o **mesmo** valor
         * do app Android (`DEFAULT_MODEL_SIZE_METERS`), para que a primeira impressão ao
         * abrir um modelo seja idêntica nos dois aplicativos.
         */
        const val DEFAULT_SIZE_METERS = 0.2f

        /**
         * Rotação com que o modelo é ancorado, em graus, nos eixos do **arquivo**.
         *
         * **Zero** — e, com a correspondência de eixos de `ModelPlacement.MODEL_ORIENTATION`, zero
         * é o estado "**em pé, com a face olhando para quem está vendo**": o **+Y** do arquivo (a
         * altura) vai para a **altura NA imagem** e o **+Z** do arquivo (o "frente" — no
         * `House.glb` do repositório, a **face da porta vermelha**) cai na **normal** do marcador.
         *
         * **A história, porque ela custa a repetir.** Da 1.0.5 à 1.0.6 valeu **90° em X**, com a
         * premissa de que estes arquivos teriam o **Z para cima** — premissa que a medição desfez
         * (o "para cima" dos modelos do repositório é o **+Y**; decisão 37). A 1.0.7 tirou os 90° e
         * ficou com a correspondência de **identidade**, o que punha o +Y do arquivo na **normal**
         * do marcador: com a folha **deitada na mesa** (normal vertical) o modelo aparecia de pé —
         * e com a folha **de frente para a webcam**, que é a situação do desktop (o marcador na mão
         * ou apoiado à frente do monitor), ele aparecia **deitado de costas** e, girada a folha em
         * torno da própria normal, girava em torno do **próprio Y**, e não do eixo correspondente.
         * A **decisão 38** fechou a correspondência: o plano XY do arquivo **é** o plano da figura e
         * o +Z do arquivo **é** a normal.
         *
         * É só o valor **inicial** — o que o cursor mostra ao abrir e para onde o "Redefinir" volta.
         * Um arquivo com outro eixo para cima continua pedindo o cursor correspondente: com esta
         * correspondência, um arquivo exportado com o **+Z para cima** fica de pé com **−90°** no
         * cursor X (há teste para isso em `ModelPlacementTest`).
         */
        val DEFAULT_ROTATION_DEGREES: Vec3 = Vec3.ZERO

        /**
         * Faixa do tamanho do modelo, em metros — a mesma do app Android
         * (`MIN_MODEL_SIZE_METERS` / `MAX_MODEL_SIZE_METERS`): de uma miniatura de 2 cm a
         * um móvel de 2 m em escala real.
         *
         * Fica aqui, e não na interface, porque **três** lugares precisam dela: o slider do
         * painel, a escala automática (`AutoScale`) e o teto do que a cena aceita.
         */
        const val MIN_SIZE_METERS = 0.02f
        const val MAX_SIZE_METERS = 2.0f
    }
}

/**
 * Contrato do desenho da cena de RA.
 *
 * ## A arquitetura: desenho **fora da tela** com leitura de pixels
 *
 * O renderizador não desenha numa janela nativa: desenha num `SwapChain` **offscreen** do
 * tamanho do painel, lê os pixels de volta ([render]) e a interface Compose desenha o
 * quadro no canvas. As três razões:
 *
 *  1. **Encaixe com o Compose** — a tela já desenha o vídeo da câmera como imagem; manter
 *     o mesmo caminho evita embutir uma janela nativa dentro do Compose (que é onde a
 *     interoperabilidade dói, e onde o Windows costuma exigir recorte e sincronia);
 *  2. **Captura de tela sem interface (etapa 7)** — capturar passa a ser só pedir o mesmo
 *     quadro num tamanho maior, sem esconder controles nem capturar a janela: `render` num
 *     alvo de 1280/1920/4 K é a MESMA função;
 *  3. **Testabilidade** — inicializar o motor gráfico, uma cena e um quadro fora da tela
 *     roda num teste comum (é o que `FilamentSpikeTest` prova desde já).
 *
 * O custo é a cópia do quadro a cada desenho (por exemplo, ~3,7 MB para 1280×720), o que
 * num quadro de 30 Hz é uma fração pequena do orçamento — e o buffer é reaproveitado.
 *
 * Implementações devem liberar os recursos nativos em [close]: o motor gráfico do Filament
 * **precisa** ser destruído explicitamente, sob pena de travar o encerramento do processo
 * no Windows.
 */
interface SceneRenderer : AutoCloseable {

    /** Nome do backend e das bibliotecas em uso — aparece no diagnóstico copiável. */
    val backend: String

    /** Largura atual do quadro desenhado, em pixels. */
    val width: Int

    /** Altura atual do quadro desenhado, em pixels. */
    val height: Int

    /**
     * Ajusta o tamanho do quadro ao do painel. É chamada quando a janela muda de tamanho;
     * implementações podem agrupar chamadas repetidas.
     */
    fun resize(width: Int, height: Int)

    /**
     * Informa os intrínsecos da câmera de vídeo (a matriz da câmera do OpenCV).
     *
     * É o que permite ao renderizador projetar **exatamente** como a detecção projetou: a
     * pose do marcador saiu de um `solvePnP` com esses números, e uma projeção diferente na
     * hora de desenhar colocaria o modelo fora do marcador. Ver [CameraProjection].
     */
    fun updateIntrinsics(intrinsics: CameraIntrinsics)


    /**
     * Desenha um quadro e devolve os pixels (RGBA, de cima para baixo, prontos para o
     * Compose).
     *
     * Devolve `null` quando não há nada a desenhar (cena vazia) ou quando o quadro foi
     * descartado — por exemplo, se outro quadro ainda está em processamento.
     */
    fun render(scene: RenderScene): SceneFrame?
}
