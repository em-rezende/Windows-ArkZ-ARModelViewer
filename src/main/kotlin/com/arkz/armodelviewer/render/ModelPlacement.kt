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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Onde o modelo vai parar na cena: a transformação que leva a **geometria crua do
 * arquivo** até o **referencial do mundo** (o da câmera).
 *
 * ## A conta, e por que ela é assim
 *
 * ```
 * mundo = pose do marcador  ∘  [ translação de ancoragem · ORIENTAÇÃO DO ARQUIVO · rotação do usuário · escala ]
 * ```
 *
 * * a **pose do marcador** (`DetectedMarker.centerPose`) leva um ponto do referencial do
 *   marcador para o mundo — é a metade que vem da detecção;
 * * a **orientação do arquivo** ([MODEL_ORIENTATION]) é a correspondência de eixos fixa entre o
 *   referencial do arquivo (glTF: **+Y** para cima, **+Z** para a frente) e o do marcador (o do
 *   ARCore: X = largura, **Y = a normal**, Z = altura NA imagem). É ela que põe o modelo **de pé**
 *   com a face olhando para quem vê — a correção da **decisão 38**;
 * * o resto é o que o app Android deixava a cargo do nó do modelo: a **escala** normalizada
 *   (maior dimensão = tamanho escolhido), a **rotação dos sliders** (em graus, nos eixos do
 *   **arquivo** — os mesmos que o marcador adota depois da orientação) e a **translação de
 *   ancoragem** do `ModelMetrics.anchorPosition` — calculada sobre a caixa **já orientada**, e
 *   que centraliza o modelo na figura e apoia a face de trás dele sobre o plano.
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
     * Orientação com que a geometria do **arquivo** entra no referencial do **marcador** — a
     * correspondência de eixos fixa do aplicativo (a decisão 38 do roadmap).
     *
     * ## Os dois referenciais
     *
     *  * **arquivo** — o do glTF, que é o que o Blender, o SketchUp, o SimLab e a conversão do
     *    Assimp produzem: **+X** para a direita, **+Y** para cima e **+Z** para a frente (o eixo
     *    em que o modelo "olha" — no `House.glb` do repositório, a **face da porta vermelha**);
     *  * **marcador** — o do ARCore para imagens, e o que o `solvePnP` produz: **X** = largura
     *    (para a direita), **Y** = a **NORMAL** (sai do plano da figura, na direção de quem olha)
     *    e **Z** = a altura NA imagem (para baixo). O **plano** da figura é o **XZ** — é por isso
     *    que o eixo do "para dentro/para fora" do plano é o **Y** (veja `ModelMetrics`).
     *
     * ## A correspondência (uma rotação de −90° em X)
     *
     * ```
     * X do arquivo →  X do marcador   (a largura da figura)
     * Y do arquivo → −Z do marcador   (a altura NA imagem: o "para cima" do arquivo é a imagem para cima)
     * Z do arquivo →  Y do marcador   (a NORMAL: o "frente" do arquivo olha para quem está vendo)
     * ```
     *
     * Com ela o **plano XY do arquivo** (a face do modelo) fica **paralelo ao plano da figura**, e
     * o +Z do arquivo (o nariz, a porta) cai na **normal**: o modelo é colado na figura como um
     * **relevo**, de pé e olhando para quem vê. E, como o giro da folha impressa em torno da
     * própria normal é um giro em torno do **+Y do marcador** — que é o **+Z do arquivo** —, o
     * modelo acompanha o giro **no seu eixo correspondente**, que é o pedido de campo.
     *
     * ## Por que ela existe (o defeito que ela corrige)
     *
     * Sem ela a correspondência era a **identidade**, e o "para cima" do arquivo (+Y) caía na
     * **normal** do marcador. Numa folha **de frente para a webcam** — a situação do aplicativo
     * de desktop, com o marcador na mão ou apoiado à frente do monitor — a normal é **horizontal**:
     * o modelo carregava **deitado de costas** (a face para o teto) e o giro da folha o rodava em
     * torno do próprio **Y**, em vez do eixo correspondente. Foi exatamente este o relato:
     * *"o modelo gira no Y quando giro o marcador pela normal; ele deveria girar no Z"*.
     *
     * A folha **apoiada na mesa** (normal vertical) era o único caso em que a identidade dava
     * "em pé" — e é o inverso do caso de campo. Como a correspondência tem de ser **fixa** (o
     * modelo é colado à figura, e não orientado pela vertical do mundo), ela vale para os dois
     * casos: com a folha na mesa, o modelo aparece deitado com a face para cima, girando no plano
     * dela. Um modo "vertical do mundo" — que manteria o modelo de pé com a folha em qualquer
     * inclinação — fica registrado como **possibilidade** na decisão 38 (como já estava na 37), e
     * não como pendência: hoje o modelo se comporta como um objeto **colado na folha**.
     *
     * > **Por que não trocar o referencial do marcador?** Porque ele é o do ARCore, é **medido** e
     * > está guardado por teste (`MarkerDetectorTest`), além de ser o mesmo do app Android: mexer
     * > nele mudaria a pose que a detecção entrega. A troca aqui é de **orientação do arquivo**,
     * > num lugar só.
     */
    val MODEL_ORIENTATION: FloatArray = eulerRotation(Vec3(-90f, 0f, 0f))

    /**
     * A caixa envolvente do arquivo **depois** de orientada por [orientation] — a caixa que a
     * ancoragem precisa ver.
     *
     * O **centro** é um ponto e gira com a orientação; a **meia-extensão** vira a soma dos módulos
     * das colunas, o que é **exato** para uma rotação de 90° (cada eixo do arquivo cai inteiro
     * sobre um eixo do marcador). A conta é escrita na forma geral — e não como uma troca de
     * eixos — para que ela continue valendo se a orientação mudar (um arquivo com outro eixo para
     * cima, por exemplo).
     *
     * Sem esta passagem, `anchorPosition` apoiaria a face errada no plano: ele trabalha no
     * referencial do **marcador** e receberia as medidas do **arquivo** (o Y dele é a altura do
     * modelo, e não a normal).
     */
    fun orientedBounds(metrics: ModelMetrics, orientation: FloatArray): ModelMetrics {
        val half = metrics.halfExtent

        /** Extensão da caixa num eixo do marcador: os módulos da coluna do arquivo. */
        fun span(row: Int): Float =
            abs(orientation[row]) * half.x +
                abs(orientation[4 + row]) * half.y +
                abs(orientation[8 + row]) * half.z

        return ModelMetrics(
            center = apply(orientation, metrics.center),
            halfExtent = Vec3(x = span(0), y = span(1), z = span(2)),
        )
    }

    /**
     * Matriz do modelo no referencial do mundo.
     *
     * @param markerPose pose do marcador reconhecido.
     * @param metrics bounding box do modelo, em unidades do arquivo.
     * @param sizeMeters tamanho escolhido para a maior dimensão (0,02 a 2 m).
     * @param rotationDegrees rotação do painel de ajustes, em graus, nos eixos do **arquivo**
     *   (X = largura, Y = "para cima", Z = o "frente") — os mesmos que [MODEL_ORIENTATION] leva
     *   ao referencial do marcador.
     * @param elevationMeters deslocamento no eixo da **normal do marcador** (o
     *   "Elevação Z" da interface; −0,5 a +0,5 m).
     * @param offsetMeters deslocamento do arrasto, em metros, nos eixos do marcador (veja
     *   `InteractiveInput.panOffset`): **X** = largura da figura e **Y** = a normal (o
     *   frente–trás). Entra somado à ancoragem e **não** gira com os cursores de rotação.
     */
    fun worldMatrix(
        markerPose: Pose,
        metrics: ModelMetrics,
        sizeMeters: Float,
        rotationDegrees: Vec3,
        elevationMeters: Float,
        offsetMeters: Vec3 = Vec3(0f, 0f, 0f),
    ): FloatArray {
        val scale = scaleFor(metrics, sizeMeters)

        // A ancoragem é calculada sobre a caixa do arquivo **já orientada** ([orientedBounds]): ela
        // trabalha no referencial do marcador e é a orientação que decide qual face do modelo apoia
        // no plano da figura (com o −90° em X, é o **fundo** do arquivo — o −Z dele).
        val placed = orientedBounds(metrics, MODEL_ORIENTATION)
        val anchor = placed.anchorPosition(scale = scale, elevationMeters = elevationMeters)

        // O deslocamento do arrasto entra **na ancoragem**: é uma translação nos eixos do
        // marcador — a largura (X) e o frente–trás (Y, a normal; veja `InteractiveInput`) —
        // somada ao que a ancoragem já faz para centralizar e apoiar o modelo. Fica junto da
        // ancoragem, e não dentro da rotação, porque o usuário move o modelo **sobre a figura**,
        // e não em torno do próprio eixo: girar o modelo nos cursores não muda o sentido em que
        // ele anda no arrasto.
        val placement = Vec3(
            x = anchor.x + offsetMeters.x,
            y = anchor.y + offsetMeters.y,
            z = anchor.z + offsetMeters.z,
        )

        // Local: translação (ancoragem + arrasto) · ORIENTAÇÃO DO ARQUIVO · rotação do usuário ·
        // escala.
        //
        // A ordem é o que dá sentido ao painel: os cursores giram o modelo nos eixos do PRÓPRIO
        // arquivo (X = largura, Y = "para cima", Z = o "frente") — os eixos que [MODEL_ORIENTATION]
        // leva ao referencial do marcador —, então "Rotação Z" gira em torno do eixo que sai da
        // folha (a normal), que é o que o usuário vê.
        //
        // A **orientação do arquivo** é a peça que faltava aqui (decisão 38): sem ela a
        // correspondência era a identidade e o "para cima" do arquivo (+Y) caía na **normal** do
        // marcador. Numa folha de frente para a webcam — a situação do desktop — a normal é
        // horizontal: o modelo carregava **deitado de costas** e o giro da folha o rodava no eixo
        // errado (o próprio Y), em vez do eixo correspondente. Com ela, o plano XY do arquivo (a
        // face do modelo) é o plano da figura e o +Z do arquivo (no `House.glb` de referência, a
        // face da frente) cai na normal: o modelo aparece **de pé, olhando para quem está vendo**, e
        // acompanha o giro da folha eixo por eixo.
        val local = multiply(
            translation(placement),
            multiply(MODEL_ORIENTATION, multiply(eulerRotation(rotationDegrees), uniformScale(scale))),
        )

        return multiply(markerPoseToMatrix(markerPose), local)
    }

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
