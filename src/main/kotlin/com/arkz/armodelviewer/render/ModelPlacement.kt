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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Onde o modelo vai parar na cena: a transformação que leva a **geometria crua do
 * arquivo** até o **referencial do mundo** (o da câmera).
 *
 * ## A conta, e por que ela é assim
 *
 * ```
 * mundo = pose do marcador  ∘  [ translação de ancoragem · rotação · escala ]
 * ```
 *
 * * a **pose do marcador** (`DetectedMarker.centerPose`) leva um ponto do referencial do
 *   marcador para o mundo — é a metade que vem da detecção;
 * * o colchete é o que o app Android deixava a cargo do nó do modelo: a **escala**
 *   normalizada (maior dimensão = tamanho escolhido), a **rotação dos sliders** (em
 *   graus, no referencial do marcador) e a **translação de ancoragem** do
 *   `ModelMetrics.anchorPosition` — que centraliza o modelo no marcador e apoia a base
 *   dele sobre o plano da imagem.
 *
 * A ordem importa e é a mesma do app Android: escala e rotação acontecem em torno da
 * **origem do modelo**, e só depois ele é deslocado para o marcador. Fazer a conta "aqui
 * fora", sem renderizador nenhum, é o que permite **testá-la** — no app Android essa
 * conta vivia dentro do nó do SceneView e não tinha teste, e é justamente onde um erro de
 * referencial faz o modelo "carregar e não aparecer".
 *
 * ## Por que matriz em `FloatArray` de 16 posições (coluna-maior)
 *
 * É o formato do `mat4` do Filament e o que o `glUniformMatrix4fv` do OpenGL espera com
 * `transpose = false` — a mesma conta serve aos dois backends, sem conversão no meio.
 */
object ModelPlacement {

    /**
     * Escala a aplicar à geometria do arquivo para que a maior dimensão do modelo meça
     * [sizeMeters].
     *
     * A normalização vem das **unidades cruas do arquivo** (o glTF define metros, mas
     * CAD/SketchUp e a conversão de OBJ gravam milímetros), então este é o único lugar
     * onde a unidade do arquivo deixa de importar.
     */
    fun scaleFor(metrics: ModelMetrics, sizeMeters: Float): Float =
        metrics.normalization * sizeMeters

    /**
     * Matriz do modelo no referencial do mundo.
     *
     * @param markerPose pose do marcador reconhecido.
     * @param metrics bounding box do modelo, em unidades do arquivo.
     * @param sizeMeters tamanho escolhido para a maior dimensão (0,02 a 2 m).
     * @param rotationDegrees rotação do painel de ajustes, em graus, nos eixos do
     *   referencial do marcador.
     * @param elevationMeters deslocamento no eixo da **normal do marcador** (o
     *   "Elevação Z" da interface; −0,5 a +0,5 m).
     */
    fun worldMatrix(
        markerPose: Pose,
        metrics: ModelMetrics,
        sizeMeters: Float,
        rotationDegrees: Vec3,
        elevationMeters: Float,
        offsetMeters: Vec3 = Vec3.ZERO,
    ): FloatArray {
        val scale = scaleFor(metrics, sizeMeters)
        val anchor = metrics.anchorPosition(scale = scale, elevationMeters = elevationMeters)

        // O deslocamento do arrasto entra **na ancoragem**: ele é um movimento no plano da
        // figura (X e Y do marcador), somado ao que a ancoragem já faz para centralizar e
        // apoiar o modelo. Fica junto da ancoragem — e não dentro da rotação — porque o
        // usuário arrasta o modelo NO PLANO da figura, e não em torno do próprio eixo.
        val placement = Vec3(
            x = anchor.x + offsetMeters.x,
            y = anchor.y + offsetMeters.y,
            z = anchor.z + offsetMeters.z,
        )

        // Local: translação (ancoragem + arrasto) · rotação do usuário · escala.
        //
        // Não há rotação de apoio — e essa é a correção da decisão 34: o "para cima" do arquivo
        // (o +Y do glTF) **é** a normal do marcador no referencial do ARCore, que é o que o
        // `solvePnP` produz (veja `MarkerDetector.markerObjectPointsMat`, em que o Y é **zero**
        // nas quatro quinas). O modelo já carrega de pé sobre a figura, o zero dos sliders é
        // "em pé" e girar a folha impressa gira o modelo em torno de si mesmo, sem deitá-lo.
        //
        // A versão anterior girava 90° em X aqui, supondo o plano do marcador em XY: o "para
        // cima" do modelo caía DENTRO do plano (no eixo Z, altura na imagem) e, ao girar a folha
        // pela normal, o modelo girava em torno do eixo errado — o defeito relatado em campo.
        val local = multiply(
            translation(placement),
            multiply(eulerRotation(rotationDegrees), uniformScale(scale)),
        )

        return multiply(markerPoseToMatrix(markerPose), local)
    }
    // O apoio de 90° saiu na decisão 34: o "para cima" do arquivo (+Y) **é** a normal do
    // marcador, então o modelo carrega de pé e não há rotação a aplicar (veja o comentário
    // do `local` acima).

    /**
     * Rotação a partir dos ângulos do painel.
     *
     * A composição é `Rz · Ry · Rx` (aplicar X, depois Y, depois Z). Para uma rotação de
     * um eixo só — o caso comum, que existe para "deitar" ou "levantar" um modelo
     * exportado com outro eixo para cima — qualquer ordem dá o mesmo resultado; em
     * combinações de dois ou três eixos a diferença é de orientação intermediária, e a
     * convenção é esta nos dois backends.
     */
    fun eulerRotation(rotationDegrees: Vec3): FloatArray {
        val rx = Math.toRadians(rotationDegrees.x.toDouble())
        val ry = Math.toRadians(rotationDegrees.y.toDouble())
        val rz = Math.toRadians(rotationDegrees.z.toDouble())

        val cx = cos(rx).toFloat()
        val sx = sin(rx).toFloat()
        val cy = cos(ry).toFloat()
        val sy = sin(ry).toFloat()
        val cz = cos(rz).toFloat()
        val sz = sin(rz).toFloat()

        // Rz · Ry · Rx, em ordem de coluna.
        return floatArrayOf(
            cz * cy, sz * cy, -sy, 0f,
            cz * sy * sx - sz * cx, sz * sy * sx + cz * cx, cy * sx, 0f,
            cz * sy * cx + sz * sx, sz * sy * cx - cz * sx, cy * cx, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    /** Matriz de translação. */
    fun translation(offset: Vec3): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        offset.x, offset.y, offset.z, 1f,
    )

    /** Matriz de escala uniforme. */
    fun uniformScale(scale: Float): FloatArray = floatArrayOf(
        scale, 0f, 0f, 0f,
        0f, scale, 0f, 0f,
        0f, 0f, scale, 0f,
        0f, 0f, 0f, 1f,
    )

    /**
     * Matriz de escala por eixo — usada pelo plano de fundo, que é largo e baixo
     * (a escala uniforme de [uniformScale] não serviria).
     */
    fun scale(scales: Vec3): FloatArray = floatArrayOf(
        scales.x, 0f, 0f, 0f,
        0f, scales.y, 0f, 0f,
        0f, 0f, scales.z, 0f,
        0f, 0f, 0f, 1f,
    )

    /** Converte a pose do marcador (rotação 3×3 + translação) numa matriz 4×4. */
    fun markerPoseToMatrix(pose: Pose): FloatArray = floatArrayOf(
        pose[0, 0].toFloat(), pose[1, 0].toFloat(), pose[2, 0].toFloat(), 0f,
        pose[0, 1].toFloat(), pose[1, 1].toFloat(), pose[2, 1].toFloat(), 0f,
        pose[0, 2].toFloat(), pose[1, 2].toFloat(), pose[2, 2].toFloat(), 0f,
        pose.tx, pose.ty, pose.tz, 1f,
    )

    /** Produto `a · b` de duas matrizes 4×4 em ordem de coluna. */
    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(16)
        for (column in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (index in 0 until 4) {
                    sum += a[index * 4 + row] * b[column * 4 + index]
                }
                result[column * 4 + row] = sum
            }
        }
        return result
    }

    /**
     * Aplica a matriz a um ponto (componente homogênea 1) — usado pelos testes e pelos
     * diagnósticos do renderizador.
     */
    fun apply(matrix: FloatArray, point: Vec3): Vec3 {
        val x = matrix[0] * point.x + matrix[4] * point.y + matrix[8] * point.z + matrix[12]
        val y = matrix[1] * point.x + matrix[5] * point.y + matrix[9] * point.z + matrix[13]
        val z = matrix[2] * point.x + matrix[6] * point.y + matrix[10] * point.z + matrix[14]
        val w = matrix[3] * point.x + matrix[7] * point.y + matrix[11] * point.z + matrix[15]
        return if (w == 0f || w == 1f) Vec3(x, y, z) else Vec3(x / w, y / w, z / w)
    }
}
