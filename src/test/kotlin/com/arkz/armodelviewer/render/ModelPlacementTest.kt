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

import com.arkz.armodelviewer.ar.Pose
import com.arkz.armodelviewer.model.ModelMetrics
import com.arkz.armodelviewer.model.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Disabled

/**
 * Testes da ancoragem do modelo.
 *
 * A conta é curta — e é a que decide se o modelo aparece **no lugar certo**. Um erro de
 * referencial aqui produz os dois sintomas clássicos: o modelo carrega e não aparece
 * (deslocado para longe) ou aparece de cabeça para baixo / enterrado no marcador. Como
 * tudo é aritmética pura, dá para conferir ponto por ponto, sem GPU.
 *
 * As contas geométricas usam a **pose de identidade** (o referencial do marcador é o do
 * mundo), o que deixa as asserções legíveis no próprio referencial do marcador — o mesmo
 * em que o `ModelMetrics` trabalha.
 *
 * Convenção do referencial do marcador (a do ARCore para imagens, mantida de propósito):
 * **X** = largura da imagem, **Y** = normal (sai do plano), **Z** = altura NA imagem.
 */
class ModelPlacementTest {

    /** Modelo em milímetros, como os exportados por CAD: 20 unidades = 20 mm. */
    private val metrics = ModelMetrics(
        center = Vec3(0f, 10f, 0f),
        halfExtent = Vec3(10f, 10f, 5f),
    )

    private val identityPose = Pose(
        rotation = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
        translation = Vec3.ZERO,
    )

    @Test
    fun `a escala normaliza a maior dimensao para o tamanho escolhido`() {
        // Maior dimensão do arquivo: 20 unidades (meia-extensão 10 em X e Y).
        val scale = ModelPlacement.scaleFor(metrics, sizeMeters = 0.2f)

        assertEquals(0.01f, scale, 1e-6f)
        assertEquals(0.2f, metrics.largestDimension * scale, 1e-5f)
    }

    @Test
    fun `o modelo fica em pe sobre a figura, centralizado e com a base no plano`() {
        val matrix = ModelPlacement.worldMatrix(
            markerPose = identityPose,
            metrics = metrics,
            sizeMeters = 0.2f,
            rotationDegrees = Vec3.ZERO,
            elevationMeters = 0f,
        )

        // Com a rotação ZERO o modelo já aparece EM PÉ: o "para cima" do arquivo (Y) vai
        // para a normal do marcador (Z). Sem o apoio o modelo deita sobre a figura, e era
        // preciso girá-lo 90° em X à mão em cada modelo carregado.
        val top = ModelPlacement.apply(matrix, Vec3(0f, 20f, 0f))
        assertEquals(0f, top.x, 1e-5f)
        assertEquals(0f, top.y, 1e-5f)
        assertEquals(0.2f, top.z, 1e-5f, "o topo do modelo sobe pela normal")

        // O centro fica na metade da altura, acima do plano.
        val center = ModelPlacement.apply(matrix, metrics.center)
        assertEquals(0f, center.x, 1e-5f, "centralizado na largura")
        assertEquals(0f, center.y, 1e-5f, "centrado na altura da imagem")
        assertEquals(0.1f, center.z, 1e-5f, "metade da altura acima do plano")

        // Um canto da BASE (y = 0 no arquivo) fica exatamente no plano da figura.
        val base = ModelPlacement.apply(matrix, Vec3(-10f, 0f, -5f))
        assertEquals(-0.1f, base.x, 1e-5f)
        assertEquals(0.05f, base.y, 1e-5f)
        assertEquals(0f, base.z, 1e-5f, "a base apoia no plano da figura")
    }

    @Test
    fun `a elevacao desloca o modelo no eixo da normal do marcador`() {
        val point = Vec3(0f, 0f, 0f)
        val base = ModelPlacement.apply(
            ModelPlacement.worldMatrix(
                identityPose, metrics, sizeMeters = 0.2f, rotationDegrees = Vec3.ZERO,
                elevationMeters = 0f,
            ),
            point,
        )
        val lifted = ModelPlacement.apply(
            ModelPlacement.worldMatrix(
                identityPose, metrics, sizeMeters = 0.2f, rotationDegrees = Vec3.ZERO,
                elevationMeters = 0.5f,
            ),
            point,
        )

        // O plano da figura é XY e a normal é o eixo Z: é o único eixo em que a elevação
        // mexe — e é o "Elevação Z" da interface.
        assertEquals(base.x, lifted.x, 1e-5f)
        assertEquals(base.y, lifted.y, 1e-5f)
        assertEquals(base.z + 0.5f, lifted.z, 1e-5f)
    }

    @Test
    fun `o arrasto desloca o modelo no plano da figura`() {
        val centered = ModelPlacement.worldMatrix(
            identityPose, metrics, sizeMeters = 0.2f, rotationDegrees = Vec3.ZERO,
            elevationMeters = 0f,
        )
        val dragged = ModelPlacement.worldMatrix(
            identityPose, metrics, sizeMeters = 0.2f, rotationDegrees = Vec3.ZERO,
            elevationMeters = 0f,
            offsetMeters = Vec3(0.2f, -0.1f, 0f),
        )

        val center = ModelPlacement.apply(centered, metrics.center)
        val moved = ModelPlacement.apply(dragged, metrics.center)

        // O arrasto entra exatamente como deslocamento no plano (X e Y do marcador) e não
        // mexe na altura acima da figura (Z, o eixo da normal).
        assertEquals(center.x + 0.2f, moved.x, 1e-5f)
        assertEquals(center.y - 0.1f, moved.y, 1e-5f)
        assertEquals(center.z, moved.z, 1e-5f, "o arrasto é no plano: a normal não se mexe")

        // A base continua apoiada no plano: o arrasto não levanta nem afunda o modelo.
        val base = ModelPlacement.apply(dragged, Vec3(-10f, 0f, -5f))
        assertEquals(0f, base.z, 1e-5f)
    }

    @Test
    fun `a rotacao dos sliders acontece no referencial do marcador`() {
        val upright = ModelPlacement.worldMatrix(
            identityPose, metrics, sizeMeters = 0.2f, rotationDegrees = Vec3.ZERO,
            elevationMeters = 0f,
        )
        val laidDown = ModelPlacement.worldMatrix(
            identityPose, metrics, sizeMeters = 0.2f, rotationDegrees = Vec3(90f, 0f, 0f),
            elevationMeters = 0f,
        )

        val topUpright = ModelPlacement.apply(upright, Vec3(0f, 20f, 0f))
        val topLaidDown = ModelPlacement.apply(laidDown, Vec3(0f, 20f, 0f))

        assertEquals(0.2f, topUpright.z, 1e-5f, "em pé: o topo sobe pela normal")
        assertEquals(0f, topLaidDown.z, 1e-5f, "deitado: o topo fica no plano da figura")
        assertEquals(-0.2f, topLaidDown.y, 1e-5f, "e vai para o lado NA imagem")
    }

    @Test
    fun `o modelo acompanha a pose do marcador`() {
        // Marcador à frente da câmera (Z negativo, como no ARCore) e deslocado.
        val pose = Pose(
            rotation = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
            translation = Vec3(0.3f, -0.2f, -0.6f),
        )

        val matrix = ModelPlacement.worldMatrix(
            pose, metrics, sizeMeters = 0.2f, rotationDegrees = Vec3.ZERO, elevationMeters = 0f,
        )

        // A translação da matriz é a posição do marcador: é o que faz o conteúdo
        // "grudar" na figura impressa.
        assertEquals(0.3f, matrix[12], 1e-5f)
        assertEquals(-0.2f, matrix[13], 1e-5f)
        assertEquals(-0.6f, matrix[14], 1e-5f)
    }

    @Test
    fun `a multiplicacao de matrizes respeita a ordem`() {
        val translate = ModelPlacement.translation(Vec3(1f, 0f, 0f))
        val scale = ModelPlacement.uniformScale(2f)

        // Escalar e depois transladar ≠ transladar e depois escalar: é exatamente por
        // isso que a ordem (T · R · S) está documentada e testada.
        val scaleThenTranslate = ModelPlacement.apply(
            ModelPlacement.multiply(translate, scale),
            Vec3(1f, 0f, 0f),
        )
        val translateThenScale = ModelPlacement.apply(
            ModelPlacement.multiply(scale, translate),
            Vec3(1f, 0f, 0f),
        )

        assertEquals(3f, scaleThenTranslate.x, 1e-6f)
        assertEquals(4f, translateThenScale.x, 1e-6f)
    }

    @Test
    fun `a rotacao de euler preserva o tamanho e nao torce o modelo`() {
        val rotation = ModelPlacement.eulerRotation(Vec3(30f, -45f, 60f))
        val point = ModelPlacement.apply(rotation, Vec3(0.3f, -0.4f, 0.5f))
        val length = kotlin.math.sqrt(point.x * point.x + point.y * point.y + point.z * point.z)

        assertEquals(0.7071f, length, 1e-4f)
        assertTrue(
            rotation[3] == 0f && rotation[7] == 0f && rotation[11] == 0f,
            "a última linha de uma rotação em coluna-maior deve ser (0,0,0,1)",
        )
    }

    /**
     * **Pendência conhecida — decisão 34 do roadmap — com o teste pronto para o dia da correção.**
     *
     * Girar a folha impressa em torno da **normal** (o eixo que sai do papel) deve girar o modelo
     * em torno de si mesmo, de pé sobre a figura. No código de hoje o "para cima" do modelo é
     * colocado no eixo **Z** do marcador (a altura NA imagem) — é o que faz o modelo carregar de
     * pé "na imagem", comportamento que o teste em campo aprovou — e, por isso, girar a folha
     * pela normal **deita** o modelo.
     *
     * O referencial que o `solvePnP` produz tem a normal no **Y** (as quatro quinas do objeto têm
     * Y = 0: veja `MarkerDetector.markerObjectPointsMat`), então a correção existe e já foi
     * medida: com o "para cima" sobre a normal, o modelo gira em torno de si — é o que este teste
     * cobra. Ela foi **adiada a pedido do usuário**, para separar as correções: no mesmo pacote,
     * ela também mexeu na altura em que o modelo carregava (deitado) e no eixo do arrasto.
     *
     * Reative este teste **junto** com a correção: ele foi escrito para falhar no código de hoje.
     */
    @Disabled("decisão 34: correção do giro pela normal adiada a pedido do usuário")
    @Test
    fun `girar o marcador pela sua normal gira o modelo em torno de si, sem deita-lo`() {
        for (passo in 0..11) {
            val graus = passo * 30.0
            val matrix = ModelPlacement.worldMatrix(
                markerPose = spinAroundNormal(graus),
                metrics = metrics,
                sizeMeters = 0.2f,
                rotationDegrees = Vec3.ZERO,
                elevationMeters = 0f,
            )

            // Direção do "para cima" do arquivo (+Y) depois de todo o empilhamento.
            val base = ModelPlacement.apply(matrix, Vec3(0f, 0f, 0f))
            val top = ModelPlacement.apply(matrix, Vec3(0f, 20f, 0f))
            val up = Vec3(top.x - base.x, top.y - base.y, top.z - base.z)

            // Com a correção: o "para cima" continua sobre a normal (0,2 m = a altura do modelo)
            // em qualquer giro da folha — o modelo gira em torno de si e não deita.
            assertEquals(0f, up.x, 1e-5f, "com a folha a $graus° o 'para cima' saiu da normal")
            assertEquals(0.2f, up.y, 1e-5f, "com a folha a $graus° o 'para cima' saiu da normal")
            assertEquals(0f, up.z, 1e-5f, "com a folha a $graus° o 'para cima' saiu da normal")
        }
    }

    /** Pose de um marcador que gira em torno da **sua normal** (o eixo Y do referencial dele). */
    private fun spinAroundNormal(degrees: Double): Pose {
        // `eulerRotation` é coluna-maior; a `Pose` guarda a rotação em ordem de LINHA.
        val m = ModelPlacement.eulerRotation(Vec3(0f, degrees.toFloat(), 0f))
        val rotation = doubleArrayOf(
            m[0].toDouble(), m[4].toDouble(), m[8].toDouble(),
            m[1].toDouble(), m[5].toDouble(), m[9].toDouble(),
            m[2].toDouble(), m[6].toDouble(), m[10].toDouble(),
        )
        return Pose(rotation = rotation, translation = Vec3.ZERO)
    }
}
