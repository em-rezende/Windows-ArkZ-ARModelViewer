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

    /**
     * Modelo em **unidades do arquivo**, na convenção dos exportadores de CAD/SketchUp:
     * 20 unidades de largura (X), 10 de profundidade (Y) e 20 de **altura (Z)** — o "para cima"
     * do arquivo é o Z, e é ele que o `ModelPlacement` leva para a normal do marcador.
     */
    private val metrics = ModelMetrics(
        center = Vec3(0f, 0f, 10f),
        halfExtent = Vec3(10f, 5f, 10f),
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

        // Com a rotação ZERO o modelo já aparece EM PÉ: o "para cima" do arquivo (+Y) **é** a
        // normal do marcador — o Y do referencial do ARCore, que é o que o `solvePnP` produz
        // (decisão 34). Não existe rotação de apoio: ela existia enquanto o código supunha o
        // plano do marcador em XY, e o efeito era o modelo deitar sobre a figura.
        val top = ModelPlacement.apply(matrix, Vec3(0f, 0f, 20f))
        assertEquals(0f, top.x, 1e-5f, "o topo fica no centro da largura")
        assertEquals(0.2f, top.y, 1e-5f, "o topo do modelo sobe pela normal")
        assertEquals(0f, top.z, 1e-5f, "e não anda na altura da imagem")

        // O centro fica na metade da altura, acima do plano.
        val center = ModelPlacement.apply(matrix, metrics.center)
        assertEquals(0f, center.x, 1e-5f, "centralizado na largura")
        assertEquals(0.1f, center.y, 1e-5f, "metade da altura acima do plano")
        assertEquals(0f, center.z, 1e-5f, "centrado na altura da imagem")

        // Um canto da BASE (z = 0 no arquivo) fica exatamente no plano da figura.
        val base = ModelPlacement.apply(matrix, Vec3(-10f, -5f, 0f))
        assertEquals(-0.1f, base.x, 1e-5f)
        assertEquals(0f, base.y, 1e-5f, "a base apoia no plano da figura (o eixo da normal)")
        assertEquals(0.05f, base.z, 1e-5f)
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

        // A normal é o **Y** do referencial do marcador (X = largura, Y = normal, Z = altura NA
        // imagem): é o único eixo em que a elevação mexe. O nome do controle — "Elevação Z" —
        // veio do app Android e é o rótulo da interface, não o eixo (decisão 34).
        assertEquals(base.x, lifted.x, 1e-5f)
        assertEquals(base.z, lifted.z, 1e-5f)
        assertEquals(base.y + 0.5f, lifted.y, 1e-5f)
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
            offsetMeters = Vec3(0.2f, 0f, -0.1f),
        )

        val center = ModelPlacement.apply(centered, metrics.center)
        val moved = ModelPlacement.apply(dragged, metrics.center)

        // O arrasto entra como deslocamento no PLANO — X (largura) e Z (altura NA imagem) — e
        // não toca na normal (Y): arrastar nunca tira o modelo do papel (decisão 34).
        assertEquals(center.x + 0.2f, moved.x, 1e-5f)
        assertEquals(center.z - 0.1f, moved.z, 1e-5f)
        assertEquals(center.y, moved.y, 1e-5f, "o arrasto é no plano: a normal não se mexe")

        // E a base continua apoiada no plano: o arrasto não levanta nem afunda o modelo.
        val base = ModelPlacement.apply(dragged, Vec3(-10f, -5f, 0f))
        assertEquals(0f, base.y, 1e-5f)
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

        val topUpright = ModelPlacement.apply(upright, Vec3(0f, 0f, 20f))
        val topLaidDown = ModelPlacement.apply(laidDown, Vec3(0f, 0f, 20f))

        // O X do slider gira no **X do marcador** (a largura da figura): o 90° deita o modelo
        // na altura da imagem, e o topo deixa a normal — é o par de eixos que o usuário vê.
        assertEquals(0.2f, topUpright.y, 1e-5f, "em pé: o topo sobe pela normal")
        assertEquals(0f, topLaidDown.y, 1e-5f, "deitado: o topo sai da normal")
        assertEquals(0.2f, topLaidDown.z, 1e-5f, "e vai para a altura NA imagem")
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

    @Test
    fun `girar o marcador pela sua normal gira o modelo em torno de si, sem deita-lo`() {
        // O referencial do marcador é o do ARCore — e o que o `solvePnP` produz a partir de
        // `MarkerDetector.markerObjectPointsMat`: X = largura, **Y = NORMAL** (o eixo que sai
        // do papel, com zero nas quatro quinas do objeto) e Z = altura NA imagem.
        //
        // Girar a folha impressa sobre a mesa é, portanto, girar o marcador em torno do
        // PRÓPRIO eixo Y. Se o modelo está de pé sobre o papel, o "para cima" dele está
        // nesse mesmo eixo: girar a folha NÃO pode mudar a direção do "para cima" no mundo —
        // o modelo gira em torno de si mesmo e continua apoiado na figura.
        //
        // Era este o defeito relatado em campo: com o "para cima" do modelo caindo dentro do
        // plano (no eixo Z), girar a folha fazia o modelo girar em torno do eixo errado.
        val normal = Vec3(0f, 1f, 0f)

        for (passo in 0..11) {
            val graus = passo * 30.0
            val matrix = ModelPlacement.worldMatrix(
                markerPose = spinAroundNormal(graus),
                metrics = metrics,
                sizeMeters = 0.2f,
                rotationDegrees = Vec3.ZERO,
                elevationMeters = 0f,
            )

            // Direção do "para cima" do arquivo (+Z, a altura) depois de todo o empilhamento.
            val base = ModelPlacement.apply(matrix, Vec3(0f, 0f, 0f))
            val top = ModelPlacement.apply(matrix, Vec3(0f, 0f, 20f))
            val up = Vec3(top.x - base.x, top.y - base.y, top.z - base.z)

            // O "para cima" continua sendo a normal em qualquer giro da folha: o modelo gira em
            // torno de si mesmo (0,2 m de altura, o tamanho escolhido) e não deita.
            assertEquals(0f, up.x, 1e-5f, "com a folha a $graus° o 'para cima' saiu da normal")
            assertEquals(0.2f, up.y, 1e-5f, "com a folha a $graus° o 'para cima' saiu da normal")
            assertEquals(0f, up.z, 1e-5f, "com a folha a $graus° o 'para cima' saiu da normal")

            // E continua de pé na figura: o topo sobe 0,2 m pela normal, centrado no plano.
            assertEquals(0f, top.x, 1e-5f)
            assertEquals(0.2f, top.y, 1e-5f, "o topo do modelo sobe pela normal (Y do marcador)")
            assertEquals(0f, top.z, 1e-5f)
        }

        // A normal do marcador é a 2ª coluna da rotação da pose — o eixo pelo qual a folha gira.
        val pose = spinAroundNormal(0.0)
        assertEquals(1.0, pose[1, 1], 1e-6, "a coluna da normal da pose deveria ser +Y")
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
