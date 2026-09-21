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

import com.arkz.armodelviewer.model.ModelFileStaging
import com.arkz.armodelviewer.model.ModelMetrics
import com.arkz.armodelviewer.model.Vec3
import org.lwjgl.assimp.AIMesh
import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.Assimp
import java.io.File

/**
 * Caixa envolvente do modelo, em **unidades do próprio arquivo**.
 *
 * É o dado que alimenta o [ModelMetrics] (normalização de tamanho e ancoragem no
 * marcador). A unidade é a do arquivo de propósito: o glTF define metros, mas CAD,
 * SketchUp e boa parte dos conversores de OBJ gravam **milímetros** — traduzir a unidade
 * aqui apagaria a informação que explica por que um modelo "carrega e não aparece".
 */
data class ModelBounds(
    val min: Vec3,
    val max: Vec3,
) {
    /** Centro da caixa. */
    val center: Vec3 get() = Vec3(
        (min.x + max.x) / 2f,
        (min.y + max.y) / 2f,
        (min.z + max.z) / 2f,
    )

    /** Metade da extensão em cada eixo. */
    val halfExtent: Vec3 get() = Vec3(
        (max.x - min.x) / 2f,
        (max.y - min.y) / 2f,
        (max.z - min.z) / 2f,
    )

    /** Maior dimensão, em unidades do arquivo. */
    val largestDimension: Float
        get() = maxOf(max.x - min.x, max.y - min.y, max.z - min.z)

    /** Métricas para a ancoragem — a ponte com o `ModelMetrics` do app Android. */
    fun toMetrics(): ModelMetrics = ModelMetrics(center = center, halfExtent = halfExtent)

    override fun toString(): String =
        "min=(%.3f, %.3f, %.3f) max=(%.3f, %.3f, %.3f) maior=%.3f".format(
            min.x, min.y, min.z, max.x, max.y, max.z, largestDimension,
        )
}

/**
 * Modelo pronto para o renderizador.
 *
 * @property loadFile arquivo que o renderizador deve abrir (o próprio escolhido pelo
 *   usuário quando já é glTF, ou o `.glb` convertido ao lado dele).
 * @property bounds caixa envolvente, em unidades do arquivo carregado.
 * @property meshCount/triangleCount estatísticas para o diagnóstico.
 * @property converted `true` quando houve conversão para GLB.
 */
data class PreparedModel(
    val loadFile: File,
    val bounds: ModelBounds,
    val meshCount: Int,
    val triangleCount: Int,
    val converted: Boolean,
)

/**
 * Lê os formatos que o Filament **não** entende (`.obj`, `.stl`, `.ply`, `.3mf`) e os
 * converte para GLB — o mesmo caminho que o SceneView faz no Android, em Kotlin puro, e
 * que aqui é feito pelo **Assimp** (binding oficial do LWJGL).
 *
 * ## Por que converter, em vez de desenhar direto
 *
 * O Filament lê glTF/GLB e nada mais. Converter com o Assimp (1) reaproveita um leitor
 * maduro para OBJ/STL/PLY/3MF, (2) produz **um** caminho de renderização (gltfio) em vez
 * de quatro, e (3) mantém o comportamento do app Android, em que escolher um `.obj` gera
 * um `.glb` ao lado do arquivo preparado — mesma pasta, mesmo nome.
 *
 * ## Flags de importação, e o que cada uma resolve
 *
 *  * `aiProcess_Triangulate` — garante triângulos (OBJ pode trazer n-gons, que o glTF não
 *    representa);
 *  * `aiProcess_PreTransformVertices` — **aplica as transformações dos nós nos
 *    vértices**: a caixa envolvente sai correta (e no referencial do arquivo) sem compor
 *    matrizes de nó, e o GLB resultante fica plano e simples;
 *  * `aiProcess_GenBoundingBoxes` — **calcula a caixa de cada malha**. Sem esta flag, a
 *    caixa vem zerada: foi o que fez o app Android precisar das métricas depois de
 *    carregar o modelo, e é o que aqui tem de acontecer na leitura — sem ela, o modelo
 *    carrega e **não aparece** (a normalização vira 1 e o tamanho fica em unidades cruas);
 *  * `aiProcess_GenNormals` — gera as normais quando o arquivo não as tem (STL, e parte
 *    dos PLY). O Filament precisa delas para iluminar, e **descarta as primitivas sem
 *    normais** na carga: sem esta flag, um STL viraria um GLB válido que o renderizador
 *    ignora — de novo o sintoma "carrega e não aparece". A flag não mexe em arquivos que
 *    já trazem normais (o que preserva o sombreamento facetado dos modelos de CAD);
 *  * `aiProcess_JoinIdenticalVertices` e `aiProcess_SortByPType` — limpeza padrão;
 *  * `aiProcess_ValidateDataStructure` — falha cedo em arquivo corrompido, em vez de
 *    gerar uma cena estranha.
 */
object AssimpModelLoader {

    /** Extensões que precisam de conversão (as demais são glTF e o Filament lê direto). */
    val convertibleExtensions: Set<String> = setOf("obj", "stl", "ply", "3mf")

    private val IMPORT_FLAGS = Assimp.aiProcess_Triangulate or
        Assimp.aiProcess_PreTransformVertices or
        Assimp.aiProcess_GenBoundingBoxes or
        // Normais: o Filament (por meio do gltfio) precisa delas para iluminar o modelo —
        // um glTF sem normais tem as primitivas DESCARTADAS na carga, e o sintoma é o
        // modelo carregar e não aparecer. A flag só age quando o arquivo não traz normais
        // (STL e alguns PLY), preservando as normais dos modelos que já as têm.
        Assimp.aiProcess_GenNormals or
        Assimp.aiProcess_JoinIdenticalVertices or
        Assimp.aiProcess_SortByPType or
        Assimp.aiProcess_ValidateDataStructure

    /** Identificador do exportador binário de glTF 2.0 no Assimp (`.glb`). */
    private const val GLB_FORMAT_ID = "glb2"

    /**
     * Prepara o arquivo escolhido pelo usuário: lê com o Assimp, mede a caixa envolvente
     * e, quando o formato não é glTF, grava um `.glb` **ao lado** do original.
     *
     * É operação de CPU (importar e exportar podem levar segundos num modelo grande):
     * quem chama deve fazer isso fora da thread de interface.
     */
    fun prepare(source: File, onLog: (String) -> Unit = {}): Result<PreparedModel> = runCatching {
        require(source.isFile) { "Arquivo não encontrado: ${source.absolutePath}" }

        val extension = source.extension.lowercase()
        require(extension in ModelFileStaging.supportedExtensions) {
            ModelFileStaging.unsupportedFormatMessage(extension.ifEmpty { "desconhecido" })
        }

        val scene = Assimp.aiImportFile(source.absolutePath, IMPORT_FLAGS)
            ?: error("Não foi possível ler '${source.name}'${assimpErrorSuffix()}")

        try {
            val bounds = boundsOf(scene)
            val triangles = triangleCountOf(scene)
            require(triangles > 0) { "'${source.name}' não tem triângulos — a malha está vazia?" }

            val needsConversion = extension in convertibleExtensions
            val loadFile = if (needsConversion) {
                val target = File(source.parentFile, "${source.nameWithoutExtension}.glb")
                exportGlb(scene, target)
                onLog(
                    "Convertido para ${target.name} " +
                        "(${target.length() / 1024} kB, $triangles triângulos).",
                )
                target
            } else {
                onLog("'${source.name}' já é glTF: carregado sem conversão.")
                source
            }

            onLog("Caixa envolvente de ${source.name}: $bounds")
            PreparedModel(
                loadFile = loadFile,
                bounds = bounds,
                meshCount = scene.mNumMeshes(),
                triangleCount = triangles,
                converted = needsConversion,
            )
        } finally {
            // A cena é memória NATIVA: precisa ser liberada explicitamente.
            Assimp.aiReleaseImport(scene)
        }
    }

    /** Mensagem de erro do Assimp, quando houver, no fim de um texto. */
    private fun assimpErrorSuffix(): String =
        Assimp.aiGetErrorString()?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "."

    /**
     * Caixa envolvente da cena inteira.
     *
     * Como a importação usa `aiProcess_PreTransformVertices`, cada malha já está no
     * referencial do arquivo — a conta é o mínimo e o máximo das caixas de todas as
     * malhas, sem compor matrizes de nó (e sem o risco de esquecer uma transformação).
     */
    private fun boundsOf(scene: AIScene): ModelBounds {
        val meshes = checkNotNull(scene.mMeshes()) { "A cena do Assimp não expõe as malhas." }
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        var any = false

        for (index in 0 until scene.mNumMeshes()) {
            val mesh = AIMesh.create(meshes.get(index))

            // Malha sem vértices tem caixa "inválida" (min > max): ignorar.
            if (mesh.mNumVertices() <= 0) continue

            val min = mesh.mAABB().mMin()
            val max = mesh.mAABB().mMax()

            minX = minOf(minX, min.x())
            minY = minOf(minY, min.y())
            minZ = minOf(minZ, min.z())
            maxX = maxOf(maxX, max.x())
            maxY = maxOf(maxY, max.y())
            maxZ = maxOf(maxZ, max.z())
            any = true
        }

        check(any) { "A cena não tem nenhuma malha com vértices." }
        return ModelBounds(Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ))
    }

    /** Total de triângulos (com `aiProcess_Triangulate`, cada face tem três índices). */
    private fun triangleCountOf(scene: AIScene): Int {
        val meshes = checkNotNull(scene.mMeshes()) { "A cena do Assimp não expõe as malhas." }
        var triangles = 0
        for (index in 0 until scene.mNumMeshes()) {
            triangles += AIMesh.create(meshes.get(index)).mNumFaces()
        }
        return triangles
    }

    /**
     * Exporta a cena já importada como GLB binário, **ao lado** do arquivo original.
     *
     * O destino é um arquivo (e não um `blob` em memória) de propósito: é o mesmo
     * comportamento do app Android e deixa o `.glb` disponível para inspeção quando algo
     * não aparece na cena — além de manter o caminho de carregamento simples (um
     * caminho de arquivo para o `gltfio`).
     */
    private fun exportGlb(scene: AIScene, target: File) {
        val result = Assimp.aiExportScene(scene, GLB_FORMAT_ID, target.absolutePath, 0)
        check(result == Assimp.aiReturn_SUCCESS) {
            "Falha ao converter para GLB${assimpErrorSuffix()}"
        }
        check(target.isFile && target.length() > 0) { "A conversão não gerou arquivo." }
    }
}
