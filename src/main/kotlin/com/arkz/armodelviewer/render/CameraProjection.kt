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

/**
 * A projeção da câmera de RA — a metade "olho" da conta, irmã do `ModelPlacement`.
 *
 * ## Por que ela é derivada das intrínsecas, e não de um campo de visão
 *
 * A pose do marcador foi obtida pelo `solvePnP` usando o **modelo pinhole** das
 * [CameraIntrinsics]: `u = fx · X/Z + cx`, `v = fy · Y/Z + cy`. Se o renderizador
 * projetasse com um campo de visão aproximado (o caminho comum: "45° de abertura"), o
 * modelo ancorado ficaria **deslocado** em relação ao marcador — pouco no centro da
 * imagem, muito nas bordas —, e o erro cresceria quanto mais perto do limite do quadro o
 * marcador estivesse. Derivando a matriz dos **mesmos** números que a detecção usa, o
 * modelo cai exatamente sobre o marcador em qualquer ponto da imagem.
 *
 * É aqui também que a diferença entre os tamanhos do quadro é resolvida: as intrínsecas
 * valem para a resolução da câmera (por exemplo 1280×720) e a matriz é montada para o
 * tamanho do painel (que muda quando o usuário redimensiona a janela) — as distâncias
 * focais e o ponto principal são escalados na mesma proporção.
 *
 * ## Convenção, e a origem dela
 *
 * Referencial de câmera do ARCore/OpenGL: **+X** para a direita, **+Y** para cima e a
 * câmera olhando para **−Z** (um ponto à frente tem z negativo). É o referencial que sai
 * do `solvePnP` depois da troca de sinal dos eixos Y e Z (o `Pose` do app já faz isso),
 * então posição de marcador e projeção falam a mesma língua.
 *
 * ## Ordem dos números
 *
 * `DoubleArray(16)` em ordem de **coluna** (a mesma do `mat4` do Filament e do
 * `glUniformMatrix4fv` com `transpose = false`), o que permite passar direto para
 * `Camera.setCustomProjection`.
 */
object CameraProjection {

    /** Plano próximo, em metros: 5 cm — o suficiente para um marcador colado na lente. */
    const val NEAR_METERS = 0.05

    /** Plano distante, em metros: 100 m — bem além das distâncias de uso (30–60 cm). */
    const val FAR_METERS = 100.0

    /**
     * Matriz de projeção para um quadro de [width]×[height] pixels.
     *
     * As intrínsecas podem ter vindo de outra resolução: elas são **escaladas** para o
     * tamanho pedido (a razão de aspecto vem do quadro, não das intrínsecas).
     */
    fun fromIntrinsics(
        intrinsics: CameraIntrinsics,
        width: Int,
        height: Int,
        nearMeters: Double = NEAR_METERS,
        farMeters: Double = FAR_METERS,
    ): DoubleArray {
        require(width > 0 && height > 0) { "quadro inválido: ${width}×$height" }
        require(nearMeters > 0.0 && farMeters > nearMeters) {
            "planos inválidos: perto=${nearMeters}, longe=${farMeters}"
        }

        val scaled = if (intrinsics.width == width && intrinsics.height == height) {
            intrinsics
        } else {
            intrinsics.scaled(width.toDouble() / intrinsics.width)
        }

        val fx = scaled.focalLengthX
        val fy = scaled.focalLengthY
        val cx = scaled.centerX
        val cy = scaled.centerY

        val matrix = DoubleArray(16)
        // Coluna 0: quanto x contribui para x_clip.
        matrix[0] = 2.0 * fx / width
        // Coluna 1: quanto y contribui para y_clip.
        matrix[5] = 2.0 * fy / height
        // Coluna 2: o ponto principal (em pixels) desloca a imagem; e os planos.
        matrix[8] = 1.0 - 2.0 * cx / width
        matrix[9] = 2.0 * cy / height - 1.0
        matrix[10] = (farMeters + nearMeters) / (nearMeters - farMeters)
        matrix[11] = -1.0
        matrix[14] = 2.0 * farMeters * nearMeters / (nearMeters - farMeters)
        return matrix
    }

    /**
     * Projeta um ponto do referencial da câmera em **pixels** do quadro.
     *
     * Serve aos testes e aos diagnósticos: é a conta que prova que a matriz e o modelo
     * pinhole da detecção concordam. Devolve `null` para pontos atrás da câmera.
     */
    fun projectToPixels(
        matrix: DoubleArray,
        x: Double,
        y: Double,
        z: Double,
        width: Int,
        height: Int,
    ): Pair<Double, Double>? {
        val clipX = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12]
        val clipY = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13]
        val clipW = matrix[3] * x + matrix[7] * y + matrix[11] * z + matrix[15]
        if (clipW <= 0.0) return null

        val ndcX = clipX / clipW
        val ndcY = clipY / clipW
        return Pair((ndcX + 1.0) / 2.0 * width, (1.0 - ndcY) / 2.0 * height)
    }

    /**
     * Tamanho (largura, altura) em metros do plano que cobre o quadro inteiro a
     * [distanceMeters] da câmera — a geometria do plano de fundo com o vídeo da webcam.
     *
     * Como o plano é construído no referencial da câmera (e não num mundo qualquer), a
     * conta usa o campo de visão **implícito nas intrínsecas**, o que mantém o vídeo e a
     * cena projetados pela mesma câmera.
     */
    fun frameSizeAt(
        intrinsics: CameraIntrinsics,
        width: Int,
        height: Int,
        distanceMeters: Double,
    ): Pair<Double, Double> {
        require(distanceMeters > 0.0) { "distância inválida: $distanceMeters" }
        val scaled = if (intrinsics.width == width && intrinsics.height == height) {
            intrinsics
        } else {
            intrinsics.scaled(width.toDouble() / intrinsics.width)
        }

        val halfHeight = distanceMeters * (height / 2.0) / scaled.focalLengthY
        val halfWidth = distanceMeters * (width / 2.0) / scaled.focalLengthX
        return Pair(2.0 * halfWidth, 2.0 * halfHeight)
    }
}
