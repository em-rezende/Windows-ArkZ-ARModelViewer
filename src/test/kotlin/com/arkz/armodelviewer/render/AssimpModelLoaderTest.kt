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

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes da leitura/conversão de modelos com o Assimp.
 *
 * Usam os **modelos de teste do próprio repositório** (`3d_models/`), que existem nas
 * quatro extensões do mesmo logo — assim a conversão de cada formato é validada com
 * geometria de verdade, sem GPU. Cada teste trabalha numa **cópia** em pasta temporária:
 * o `.glb` convertido fica lá, e o repositório não acumula artefatos.
 *
 * O diretório de trabalho do teste é a raiz do projeto (padrão do Gradle), então
 * `3d_models/...` resolve sem caminho absoluto.
 */
class AssimpModelLoaderTest {

    @TempDir
    lateinit var tempDir: Path

    /** Copia um modelo do repositório para a pasta temporária do teste. */
    private fun copyModel(name: String): File {
        val source = File("3d_models", name)
        assertTrue(source.isFile, "modelo de teste ausente: ${source.absolutePath}")

        val target = tempDir.resolve(name).toFile()
        source.copyTo(target, overwrite = true)
        return target
    }

    @Test
    fun `converte o OBJ em GLB ao lado do arquivo`() {
        val prepared = AssimpModelLoader.prepare(copyModel("ArkZ_logo.obj")).getOrThrow()

        assertTrue(prepared.converted, "um .obj precisa de conversão para o Filament")
        assertEquals("ArkZ_logo.glb", prepared.loadFile.name)
        assertEquals(
            tempDir.toFile(),
            prepared.loadFile.parentFile,
            "o GLB fica ao lado do original",
        )
        assertIsGlb(prepared.loadFile)
        assertTrue(prepared.triangleCount > 0, "triângulos: ${prepared.triangleCount}")
        assertTrue(prepared.bounds.largestDimension > 0f, "medida: ${prepared.bounds}")
    }

    @Test
    fun `converte o STL e o PLY em GLB`() {
        listOf("ArkZ_logo.stl", "ArkZ_logo.ply").forEach { name ->
            val prepared = AssimpModelLoader.prepare(copyModel(name)).getOrThrow()

            assertTrue(prepared.converted, "'$name' precisa de conversão")
            assertIsGlb(prepared.loadFile)
            assertTrue(prepared.triangleCount > 0, "'$name' sem triângulos")
        }
    }

    @Test
    fun `um arquivo glTF nao precisa de conversao`() {
        val source = copyModel("Edificio.glb")

        val prepared = AssimpModelLoader.prepare(source).getOrThrow()

        assertFalse(prepared.converted, "glTF é lido direto pelo Filament")
        assertEquals(source, prepared.loadFile)
        assertTrue(prepared.bounds.largestDimension > 0f, "medida: ${prepared.bounds}")
        assertTrue(prepared.meshCount > 0)
    }

    @Test
    fun `as tres exportacoes do mesmo modelo dao a mesma medida`() {
        // O mesmo logo em OBJ, STL e PLY: se a leitura de um formato aplicasse escala
        // errada (ou ignorasse a unidade do arquivo), as medidas não bateriam.
        val measures = listOf("ArkZ_logo.obj", "ArkZ_logo.stl", "ArkZ_logo.ply")
            .associateWith { name ->
                AssimpModelLoader.prepare(copyModel(name)).getOrThrow().bounds.largestDimension
            }

        val largest = measures.values.max()
        val smallest = measures.values.min()
        assertTrue(
            smallest / largest > 0.9f,
            "medidas divergentes entre os formatos: $measures",
        )
    }

    @Test
    fun `a caixa envolvente alimenta as metricas de ancoragem`() {
        val prepared = AssimpModelLoader.prepare(copyModel("ArkZ_logo.stl")).getOrThrow()
        val metrics = prepared.bounds.toMetrics()

        // A maior dimensão do arquivo normalizada: é essa conta que faz o tamanho
        // escolhido pelo usuário ser o tamanho REAL em metros, independentemente da
        // unidade em que o modelo foi exportado.
        val scale = ModelPlacement.scaleFor(metrics, sizeMeters = 0.2f)
        assertEquals(0.2f, prepared.bounds.largestDimension * scale, 1e-3f)
    }

    @Test
    fun `formato nao suportado e recusado com a mensagem do app`() {
        val dae = tempDir.resolve("cena.dae").toFile().apply { writeText("<COLLADA/>") }

        val result = AssimpModelLoader.prepare(dae)

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("COLLADA"), "mensagem inesperada: $message")
    }

    @Test
    fun `arquivo corrompido falha com mensagem em vez de estourar`() {
        val broken = tempDir.resolve("quebrado.obj").toFile()
        broken.writeText("isto não é um OBJ de verdade")

        val result = AssimpModelLoader.prepare(broken)

        assertTrue(result.isFailure, "um arquivo ilegível tem de virar falha tratável")
        assertTrue(result.exceptionOrNull()?.message.orEmpty().isNotBlank())
    }

    @Test
    fun `arquivo inexistente falha com mensagem`() {
        val result = AssimpModelLoader.prepare(tempDir.resolve("nao-existe.stl").toFile())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().isNotBlank())
    }

    @Test
    fun `o GLB exportado traz malhas com posicoes e normais`() {
        // O texto JSON do GLB é inspecionado de propósito: é o que o gltfio (o leitor do
        // Filament) vai ler. Uma malha sem NORMAL faz o Filament **descartar a primitiva**
        // — o arquivo é válido, o modelo simplesmente não aparece, e nenhum erro é
        // levantado. Este teste é a rede que pega essa regressão.
        val prepared = AssimpModelLoader.prepare(copyModel("ArkZ_logo.stl")).getOrThrow()

        val json = glbJson(prepared.loadFile)

        assertTrue(json.contains("\"meshes\""), "o GLB tem de declarar malhas")
        assertTrue(json.contains("\"primitives\""), "o GLB tem de declarar primitivas")
        assertTrue(json.contains("POSITION"), "as primitivas precisam de posições")
        assertTrue(
            json.contains("NORMAL"),
            "as primitivas precisam de normais (sem elas o Filament não desenha o modelo)",
        )
        assertTrue(json.contains("\"nodes\""), "o GLB tem de declarar nós")
    }

    /**
     * Extrai o bloco JSON de um GLB (o primeiro bloco do arquivo, logo após o cabeçalho de
     * 12 bytes e o cabeçalho do bloco de 8).
     */
    private fun glbJson(file: File): String {
        val bytes = file.readBytes()
        val jsonLength = readIntLe(bytes, 12)
        return String(bytes, 20, jsonLength, Charsets.UTF_8)
    }

    /**
     * Confere o cabeçalho binário do GLB: assinatura `glTF`, versão 2, tamanho total
     * declarado igual ao do arquivo e o primeiro bloco sendo JSON.
     *
     * É o que garante que o arquivo entregue ao `gltfio` é um glTF **binário** de
     * verdade, e não um JSON de texto com a extensão trocada (o exportador do Assimp tem
     * os dois modos: `gltf2` e `glb2`).
     */
    private fun assertIsGlb(file: File) {
        val bytes = file.readBytes()
        assertTrue(bytes.size > 20, "arquivo pequeno demais para ser um GLB: ${bytes.size} bytes")

        assertEquals(0x46546C67, readIntLe(bytes, 0), "assinatura 'glTF'")
        assertEquals(2, readIntLe(bytes, 4), "versão do glTF")
        assertEquals(bytes.size, readIntLe(bytes, 8), "tamanho total declarado no cabeçalho")
        assertEquals(0x4E4F534A, readIntLe(bytes, 16), "o primeiro bloco deve ser o JSON")
    }

    /** Inteiro de 32 bits little-endian (o GLB é especificado assim). */
    private fun readIntLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
