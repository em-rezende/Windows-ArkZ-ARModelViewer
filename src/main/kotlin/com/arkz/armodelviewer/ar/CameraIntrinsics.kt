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

import org.bytedeco.javacpp.indexer.DoubleIndexer
import org.bytedeco.opencv.global.opencv_core.CV_64F
import org.bytedeco.opencv.opencv_core.Mat
import kotlin.math.tan

/**
 * Parâmetros intrínsecos da webcam (a "matriz da câmera" do OpenCV).
 *
 * O ARCore descobria isso sozinho no Android; no desktop o app precisa saber, porque
 * é com esses números que o `solvePnP` transforma os quatro cantos do marcador na
 * **pose 3D** (posição e orientação) usada para ancorar o modelo.
 *
 * O padrão é estimar a distância focal pelo **campo de visão horizontal** — 60° é um
 * valor típico de webcam de notebook — e assumir pixels quadrados (`fx = fy`). A
 * estimativa erra a distância do modelo em alguns por cento (o que a barra de status
 * e o painel de ajustes absorvem: o usuário ajusta o tamanho e vê o resultado), e
 * pode ser substituída por uma calibração real com o tabuleiro de xadrez
 * (`tools/calibrate-camera.ps1`), cujo resultado é gravado nas preferências.
 *
 * @property width/height resolução do quadro para o qual os intrínsecos valem.
 * @property focalLengthX/focalLengthY distância focal em pixels.
 * @property centerX/centerY ponto principal (em tese, o centro da imagem).
 */
data class CameraIntrinsics(
    val width: Int,
    val height: Int,
    val focalLengthX: Double,
    val focalLengthY: Double,
    val centerX: Double,
    val centerY: Double,
) {

    /** Campo de visão horizontal (em graus) implícito nestes intrínsecos. */
    val horizontalFieldOfViewDegrees: Double
        get() = 2.0 * Math.toDegrees(kotlin.math.atan(width / (2.0 * focalLengthX)))

    /**
     * Matriz 3×3 da câmera, no formato do OpenCV (`CV_64F`).
     *
     * A matriz é criada com memória **própria** do OpenCV e preenchida por um
     * indexador: passar um `Pointer` de Java para o `Mat` deixaria a matriz
     * apontando para memória que o coletor de lixo pode reciclar.
     */
    fun cameraMatrix(): Mat = matOf(
        3,
        3,
        doubleArrayOf(
            focalLengthX, 0.0, centerX,
            0.0, focalLengthY, centerY,
            0.0, 0.0, 1.0,
        ),
    )

    /**
     * Coeficientes de distorção (5 valores).
     *
     * Assumimos **sem distorção**: as webcams de notebook já entregam a imagem
     * corrigida pelo driver (a distorção que sobra é de poucos pixels), e um modelo
     * de distorção errado é pior do que nenhum. Quando a calibração real for feita,
     * estes valores passam a vir do arquivo de calibração.
     */
    fun distortionCoefficients(): Mat = matOf(5, 1, DoubleArray(5))

    /** Mesmos intrínsecos para outro tamanho de quadro (a janela pode reabrir menor). */
    fun scaled(factor: Double): CameraIntrinsics = CameraIntrinsics(
        width = (width * factor).toInt(),
        height = (height * factor).toInt(),
        focalLengthX = focalLengthX * factor,
        focalLengthY = focalLengthY * factor,
        centerX = centerX * factor,
        centerY = centerY * factor,
    )

    companion object {

        /** Campo de visão horizontal assumido quando não há calibração. */
        const val DEFAULT_HORIZONTAL_FOV_DEGREES = 60.0

        /** Estima os intrínsecos a partir da resolução e do campo de visão. */
        fun forFrame(
            width: Int,
            height: Int,
            horizontalFieldOfViewDegrees: Double = DEFAULT_HORIZONTAL_FOV_DEGREES,
        ): CameraIntrinsics {
            require(width > 0 && height > 0) { "Resolução inválida: ${width}×$height" }
            require(horizontalFieldOfViewDegrees > 1.0 && horizontalFieldOfViewDegrees < 179.0) {
                "Campo de visão fora do razoável: $horizontalFieldOfViewDegrees°"
            }

            val focal = width / (2.0 * tan(Math.toRadians(horizontalFieldOfViewDegrees / 2.0)))
            return CameraIntrinsics(
                width = width,
                height = height,
                focalLengthX = focal,
                focalLengthY = focal,
                centerX = width / 2.0,
                centerY = height / 2.0,
            )
        }

        private fun matOf(rows: Int, cols: Int, values: DoubleArray): Mat {
            val mat = Mat(rows, cols, CV_64F)
            val indexer = mat.createIndexer<DoubleIndexer>()
            try {
                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        indexer.put(row.toLong(), col.toLong(), values[row * cols + col])
                    }
                }
            } finally {
                indexer.release()
            }
            return mat
        }
    }
}
