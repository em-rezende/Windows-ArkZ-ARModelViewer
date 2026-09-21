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
import com.arkz.armodelviewer.model.Vec3
import com.arkz.armodelviewer.util.AppLog
import io.github.erkko68.filament.Box
import io.github.erkko68.filament.Camera
import io.github.erkko68.filament.Engine
import io.github.erkko68.filament.EntityManager
import io.github.erkko68.filament.IndexBuffer
import io.github.erkko68.filament.LightManager
import io.github.erkko68.filament.Material
import io.github.erkko68.filament.MaterialInstance
import io.github.erkko68.filament.RenderableManager
import io.github.erkko68.filament.Renderer
import io.github.erkko68.filament.Scene
import io.github.erkko68.filament.SwapChain
import io.github.erkko68.filament.Texture
import io.github.erkko68.filament.TextureSampler
import io.github.erkko68.filament.TransformManager
import io.github.erkko68.filament.VertexBuffer
import io.github.erkko68.filament.View
import io.github.erkko68.filament.Viewport
import io.github.erkko68.filament.filamat.Filamat
import io.github.erkko68.filament.filamat.MaterialBuilder
import io.github.erkko68.filament.gltfio.AssetLoader
import io.github.erkko68.filament.gltfio.FilamentAsset
import io.github.erkko68.filament.gltfio.ResourceLoader
import io.github.erkko68.filament.gltfio.UbershaderProvider
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Backend **Filament** do [SceneRenderer] — o mesmo renderizador do app Android.
 *
 * ## O que ele monta
 *
 * ```
 * câmera (projeção das intrínsecas, olhando para −Z)
 *   ├── plano de fundo: um quadrilátero no referencial da câmera, com o vídeo da webcam
 *   └── modelo: o .glb carregado pelo gltfio, com a matriz do ModelPlacement
 * ```
 *
 * * o **plano de fundo** é geometria de verdade (um quad de 4 vértices) desenhado na
 *   distância de [BACKGROUND_DISTANCE_METERS], dimensionado para cobrir exatamente o
 *   quadro — a conta está em [CameraProjection.frameSizeAt]. Como ele fica bem atrás de
 *   tudo (o marcador é usado a 30–60 cm), o modelo não é cortado pelo plano e não é
 *   preciso mexer no teste de profundidade;
 * * o **modelo** é carregado pelo `gltfio` (o leitor de glTF do próprio Filament, com os
 *   *uber shaders* embutidos na biblioteca nativa) e recebe a matriz de mundo pelo
 *   `TransformManager` — a mesma matriz que os testes cobrem no `ModelPlacement`.
 *
 * ## Materiais
 *
 * O único material escrito à mão é o do plano de fundo (não iluminado, com a textura da
 * câmera), **compilado em tempo de execução** pelo `filamat`: os jars do binding não
 * trazem `.filamat` nenhum (decisão 6 do roadmap). Os materiais do modelo vêm dos *uber
 * shaders* do gltfio.
 *
 * ## Ciclo de vida
 *
 * O motor gráfico é criado no construtor — operação cara: quem chama deve fazer isso fora
 * da thread de interface — e **precisa** ser destruído em [close], senão o encerramento do
 * processo trava no Windows.
 */
class FilamentRenderer(
    width: Int = DEFAULT_WIDTH,
    height: Int = DEFAULT_HEIGHT,
    intrinsics: CameraIntrinsics = CameraIntrinsics.forFrame(width, height),
) : SceneRenderer {

    override val backend: String = "Filament (filament-kmp 0.5.0: gltfio + filamat)"

    private val engine: Engine = Engine.create()
    private val scene: Scene = engine.createScene()
    private val view: View = engine.createView()
    private val camera: Camera = engine.createCamera()
    private val renderer: Renderer = engine.createRenderer()
    private val transformManager: TransformManager = engine.transformManager
    private val entities = EntityManager.get()

    /** Materiais do modelo: os *uber shaders* embutidos na biblioteca nativa do gltfio. */
    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader.create(engine, materialProvider, entities)
    private val resourceLoader = ResourceLoader(engine, false)

    /** Material do plano de fundo, compilado em tempo de execução. */
    private val backgroundMaterial: Material = Material.Builder()
        .payload(compileBackgroundMaterial())
        .build(engine)
    private val backgroundInstance: MaterialInstance = backgroundMaterial.createInstance()
    private val backgroundSampler = TextureSampler(
        TextureSampler.MinFilter.LINEAR,
        TextureSampler.MagFilter.LINEAR,
        TextureSampler.WrapMode.CLAMP_TO_EDGE,
    )

    private val backgroundVertexBuffer: VertexBuffer
    private val backgroundIndexBuffer: IndexBuffer
    private val backgroundTransform: Int
    private val backgroundEntity: Int

    /** Textura do vídeo da câmera; recriada quando o quadro da câmera muda de tamanho. */
    private var backgroundTexture: Texture? = null
    private var backgroundSize: Pair<Int, Int>? = null

    private var swapChain: SwapChain? = null

    override var width: Int = width
        private set

    override var height: Int = height
        private set

    private var cameraIntrinsics: CameraIntrinsics = intrinsics

    /** Arquivo do modelo carregado agora (`null` = nenhum). */
    private var loadedModelFile: File? = null
    private var modelAsset: FilamentAsset? = null
    private var modelTransform: Int = 0
    private var modelInScene = false

    init {
        require(width > 0 && height > 0) { "quadro inválido: ${width}×$height" }
        require(intrinsics.width > 0 && intrinsics.height > 0) { "intrínsecas inválidas" }

        // ---------- Câmera ----------
        view.scene = scene
        view.camera = camera
        // Na origem, olhando para −Z, com +Y para cima: o referencial das intrínsecas
        // (e o mesmo do ARCore, de onde a pose do marcador vem).
        camera.lookAt(0.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 1.0, 0.0)

        // ---------- Plano de fundo ----------
        backgroundVertexBuffer = buildUnitQuad()
        backgroundIndexBuffer = IndexBuffer.Builder()
            .indexCount(QUAD_INDICES.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        val indexData = ByteBuffer.allocate(QUAD_INDICES.size * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        QUAD_INDICES.forEach { indexData.putShort(it) }
        backgroundIndexBuffer.setBuffer(engine, indexData.array())

        backgroundEntity = entities.create()
        RenderableManager.Builder(1)
            .geometry(
                0,
                RenderableManager.PrimitiveType.TRIANGLES,
                backgroundVertexBuffer,
                backgroundIndexBuffer,
            )
            .material(0, backgroundInstance)
            // A caixa envolvente é declarada em vez de deduzida dos vértices: é uma
            // geometria fixa e conhecida (o quad unitário no plano XY), e sem ela o
            // Filament aborta com "AABB can't be empty" — a asserção que exige que o
            // renderizável tenha uma caixa válida para o descarte por frustum.
            .boundingBox(Box(floatArrayOf(0f, 0f, 0f), floatArrayOf(0.5f, 0.5f, 0.01f)))
            // O quadrilátero é visto de frente: dispensar o descarte de faces tira do
            // caminho qualquer dúvida sobre a orientação dos triângulos.
            .culling(false)
            .build(engine, backgroundEntity)
        scene.addEntity(backgroundEntity)
        backgroundTransform = transformManager.create(backgroundEntity)

        // ---------- Luzes ----------
        createDirectionalLight(x = -0.45f, y = -1f, z = -0.55f, intensity = KEY_LIGHT_LUX)
        createDirectionalLight(x = 0.7f, y = -0.35f, z = 0.4f, intensity = FILL_LIGHT_LUX)

        // ---------- Tamanho inicial ----------
        resizeInternal(width, height)
        AppLog.info("Renderizador pronto: ${width}×$height. $backend")
    }

    /**
     * Quadrilátero unitário no plano XY, centrado na origem, com as coordenadas de
     * textura na convenção do **vídeo** (v = 0 na linha de cima da imagem).
     *
     * Os vértices estão em ordem anti-horária vista da câmera (que está em +Z olhando
     * para −Z), e o descarte de faces está desligado de todo modo.
     */
    private fun buildUnitQuad(): VertexBuffer {
        val stride = 5 * Float.SIZE_BYTES
        val vertexBuffer = VertexBuffer.Builder()
            .vertexCount(4)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, stride)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 3 * Float.SIZE_BYTES, stride)
            .build(engine)

        val data = ByteBuffer.allocate(QUAD_VERTICES.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        QUAD_VERTICES.forEach { data.putFloat(it) }
        vertexBuffer.setBufferAt(engine, 0, data.array())
        return vertexBuffer
    }

    /** Luz direcional branca sem sombras — a única iluminação da cena. */
    private fun createDirectionalLight(x: Float, y: Float, z: Float, intensity: Float) {
        val entity = entities.create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1f, 1f, 1f)
            .intensity(intensity)
            // A direção é o sentido em que a luz VIAJA (de cima para baixo, para dentro
            // da cena), não o lado de onde ela vem.
            .direction(x, y, z)
            .castShadows(false)
            .build(engine, entity)
        scene.addEntity(entity)
    }

    // ------------------------------------------------------------------ SceneRenderer

    override fun resize(width: Int, height: Int) {
        if (width == this.width && height == this.height) return
        require(width > 0 && height > 0) { "quadro inválido: ${width}×$height" }
        resizeInternal(width, height)
    }

    override fun updateIntrinsics(intrinsics: CameraIntrinsics) {
        cameraIntrinsics = intrinsics
        applyCameraAndBackgroundGeometry()
    }

    override fun render(scene: RenderScene): SceneFrame? {
        scene.background?.let { updateBackground(it) }
        updateModel(scene.model)
        updatePlacement(scene.worldMatrix)

        val swapChain = this.swapChain ?: return null
        if (!renderer.beginFrame(swapChain, System.nanoTime())) {
            // O motor recusa o quadro quando não há nada para ele fazer (janela
            // minimizada, por exemplo): não é erro, é um quadro descartado.
            return null
        }

        try {
            renderer.render(view)
            // A leitura acontece DENTRO do quadro: é o momento em que o Filament aceita
            // registrar a cópia dos pixels do alvo atual.
            return readBack()
        } finally {
            renderer.endFrame()
        }
    }

    // ------------------------------------------------------------------ interno

    private fun resizeInternal(width: Int, height: Int) {
        this.width = width
        this.height = height

        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = engine.createSwapChain(width, height, 0L)
        view.viewport = Viewport(0, 0, width, height)

        applyCameraAndBackgroundGeometry()
    }

    /**
     * Recalcula a projeção da câmera e o tamanho do plano de fundo.
     *
     * Os dois dependem do mesmo par (intrínsecas, tamanho do quadro): a projeção porque é
     * dela que sai o alinhamento com o marcador, e o plano porque ele tem de cobrir
     * exatamente o quadro.
     */
    private fun applyCameraAndBackgroundGeometry() {
        camera.setCustomProjection(
            CameraProjection.fromIntrinsics(cameraIntrinsics, width, height),
            CameraProjection.NEAR_METERS,
            CameraProjection.FAR_METERS,
        )

        val (planeWidth, planeHeight) = CameraProjection.frameSizeAt(
            intrinsics = cameraIntrinsics,
            width = width,
            height = height,
            distanceMeters = BACKGROUND_DISTANCE_METERS,
        )

        // O quad é unitário: a escala o leva ao tamanho do quadro e a translação o afasta
        // da câmera. Os eixos de textura saem certos porque o quad vive no referencial da
        // câmera (x para a direita, y para cima).
        transformManager.setTransform(
            backgroundTransform,
            ModelPlacement.multiply(
                ModelPlacement.translation(Vec3(0f, 0f, -BACKGROUND_DISTANCE_METERS.toFloat())),
                ModelPlacement.scale(Vec3(planeWidth.toFloat(), planeHeight.toFloat(), 1f)),
            ),
        )
    }

    /**
     * Sobe o quadro da câmera para a textura do plano de fundo.
     *
     * A textura é recriada quando o quadro da câmera muda de tamanho (troca de resolução
     * da webcam) e reaproveitada nos demais quadros — recriar por quadro seria alocar e
     * liberar memória de vídeo 30 vezes por segundo.
     */
    private fun updateBackground(frame: BackgroundFrame) {
        var texture = backgroundTexture
        val size = frame.width to frame.height
        if (texture == null || backgroundSize != size) {
            texture?.let { engine.destroyTexture(it) }
            texture = Texture.Builder()
                .width(frame.width)
                .height(frame.height)
                .levels(1)
                .sampler(Texture.Sampler.SAMPLER_2D)
                // O vídeo da webcam já vem codificado em sRGB: declarar isso faz o
                // Filament linearizar na amostragem e reencodar na saída — o vídeo
                // aparece com as cores originais, e não lavado.
                .format(Texture.InternalFormat.SRGB8_A8)
                .build(engine)

            backgroundTexture = texture
            backgroundSize = frame.width to frame.height
            backgroundInstance.setParameter(CAMERA_FRAME_PARAMETER, texture, backgroundSampler)
        }

        texture.setImage(
            engine,
            0,
            Texture.PixelBufferDescriptor(
                frame.rgba,
                frame.rgba.size,
                Texture.Format.RGBA,
                Texture.Type.UBYTE,
            ),
        )
    }

    /**
     * Carrega (ou troca) o modelo da cena, quando o arquivo preparado muda.
     *
     * O `gltfio` lê os **bytes** do `.glb`/`.gltf` — o mesmo arquivo que o
     * `AssimpModelLoader` preparou (o original ou o convertido ao lado dele). Depois de
     * carregar os recursos, a fonte é liberada (`releaseSourceData`), que é o que evita
     * manter uma segunda cópia da geometria na memória.
     */
    private fun updateModel(prepared: PreparedModel?) {
        val file = prepared?.loadFile
        if (file == loadedModelFile) return

        releaseModel()
        if (file == null) return

        val bytes = try {
            file.readBytes()
        } catch (error: Exception) {
            AppLog.warn("Não foi possível ler '${file.name}': ${error.message}")
            return
        }

        val asset = try {
            assetLoader.createAsset(bytes)
        } catch (error: Exception) {
            AppLog.warn("O gltfio recusou '${file.name}': ${error.message}")
            return
        }

        if (asset == null) {
            AppLog.warn("O gltfio não conseguiu carregar '${file.name}'.")
            return
        }

        resourceLoader.loadResources(asset)
        asset.releaseSourceData()

        modelAsset = asset
        loadedModelFile = file

        // O nó raiz do asset pode já ter um componente de transformação (o gltfio cria um
        // para cada nó); se não tiver, criamos — é nele que a matriz do marcador entra.
        modelTransform = transformManager.getInstance(asset.root)
            .takeIf { it != 0 }
            ?: transformManager.create(asset.root)

        AppLog.info(
            "Modelo na cena: ${file.name} " +
                "(${asset.entities.size} entidades, ${asset.boundingBox.halfExtent.contentToString()})",
        )
    }

    /**
     * Posiciona o modelo — e só o coloca na cena quando o marcador está rastreado.
     *
     * Sem rastreio, o asset é **removido** da cena em vez de ficar com uma transformação
     * degenerada: o modelo não pode aparecer no quadro (nem por um instante) enquanto a
     * pose não vale.
     */
    private fun updatePlacement(worldMatrix: FloatArray?) {
        val asset = modelAsset ?: return
        val visible = worldMatrix != null

        if (worldMatrix != null) {
            transformManager.setTransform(modelTransform, worldMatrix)
        }

        if (visible == modelInScene) return
        if (visible) scene.addEntities(asset.entities) else scene.removeEntities(asset.entities)
        modelInScene = visible
    }

    /**
     * Lê os pixels do quadro que acabou de ser desenhado.
     *
     * O `readPixels` do Filament é **assíncrono**: ele agenda a cópia e avisa pelo
     * retorno de chamada do descritor. O `flushAndWait` processa a fila de comandos do
     * motor até a cópia terminar (sem ele o retorno de chamada só aconteceria no próximo
     * quadro — e a captura de tela sairia atrasada ou vazia).
     */
    private fun readBack(): SceneFrame? {
        val pixels = ByteArray(width * height * 4)
        val copied = AtomicBoolean(false)

        renderer.readPixels(
            0,
            0,
            width,
            height,
            Texture.PixelBufferDescriptor(
                pixels,
                pixels.size,
                Texture.Format.RGBA,
                Texture.Type.UBYTE,
            ) { copied.set(true) },
        )

        engine.flushAndWait()
        if (!copied.get()) {
            AppLog.warn("O quadro de ${width}×$height não foi lido de volta a tempo.")
            return null
        }

        // O Filament devolve as linhas de baixo para cima (convenção do OpenGL) e o
        // Compose desenha de cima para baixo.
        return SceneFrame(pixels, width, height).flippedVertically()
    }

    /** Solta o modelo carregado (entidades, recursos e a cópia dos bytes). */
    private fun releaseModel() {
        val asset = modelAsset ?: return

        if (modelInScene) {
            scene.removeEntities(asset.entities)
            modelInScene = false
        }

        assetLoader.destroyAsset(asset)
        modelAsset = null
        loadedModelFile = null
        modelTransform = 0
    }

    /**
     * Compila o material do plano de fundo **em tempo de execução**: superfície não
     * iluminada, com a textura do vídeo na cor de base e as coordenadas de textura do
     * quadrilátero. O `prepareMaterial` é obrigatório na linguagem de materiais do
     * Filament, mesmo num material não iluminado.
     *
     * ## Plataforma e API de destino: `ALL` nos dois, e por quê
     *
     * O motor gráfico escolhe o backend sozinho (aqui ele escolhe **Vulkan**; numa máquina
     * sem Vulkan, OpenGL). O padrão do compilador, porém, é o de um material de celular em
     * OpenGL — só GLSL. Um material sem SPIR-V num motor Vulkan faz o Filament **abortar o
     * processo** (não é uma exceção: é uma asserção nativa), com a mensagem
     * *"the material was not built for any of the Vulkan backend's supported shader
     * languages (SPIR-V)"*. Compilar para todas as plataformas e APIs custa alguns
     * milissegundos na inicialização e evita amarrar o aplicativo a um backend.
     *
     * ## Ciclo do compilador
     *
     * O `Filamat` precisa ser inicializado antes e encerrado depois — é um recurso global
     * da biblioteca nativa, e deixá-lo ligado mantém memória reservada.
     */
    private fun compileBackgroundMaterial(): ByteArray {
        Filamat.init()
        try {
            val compiled = MaterialBuilder()
                .name("arkz_background")
                .shading(MaterialBuilder.Shading.UNLIT)
                .platform(MaterialBuilder.Platform.ALL)
                .targetApi(MaterialBuilder.TargetApi.ALL)
                .require(VertexBuffer.VertexAttribute.UV0)
                .samplerParameter(
                    MaterialBuilder.SamplerType.SAMPLER_2D,
                    MaterialBuilder.SamplerFormat.FLOAT,
                    MaterialBuilder.ParameterPrecision.DEFAULT,
                    CAMERA_FRAME_PARAMETER,
                )
                .material(BACKGROUND_MATERIAL_CODE)
                .build()

            check(compiled.isValid) {
                "o filamat não conseguiu compilar o material do plano de fundo"
            }
            return compiled.buffer
        } finally {
            Filamat.shutdown()
        }
    }

    /**
     * Libera **tudo**, na ordem inversa da criação, e destrói o motor gráfico.
     *
     * O `Engine.destroy()` no fim é obrigatório: sem ele o processo não encerra sozinho no
     * Windows (o motor mantém uma thread de driver viva). Como o motor é o dono de todos os
     * recursos criados a partir dele, destruí-lo é o que garante que nada fica para trás —
     * inclusive os materiais dos *uber shaders* do gltfio, que não são destruídos um a um.
     */
    override fun close() {
        releaseModel()

        // A entidade do plano de fundo sai ANTES dos recursos que ela usa: o Filament
        // aborta (com asserção nativa) se um material, vértice ou textura for destruído
        // enquanto um renderizável ainda o referencia — a mensagem é
        // "destroying MaterialInstance ... which is still in use by Renderable".
        scene.removeEntity(backgroundEntity)
        engine.destroyEntity(backgroundEntity)

        backgroundTexture?.let { engine.destroyTexture(it) }
        backgroundTexture = null

        engine.destroyIndexBuffer(backgroundIndexBuffer)
        engine.destroyVertexBuffer(backgroundVertexBuffer)
        engine.destroyMaterialInstance(backgroundInstance)
        engine.destroyMaterial(backgroundMaterial)

        resourceLoader.close()
        AssetLoader.destroy(assetLoader)

        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = null

        engine.destroyRenderer(renderer)
        engine.destroyCamera(camera)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroy()

        AppLog.info("Renderizador Filament finalizado.")
    }

    companion object {

        /** Tamanho inicial do quadro (a janela ajusta depois com [resize]). */
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720

        /**
         * Distância do plano de fundo, em metros.
         *
         * Precisa ser maior que a distância de uso do marcador (30–60 cm, e no máximo
         * alguns metros) e menor que [CameraProjection.FAR_METERS] — 50 m fica folgado nos
         * dois lados.
         */
        const val BACKGROUND_DISTANCE_METERS = 50.0

        /**
         * Intensidades das luzes, em **lux**.
         *
         * O Filament ilumina cenas usando a exposição de uma câmera fotográfica ao sol
         * (f/16, 1/125 s, ISO 100), em que a luz do dia tem cerca de 100 000 lux — daí os
         * números. A luz principal vem de cima e de trás da câmera (como a iluminação de
         * um ambiente) e o preenchimento fraco vem do lado oposto, para o modelo não ficar
         * com metade preta.
         */
        private const val KEY_LIGHT_LUX = 90_000f
        private const val FILL_LIGHT_LUX = 25_000f

        /** Nome do parâmetro da textura, usado no shader como `materialParams_...`. */
        private const val CAMERA_FRAME_PARAMETER = "cameraFrame"

        /**
         * Quadrilátero unitário do plano de fundo: posição (x, y, z) e textura (u, v) por
         * vértice, **v = 0 na linha de cima da imagem** — a mesma ordem dos pixels do
         * quadro da câmera (de cima para baixo).
         */
        private val QUAD_VERTICES = floatArrayOf(
            -0.5f, -0.5f, 0f, 0f, 1f,
            0.5f, -0.5f, 0f, 1f, 1f,
            0.5f, 0.5f, 0f, 1f, 0f,
            -0.5f, 0.5f, 0f, 0f, 0f,
        )

        private val QUAD_INDICES = shortArrayOf(0, 1, 2, 0, 2, 3)

        private val BACKGROUND_MATERIAL_CODE = """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                material.baseColor = texture(materialParams_cameraFrame, getUV0());
            }
        """.trimIndent()
    }
}
