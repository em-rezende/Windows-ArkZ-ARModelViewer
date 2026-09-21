/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.ar

import com.arkz.armodelviewer.model.Vec3
import org.bytedeco.javacpp.indexer.DoubleIndexer
import org.bytedeco.opencv.global.opencv_calib3d.Rodrigues
import org.bytedeco.opencv.opencv_core.Mat
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pose de um marcador em relação à câmera — o equivalente desktop do
 * `AugmentedImage.getCenterPose()` do ARCore.
 *
 * Leva um ponto do **referencial do marcador** para o **referencial do mundo**
 * (que, sem rastreio de movimento, é o da câmera). O referencial do marcador é o
 * mesmo que o ARCore usa para imagens, mantido de propósito para que a matemática de
 * ancoragem do app Android (`ModelMetrics.anchorPosition`) valha sem alteração:
 *
 *  * **+X** = largura da imagem (para a direita);
 *  * **+Y** = normal da imagem (saindo do plano do marcador, na direção da câmera);
 *  * **+Z** = altura NA imagem (para baixo).
 *
 * A conversão a partir do resultado do `solvePnP` é só uma troca de eixos (veja
 * [fromRodriguesAndTranslation]).
 *
 * @property rotation matriz de rotação 3×3 em ordem de linha.
 * @property translation translação em metros, no referencial do mundo.
 */
class Pose(
    private val rotation: DoubleArray,
    val translation: Vec3,
) {

    init {
        require(rotation.size == 9) { "A rotação precisa ser 3×3." }
    }

    /** Elemento da rotação (0..2, 0..2). */
    operator fun get(row: Int, col: Int): Double = rotation[row * 3 + col]

    val tx: Float get() = translation.x
    val ty: Float get() = translation.y
    val tz: Float get() = translation.z

    /** Distância do marcador à câmera, em metros. */
    val distanceMeters: Float
        get() = sqrt(
            translation.x * translation.x +
                translation.y * translation.y +
                translation.z * translation.z,
        )

    /** Aplica a pose a um ponto do referencial do marcador. */
    fun transform(point: Vec3): Vec3 = Vec3(
        x = (rotation[0] * point.x + rotation[1] * point.y + rotation[2] * point.z).toFloat() + translation.x,
        y = (rotation[3] * point.x + rotation[4] * point.y + rotation[5] * point.z).toFloat() + translation.y,
        z = (rotation[6] * point.x + rotation[7] * point.y + rotation[8] * point.z).toFloat() + translation.z,
    )

    /** `true` quando a rotação é ortonormal (verificação usada pelos testes). */
    fun isRotationOrthonormal(tolerance: Double = 1e-6): Boolean {
        fun dot(rowA: Int, rowB: Int): Double {
            var sum = 0.0
            for (col in 0 until 3) sum += this[rowA, col] * this[rowB, col]
            return sum
        }
        return abs(dot(0, 0) - 1.0) < tolerance &&
            abs(dot(1, 1) - 1.0) < tolerance &&
            abs(dot(2, 2) - 1.0) < tolerance &&
            abs(dot(0, 1)) < tolerance &&
            abs(dot(0, 2)) < tolerance &&
            abs(dot(1, 2)) < tolerance
    }

    override fun toString(): String =
        "Pose(t=(%.4f, %.4f, %.4f) m, distância=%.3f m)".format(tx, ty, tz, distanceMeters)

    companion object {

        /**
         * Converte o resultado do `solvePnP` (rotação em Rodrigues + translação) numa
         * [Pose] no referencial do app.
         *
         * O `solvePnP` trabalha no referencial de câmera do OpenCV: X para a
         * direita, **Y para baixo** e Z para dentro da cena. O app (como o ARCore)
         * usa **Y para cima** e Z saindo da tela — a diferença é uma reflexão nos
         * eixos Y e Z, aplicada aqui de uma vez só. Sem ela o modelo apareceria
         * **de cabeça para baixo** e atrás da câmera.
         *
         * @param rotationVector `rvec` devolvido pelo `solvePnP` (3×1 `CV_64F`).
         * @param translationVector `tvec` devolvido pelo `solvePnP` (3×1 `CV_64F`).
         */
        fun fromRodriguesAndTranslation(
            rotationVector: Mat,
            translationVector: Mat,
            flipCameraAxes: Boolean = true,
        ): Pose {
            val rotationMatrix = Mat()
            try {
                Rodrigues(rotationVector, rotationMatrix)

                val rotation = DoubleArray(9)
                val rotationIndexer = rotationMatrix.createIndexer<DoubleIndexer>()
                try {
                    for (row in 0 until 3) {
                        for (col in 0 until 3) {
                            rotation[row * 3 + col] = rotationIndexer.get(row.toLong(), col.toLong())
                        }
                    }
                } finally {
                    rotationIndexer.release()
                }

                val translation = translationVector.createIndexer<DoubleIndexer>()
                val tx: Double
                val ty: Double
                val tz: Double
                try {
                    tx = translation.get(0L, 0L)
                    ty = translation.get(1L, 0L)
                    tz = translation.get(2L, 0L)
                } finally {
                    translation.release()
                }

                if (flipCameraAxes) {
                    // Y e Z trocam de sinal: linhas 1 e 2 da rotação, e ty/tz.
                    for (col in 0 until 3) {
                        rotation[3 + col] = -rotation[3 + col]
                        rotation[6 + col] = -rotation[6 + col]
                    }
                    return Pose(
                        rotation = rotation,
                        translation = Vec3(tx.toFloat(), (-ty).toFloat(), (-tz).toFloat()),
                    )
                }

                return Pose(
                    rotation = rotation,
                    translation = Vec3(tx.toFloat(), ty.toFloat(), tz.toFloat()),
                )
            } finally {
                rotationMatrix.release()
            }
        }
    }
}
