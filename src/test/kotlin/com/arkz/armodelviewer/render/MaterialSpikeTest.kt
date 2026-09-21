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

import io.github.erkko68.filament.VertexBuffer
import io.github.erkko68.filament.filamat.Filamat
import io.github.erkko68.filament.filamat.MaterialBuilder
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **Spike** da compilação de materiais: o `filamat` do binding compila, nesta máquina, o
 * material do plano de fundo (o vídeo da câmera)?
 *
 * ## Por que este teste existe
 *
 * Os jars do Filament **não trazem nenhum `.filamat`** e o `matc` (compilador oficial) só
 * existe como executável — depender dele obrigaria a baixar o SDK do Filament durante o
 * build. A alternativa adotada é compilar o material **em tempo de execução** com o módulo
 * `filamat` do próprio binding (decisão 6 do roadmap e do README).
 *
 * Se este teste passa, a última peça desconhecida da etapa 4 está resolvida: dá para criar
 * o material do quadro de fundo (não iluminado, com a textura da câmera) sem asset externo
 * e sem etapa de build.
 *
 * O material compilado aqui é **exatamente** o do plano de fundo: superfície não
 * iluminada, uma textura (`sampler2D`) com a imagem da câmera e coordenadas de textura
 * (UV0) do plano. `prepareMaterial` é obrigatório na linguagem de materiais do Filament.
 */
class MaterialSpikeTest {

    @Test
    fun `o compilador de materiais produz um material nao iluminado com textura`() {
        Filamat.init()
        val compiled = try {
            MaterialBuilder()
                .name("arkz_background")
                .shading(MaterialBuilder.Shading.UNLIT)
                // O motor escolhe o backend sozinho (Vulkan, aqui) e um material só-GLSL
                // faz o Filament abortar; ver o KDoc de `FilamentRenderer`.
                .platform(MaterialBuilder.Platform.ALL)
                .targetApi(MaterialBuilder.TargetApi.ALL)
                .require(VertexBuffer.VertexAttribute.UV0)
                .samplerParameter(
                    MaterialBuilder.SamplerType.SAMPLER_2D,
                    MaterialBuilder.SamplerFormat.FLOAT,
                    MaterialBuilder.ParameterPrecision.DEFAULT,
                    CAMERA_FRAME_PARAMETER,
                )
                .material(
                    """
                    void material(inout MaterialInputs material) {
                        prepareMaterial(material);
                        material.baseColor = texture(materialParams_cameraFrame, getUV0());
                    }
                    """.trimIndent(),
                )
                .build()
        } finally {
            Filamat.shutdown()
        }

        assertTrue(compiled.isValid, "o compilador devolveu um material inválido")

        val bytes = compiled.buffer
        assertTrue(
            bytes.size > 512,
            "um material compilado tem shaders de verdade dentro; vieram ${bytes.size} bytes",
        )
    }

    private companion object {
        /** Nome do parâmetro de textura, usado dentro do shader como `materialParams_...`. */
        const val CAMERA_FRAME_PARAMETER = "cameraFrame"
    }
}
