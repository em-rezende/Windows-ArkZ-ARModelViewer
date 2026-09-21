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

import com.arkz.armodelviewer.camera.toGrayBytes
import com.arkz.armodelviewer.markers.MarkerDefinition
import com.arkz.armodelviewer.util.AppLog
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.opencv.global.opencv_calib3d.RANSAC
import org.bytedeco.opencv.global.opencv_calib3d.SOLVEPNP_IPPE
import org.bytedeco.opencv.global.opencv_calib3d.findHomography
import org.bytedeco.opencv.global.opencv_calib3d.solvePnP
import org.bytedeco.opencv.global.opencv_core.CV_32FC2
import org.bytedeco.opencv.global.opencv_core.CV_32FC3
import org.bytedeco.opencv.global.opencv_core.CV_8UC1
import org.bytedeco.opencv.global.opencv_core.NORM_HAMMING
import org.bytedeco.opencv.global.opencv_core.perspectiveTransform
import org.bytedeco.opencv.global.opencv_imgproc.INTER_AREA
import org.bytedeco.opencv.global.opencv_imgproc.resize
import org.bytedeco.opencv.opencv_core.DMatchVectorVector
import org.bytedeco.opencv.opencv_core.KeyPoint
import org.bytedeco.opencv.opencv_core.KeyPointVector
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Point2fVector
import org.bytedeco.opencv.opencv_core.Size
import org.bytedeco.opencv.opencv_features2d.BFMatcher
import org.bytedeco.opencv.opencv_features2d.ORB
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

/**
 * Detecção de marcadores de imagem por **pontos de interesse** — o que o ARCore
 * fazia com *Augmented Images* no Android.
 *
 * ## Por que casamento de pontos de interesse, e não ArUco
 *
 * O app Android usa os marcadores que ele mesmo desenha (pseudo-QR com três finder
 * patterns) e imagens escolhidas pelo usuário. Nenhum dicionário ArUco reconhece
 * esses padrões: o ARCore reconhece **pela aparência da figura**, casando pontos de
 * interesse e ajustando uma homografia. Reimplementar assim é o que faz os **mesmos
 * marcadores já impressos continuarem valendo** nesta versão.
 *
 * ## O caminho, quadro a quadro
 *
 *  1. o quadro vira uma imagem em tons de cinza (`CV_8UC1`);
 *  2. o ORB extrai pontos de interesse e descritores do quadro;
 *  3. cada marcador tem os descritores pré-calculados (uma vez, ao abrir);
 *  4. `knnMatch` (k = 2) + teste de razão de Lowe escolhe os pares confiáveis;
 *  5. `findHomography` com RANSAC dá os **quatro cantos** do marcador no quadro;
 *  6. `solvePnP` transforma esses cantos na **pose 3D** que ancora o modelo;
 *  7. o estado de rastreio e a suavização temporal evitam oscilação a cada quadro.
 *
 * ## Custo e onde ele roda
 *
 * O ORB num quadro de 1280×720 custa algumas dezenas de milissegundos: **nunca**
 * chame [detect] na thread de interface. Os arranjos de trabalho são reaproveitados
 * entre chamadas e nada é sincronizado — use o detector numa **única** thread.
 *
 * @param markers marcadores cadastrados (embutidos + personalizados).
 * @param intrinsics intrínsecos da câmera para a resolução dos quadros.
 * @param minMatchCount mínimo de pontos casados para aceitar uma detecção.
 * @param ratioTest limiar do teste de razão de Lowe (0,75 é o clássico para ORB).
 */
class MarkerDetector(
    markers: List<MarkerDefinition>,
    private val intrinsics: CameraIntrinsics,
    private val minMatchCount: Int = 12,
    private val ratioTest: Float = 0.75f,
    private val ransacReprojectionThreshold: Double = 3.0,
) : AutoCloseable {

    /**
     * ORB com parâmetros ajustados para **marcadores impressos**.
     *
     * O ORB é o detector/descritor padrão para este caso: binário, rápido e
     * invariante a rotação. Os 1500 pontos e o `fastThreshold` baixo (20) existem
     * para reconhecer o marcador mesmo a 1–2 m da webcam, quando a figura tem
     * poucos pixels; o preço é um casamento um pouco mais lento — irrelevante para
     * poucos marcadores.
     */
    private val orb = ORB.create(
        FEATURE_COUNT,
        SCALE_FACTOR,
        LEVELS,
        EDGE_THRESHOLD,
        FIRST_LEVEL,
        WTA_K,
        ORB.HARRIS_SCORE,
        PATCH_SIZE,
        FAST_THRESHOLD,
    )

    /**
     * Casamento por força bruta com distância de Hamming.
     *
     * Para poucos marcadores (um a uma dúzia de descritores de referência) a busca
     * exaustiva é **mais previsível** que o FLANN: não exige ajustar número de
     * tabelas/chaves do LSH e devolve sempre o mesmo resultado, o que também torna
     * os testes determinísticos.
     */
    private val matcher = BFMatcher.create(NORM_HAMMING, false)

    /** Marcadores com descritores já calculados (um por marcador cadastrado). */
    private val references: List<ReferenceMarker> =
        markers.mapIndexed { index, marker -> ReferenceMarker(index, marker, orb) }

    /** Intrínsecos no formato do OpenCV (uma matriz só, para todos os quadros). */
    private val cameraMatrix: Mat = intrinsics.cameraMatrix()
    private val distortion: Mat = intrinsics.distortionCoefficients()

    /** Estado de rastreio por marcador (id → estado). */
    private val tracks = HashMap<String, MarkerTrack>()

    private var frameCounter = 0L

    private var grayBytes = ByteArray(0)
    private var grayPixels = IntArray(0)
    private var grayPointer: BytePointer? = null

    /**
     * Marca que [close] já rodou.
     *
     * Um `MarkerDetector` não pode ser usado depois de fechado: as matrizes nativas
     * dos descritores já foram devolvidas e o matcher do OpenCV receberia uma matriz
     * vazia — o que faz o OpenCV **abortar com uma asserção nativa** em vez de
     * devolver um erro tratável. Esta flag transforma isso num resultado vazio.
     */
    @Volatile
    private var closed = false

    /** Diagnóstico: as formas dos descritores só são registradas uma vez. */
    private var shapesLogged = false

    /** Quantos quadros de diagnóstico já foram gravados (limite para não encher o disco). */
    private var dumpedFrames = 0

    /** Quantos marcadores estão cadastrados. */
    val markerCount: Int get() = references.size

    /**
     * Executa a detecção num quadro.
     *
     * @return os marcadores **visíveis** neste quadro, incluindo os que estão em
     *   `PAUSED` com a última pose conhecida. `STOPPED` não é devolvido: quem saiu
     *   da cena não deve ser desenhado.
     */
    fun detect(frame: BufferedImage): List<DetectedMarker> {
        if (closed) {
            AppLog.warn("detect() chamado depois de close(): ignorado.")
            return emptyList()
        }

        frameCounter++
        val gray = prepareGray(frame)

        val result = runCatching {
            val keyPoints = KeyPointVector()
            val descriptors = Mat()
            try {
                orb.detectAndCompute(gray, Mat(), keyPoints, descriptors)

                if (descriptors.empty() || keyPoints.empty()) {
                    emptyList()
                } else {
                    val framePoints = Point2fVector()
                    try {
                        KeyPoint.convert(keyPoints, framePoints)
                        logDescriptorShapes(descriptors)
                        references.mapNotNull { reference ->
                            matchReferenceSafely(reference, descriptors, framePoints, frame)
                        }
                    } finally {
                        framePoints.close()
                    }
                }
            } finally {
                descriptors.release()
                keyPoints.clear()
            }
        }

        // Um quadro que falhou não deixa o rastreio "preso": a lista vazia faz os
        // marcadores entrarem em PAUSED, como se a câmera não os tivesse visto.
        val candidates = result.getOrElse { error ->
            // Uma falha nativa (ou de índice) não pode derrubar o aplicativo: a
            // detecção é um recurso sobre o vídeo, e o vídeo precisa continuar.
            AppLog.error("Falha na detecção: ${error.message}", error)
            dumpFrameForDiagnosis(frame, "falha")
            emptyList()
        }

        return updateTracking(candidates)
    }

    /** Registra (uma vez) as formas dos descritores — o que o matcher exige casar. */
    private fun logDescriptorShapes(frameDescriptors: Mat) {
        if (shapesLogged) return
        shapesLogged = true
        AppLog.info(
            "Descritores: quadro ${describe(frameDescriptors)}; referências " +
                references.joinToString(", ") { reference ->
                    "${reference.marker.id} ${describe(reference.descriptors)}"
                },
        )
    }

    /**
     * Descreve uma matriz de descritores de forma legível no log.
     *
     * O `knnMatch` do OpenCV exige que os dois lados tenham o **mesmo tipo** e o
     * **mesmo número de colunas** — é exatamente isso que o log precisa mostrar
     * quando o casamento é recusado.
     */
    private fun describe(descriptors: Mat): String = buildString {
        append(descriptors.rows()).append('×').append(descriptors.cols())
        append(" tipo ").append(descriptors.type())
        when {
            descriptors.empty() -> append(" (VAZIO)")
            descriptors.type() == TYPE_CV_8UC1 -> append(" (CV_8UC1)")
            descriptors.type() == TYPE_CV_32FC1 -> append(" (CV_32F)")
            else -> append(" (tipo inesperado)")
        }
    }

    /**
     * Chama [matchReference] protegendo o aplicativo de falhas nativas.
     *
     * O OpenCV trata violação de contrato com **asserção** (`CV_Assert`), que
     * interrompe o processo — não há exceção Java para capturar. Por isso há, antes da
     * chamada, a checagem explícita das pré-condições do casamento
     * (`knnMatch` exige o mesmo tipo e o mesmo número de colunas nos dois lados).
     */
    private fun matchReferenceSafely(
        reference: ReferenceMarker,
        frameDescriptors: Mat,
        framePoints: Point2fVector,
        frame: BufferedImage,
    ): MarkerCandidate? {
        if (!reference.hasFeatures || reference.descriptors.empty()) return null

        if (frameDescriptors.type() != reference.descriptors.type() ||
            frameDescriptors.cols() != reference.descriptors.cols()
        ) {
            AppLog.warn(
                "Descritores incompatíveis para '${reference.marker.id}': " +
                    "quadro tipo ${frameDescriptors.type()} com ${frameDescriptors.cols()} colunas × " +
                    "referência tipo ${reference.descriptors.type()} com " +
                    "${reference.descriptors.cols()} colunas — detecção ignorada.",
            )
            dumpFrameForDiagnosis(frame, "descritores-incompativeis")
            return null
        }

        return runCatching { matchReference(reference, frameDescriptors, framePoints) }
            .getOrElse { error ->
                AppLog.error(
                    "Falha ao casar o marcador '${reference.marker.id}': ${error.message}",
                    error,
                )
                dumpFrameForDiagnosis(frame, "erro-casamento")
                null
            }
    }

    /** Salva o quadro (e o cinza correspondente) para reproduzir o problema depois. */
    private fun dumpFrameForDiagnosis(frame: BufferedImage, reason: String) {
        if (dumpedFrames >= MAX_DIAGNOSTIC_DUMPS) return
        dumpedFrames++

        val count = frame.width * frame.height
        val bytes = ByteArray(count)
        frame.toGrayBytes(bytes, IntArray(count))
        AppLog.dumpGrayFrame(bytes, frame.width, frame.height, "quadro-$reason-$dumpedFrames.png")
    }

    /**
     * Esquece todo o rastreio (o botão **Reiniciar** do app Android "descarta as
     * detecções").
     */
    fun reset() {
        tracks.clear()
    }

    /** Libera os recursos nativos (uma vez, ao trocar de marcadores ou fechar). */
    override fun close() {
        if (closed) return
        closed = true

        AppLog.info("Detector encerrado (${references.size} marcador(es)).")
        references.forEach { it.close() }
        cameraMatrix.release()
        distortion.release()
        grayPointer = null
        tracks.clear()
    }

    /**
     * Converte o quadro para tons de cinza, reaproveitando os arranjos.
     *
     * O `BytePointer` fica guardado no detector de propósito: o `Mat` devolvido é
     * uma **visão** sobre a memória dele (`Mat(rows, cols, type, data)` não copia),
     * então o objeto Java precisa continuar vivo enquanto o `Mat` existir — deixá-lo
     * sair de escopo faria o coletor de lixo liberar memória que o OpenCV ainda usa.
     */
    private fun prepareGray(frame: BufferedImage): Mat {
        val count = frame.width * frame.height
        if (grayBytes.size < count) {
            grayBytes = ByteArray(count)
            grayPixels = IntArray(count)
            // `BytePointer(long)` ALOCA memória nativa: o `Mat` devolvido abaixo é uma
            // visão sobre ela (não há cópia) e é por isso que o ponteiro fica guardado
            // no detector — se ele for coletado, o OpenCV passa a ler memória liberada.
            grayPointer = BytePointer(count.toLong())
        }

        val pointer = grayPointer ?: BytePointer(count.toLong()).also { grayPointer = it }
        frame.toGrayBytes(grayBytes, grayPixels)
        // `BytePointer(byte...)` (e o `put` equivalente) COPIA o arranjo de Java para a
        // memória nativa: sem esta cópia o OpenCV leria o quadro ANTERIOR — um erro
        // que aparece como "a detecção só funciona no primeiro quadro".
        pointer.put(*grayBytes)
        return Mat(frame.height, frame.width, CV_8UC1, pointer)
    }

    /**
     * Procura um marcador no quadro: casa pontos, ajusta a homografia e devolve o
     * candidato (com cantos e pose) ou `null`.
     */
    /** Fecha a detecção de um marcador: cantos suavizados, pose e estado. */
    private fun matchReference(
        reference: ReferenceMarker,
        frameDescriptors: Mat,
        framePoints: Point2fVector,
    ): MarkerCandidate? {
        // Um marcador sem pontos de interesse (imagem lisa demais para o ORB) nunca
        // vai casar: sai antes de gastar tempo no matcher.
        if (!reference.hasFeatures) return null

        val matches = DMatchVectorVector()
        val referenceCoordinates = FloatArray(MAX_MATCH_BUFFER * 2)
        val frameCoordinates = FloatArray(MAX_MATCH_BUFFER * 2)
        var count = 0

        try {
            matcher.knnMatch(frameDescriptors, reference.descriptors, matches, 2)

            for (index in 0 until matches.size()) {
                val pair = matches.get(index.toLong())
                if (pair.size() < 2) continue

                val best = pair.get(0L)
                val second = pair.get(1L)
                // Teste de razão de Lowe: se o segundo vizinho é quase tão parecido
                // quanto o primeiro, o casamento é ambíguo — descarta.
                if (best.distance() > ratioTest * second.distance()) continue
                if (count >= MAX_MATCH_BUFFER) break

                val framePoint = framePoints.get(best.queryIdx().toLong())
                val referencePoint = reference.points.get(best.trainIdx().toLong())

                referenceCoordinates[count * 2] = referencePoint.x()
                referenceCoordinates[count * 2 + 1] = referencePoint.y()
                frameCoordinates[count * 2] = framePoint.x()
                frameCoordinates[count * 2 + 1] = framePoint.y()
                count++
            }
        } finally {
            matches.clear()
        }

        if (count < minMatchCount) return null

        val sourcePoints = pointsMat(referenceCoordinates, count)
        val destinationPoints = pointsMat(frameCoordinates, count)
        val projected = Mat()
        try {
            // A máscara é obrigatória na assinatura do JavaCPP (o C++ tem valor
            // padrão, o binding não): uma `Mat` vazia significa "não me interessa
            // quais pontos ficaram dentro" — o RANSAC só precisa da homografia.
            val homography = findHomography(
                sourcePoints,
                destinationPoints,
                Mat(),
                RANSAC,
                ransacReprojectionThreshold,
            )
            try {
                if (homography.empty()) return null

                perspectiveTransform(reference.corners, projected, homography)
                val corners = readCorners(projected) ?: return null
                if (!isAcceptableQuad(corners, intrinsics.width, intrinsics.height)) return null

                val pose = poseFor(reference, corners) ?: return null
                return MarkerCandidate(
                    reference = reference,
                    corners = corners,
                    pose = pose,
                    matchCount = count,
                )
            } finally {
                homography.release()
            }
        } finally {
            sourcePoints.release()
            destinationPoints.release()
            projected.release()
        }
    }

    /** Lê os quatro cantos de um `Mat` 4×1 de `CV_32FC2`, em ordem. */
    private fun readCorners(points: Mat): List<Vec2>? {
        if (points.empty() || points.rows() < 4) return null

        val indexer = points.createIndexer<FloatIndexer>()
        try {
            return (0 until 4).map { index ->
                Vec2(
                    x = indexer.get(index.toLong(), 0L, 0L),
                    y = indexer.get(index.toLong(), 0L, 1L),
                )
            }
        } finally {
            indexer.release()
        }
    }

    /**
     * O quadrilátero encontrado é plausível como um marcador visto pela câmera?
     *
     * Uma homografia **sempre** devolve alguma coisa, mesmo quando os pontos casados
     * são ruído: sem estas checagens o app desenharia caixas ciano gigantes, viradas
     * do avesso ou do tamanho da tela em cima de qualquer textura.
     */
    private fun isAcceptableQuad(corners: List<Vec2>, width: Int, height: Int): Boolean {
        // 1) Área mínima: 1% do quadro. Um marcador pequeno demais não tem pixels
        //    suficientes para uma pose estável (o texto de Ajuda do app já pede que
        //    a figura ocupe boa parte do quadro).
        var doubleArea = 0.0
        for (index in corners.indices) {
            val current = corners[index]
            val next = corners[(index + 1) % corners.size]
            doubleArea += current.x.toDouble() * next.y - next.x.toDouble() * current.y
        }
        val area = kotlin.math.abs(doubleArea) / 2.0
        if (area < width.toDouble() * height * MIN_AREA_RATIO) return false

        // 2) Cantos plausíveis, com folga: o marcador pode estar saindo pela borda.
        corners.forEach { corner ->
            if (corner.x < -QUAD_MARGIN_RATIO * width || corner.x > (1 + QUAD_MARGIN_RATIO) * width) {
                return false
            }
            if (corner.y < -QUAD_MARGIN_RATIO * height || corner.y > (1 + QUAD_MARGIN_RATIO) * height) {
                return false
            }
        }

        // 3) Convexidade: os quatro produtos vetoriais têm de ter o mesmo sinal.
        var positive = 0
        var negative = 0
        for (index in corners.indices) {
            val a = corners[index]
            val b = corners[(index + 1) % corners.size]
            val c = corners[(index + 2) % corners.size]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (cross > 0) positive++ else if (cross < 0) negative++
        }
        if (positive != 4 && negative != 4) return false

        // 4) Proporção: uma perspectiva forte deforma o quadrado, mas não a ponto de
        //    uma aresta ficar cinco vezes maior que a oposta.
        val sides = corners.indices.map { index ->
            val a = corners[index]
            val b = corners[(index + 1) % corners.size]
            kotlin.math.hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        }
        return sides.min() / sides.max() >= MIN_SIDE_RATIO
    }

    /**
     * Pose 3D do marcador a partir dos quatro cantos no quadro.
     *
     * Os pontos de objeto são as mesmas quatro quinas no **referencial do marcador**
     * (o do ARCore para imagens: X = largura, Y = normal, Z = altura), em metros e
     * com a largura física real do marcador — é isso que dá a **escala correta** ao
     * modelo ancorado. Como os pontos são coplanares, usamos `SOLVEPNP_IPPE`, o
     * algoritmo feito para alvo plano (o `ITERATIVE` genérico é instável com apenas
     * quatro pontos coplanares).
     */
    private fun poseFor(reference: ReferenceMarker, corners: List<Vec2>): Pose? {
        val coordinates = FloatArray(8)
        corners.forEachIndexed { index, corner ->
            coordinates[index * 2] = corner.x
            coordinates[index * 2 + 1] = corner.y
        }

        val imagePoints = pointsMat(coordinates, 4)
        val rotationVector = Mat()
        val translationVector = Mat()
        try {
            val solved = solvePnP(
                reference.objectPoints,
                imagePoints,
                cameraMatrix,
                distortion,
                rotationVector,
                translationVector,
                false,
                SOLVEPNP_IPPE,
            )
            if (!solved) return null
            return Pose.fromRodriguesAndTranslation(rotationVector, translationVector)
        } finally {
            imagePoints.release()
            rotationVector.release()
            translationVector.release()
        }
    }

    /**
     * Atualiza o estado de rastreio de cada marcador e devolve os visíveis.
     *
     * As regras são as mesmas que o app Android herdou do ARCore:
     *
     *  * visto agora → `TRACKING` com a pose medida;
     *  * visto há pouco e não encontrado neste quadro → `PAUSED` com a última pose,
     *    para o conteúdo **não piscar** a cada quadro em que a webcam perde o
     *    marcador (luz, foco, mão na frente);
     *  * perdido além de [PAUSE_FRAMES] → `STOPPED`, e sai da cena;
     *  * dois marcadores cadastrados casando com a MESMA figura física (pose a menos
     *    de [POSE_DUPLICATE_TOLERANCE_METERS]) → fica só o mais consistente. É o
     *    `distinctByPose` do app Android, que existia porque dois nós ficavam
     *    exatamente um sobre o outro.
     *
     * A pose devolvida é recalculada dos cantos **suavizados** (e não a medida no
     * quadro): assim o modelo não treme junto com o ruído do casamento de pontos, e
     * o custo é um `solvePnP` por marcador visível.
     */
    private fun updateTracking(candidates: List<MarkerCandidate>): List<DetectedMarker> {
        val seen = dedupeByPose(candidates).associateBy { it.reference.marker.id }

        seen.forEach { (id, candidate) ->
            val track = tracks.getOrPut(id) {
                MarkerTrack(candidate.reference, candidate.corners, candidate.matchCount)
            }
            track.seenNow(candidate, frameCounter)
        }

        tracks.forEach { (id, track) ->
            if (!seen.containsKey(id)) track.pause()
        }
        tracks.values.removeAll { frameCounter - it.lastSeenFrame > PAUSE_FRAMES }

        return tracks.values
            .sortedBy { it.reference.index }
            .mapNotNull { track ->
                val pose = poseFor(track.reference, track.corners) ?: return@mapNotNull null
                DetectedMarker(
                    index = track.reference.index,
                    name = track.reference.marker.id,
                    label = track.reference.marker.label,
                    trackingState = track.state,
                    trackingMethod = track.method,
                    centerPose = pose,
                    extentX = track.reference.marker.physicalWidthMeters,
                    extentZ = track.reference.marker.physicalWidthMeters,
                    corners = track.corners,
                    inlierCount = track.matchCount,
                )
            }
    }

    /**
     * Mantém um candidato por **figura física**.
     *
     * O critério antigo (comparar a translação da pose com 3 cm de tolerância) vinha do
     * app Android, mas não resolvia o caso real: com **casamento cruzado** — a mesma
     * figura impressa casando também com outra referência cadastrada — as poses ficam
     * parecidas sem serem idênticas, e a caixa ciano piscava entre dois nomes. Agora o
     * critério é o que o usuário vê: **mesmo lugar na imagem e mesmo tamanho aparente**
     * (com a proximidade das poses como segundo teste).
     *
     * Quem vence é o candidato com mais pontos casados; no empate, o marcador que já
     * está sendo rastreado — o que evita trocar de nome a cada quadro.
     */
    private fun dedupeByPose(candidates: List<MarkerCandidate>): List<MarkerCandidate> {
        val ordered = candidates.sortedWith(
            compareByDescending<MarkerCandidate> { it.matchCount }
                .thenByDescending { tracks.containsKey(it.reference.marker.id) },
        )

        val kept = mutableListOf<MarkerCandidate>()
        ordered.forEach { candidate ->
            val duplicated = kept.any { existing ->
                isSamePhysicalFigure(existing.corners, candidate.corners) ||
                    arePosesClose(existing.pose.translation, candidate.pose.translation)
            }
            if (!duplicated) kept.add(candidate)
        }

        return kept
    }

    /** Duas posições a menos de [POSE_DUPLICATE_TOLERANCE_METERS] são o mesmo ponto. */
    private fun arePosesClose(a: com.arkz.armodelviewer.model.Vec3, b: com.arkz.armodelviewer.model.Vec3): Boolean {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return dx * dx + dy * dy + dz * dz <
            POSE_DUPLICATE_TOLERANCE_METERS * POSE_DUPLICATE_TOLERANCE_METERS
    }

    private companion object {
        /** Pontos de interesse por quadro/imagem de referência. */
        const val FEATURE_COUNT = 1500

        /** Pirâmide de escala do ORB (padrão do OpenCV, ajustado para impressos). */
        const val SCALE_FACTOR = 1.2f
        const val LEVELS = 8
        const val EDGE_THRESHOLD = 31
        const val FIRST_LEVEL = 0
        const val WTA_K = 2
        const val PATCH_SIZE = 31

        /** Limiar do detector FAST: baixo para pegar detalhes com pouco contraste. */
        const val FAST_THRESHOLD = 20

        /** Pontos guardados por par (acima disso não há ganho de precisão). */
        const val MAX_MATCH_BUFFER = 512

        /** Área mínima do marcador no quadro (fração da imagem). */
        const val MIN_AREA_RATIO = 0.01

        /** Folga para os cantos fora do quadro (fração da largura/altura). */
        const val QUAD_MARGIN_RATIO = 0.15

        /** Proporção mínima entre a menor e a maior aresta. */
        const val MIN_SIDE_RATIO = 0.2

        /**
         * Quantos quadros um marcador não visto continua na cena, em `PAUSED`.
         *
         * 15 quadros ≈ 0,5 s a 30 fps: tempo suficiente para o usuário passar a mão
         * na frente da câmera sem o conteúdo piscar, e curto o suficiente para o
         * marcador sair da cena ao ser afastado.
         */
        const val PAUSE_FRAMES = 15L

        /** Distância (em metros) abaixo da qual duas detecções são a mesma figura. */
        const val POSE_DUPLICATE_TOLERANCE_METERS = 0.03f

        /** Quantos quadros de diagnóstico são gravados antes de parar (evita encher o disco). */
        const val MAX_DIAGNOSTIC_DUMPS = 3

        /** Tipos do OpenCV citados na descrição dos descritores no log. */
        const val TYPE_CV_8UC1 = 0
        const val TYPE_CV_32FC1 = 5
    }
}

/** Um candidato de detecção, antes de virar estado de rastreio. */
private class MarkerCandidate(
    val reference: ReferenceMarker,
    val corners: List<Vec2>,
    val pose: Pose,
    val matchCount: Int,
)

/**
 * Estado de rastreio de UM marcador entre quadros.
 *
 * Guarda os cantos **suavizados** (média exponencial) e o último quadro em que o
 * marcador foi visto. A suavização existe porque o casamento de pontos varia de um
 * quadro para o outro mesmo com a câmera parada: sem ela, a caixa ciano (e, na etapa
 * 4, o modelo) tremeria cerca de um pixel a 30 quadros por segundo — visível e
 * incômodo, ainda mais num modelo 3D.
 */
private class MarkerTrack(
    val reference: ReferenceMarker,
    corners: List<Vec2>,
    matchCount: Int,
) {
    var corners: List<Vec2> = corners
        private set

    var lastSeenFrame: Long = 0
        private set

    var matchCount: Int = matchCount
        private set

    var state: TrackingState = TrackingState.TRACKING
        private set

    var method: TrackingMethod = TrackingMethod.FULL_TRACKING
        private set

    /** Marcador encontrado neste quadro. */
    fun seenNow(candidate: MarkerCandidate, frame: Long) {
        corners = corners.zip(candidate.corners) { previous, measured ->
            Vec2(
                x = previous.x + (measured.x - previous.x) * MARKER_SMOOTHING,
                y = previous.y + (measured.y - previous.y) * MARKER_SMOOTHING,
            )
        }
        matchCount = candidate.matchCount
        lastSeenFrame = frame
        state = TrackingState.TRACKING
        method = TrackingMethod.FULL_TRACKING
    }

    /** Não encontrado neste quadro, mas ainda dentro da janela de pausa. */
    fun pause() {
        state = TrackingState.PAUSED
        method = TrackingMethod.LAST_KNOWN_POSE
    }
}

/**
 * Um marcador com os descritores **pré-calculados**.
 *
 * O cálculo roda no construtor (ORB na imagem de referência) e custa algumas dezenas
 * de milissegundos **por marcador** — quem cria o detector deve fazer isso fora da
 * thread de interface. Depois disso, cada quadro custa só o casamento.
 *
 * O `Mat` da imagem em tons de cinza é uma **visão** sobre um arranjo de bytes de
 * Java; ele é liberado no fim do `init` porque os descritores já são independentes.
 */
private class ReferenceMarker(
    val index: Int,
    val marker: MarkerDefinition,
    orb: ORB,
) {
    /** Pontos de interesse da referência (mesmos índices dos descritores). */
    val points: Point2fVector

    /** Descritores da referência. */
    val descriptors: Mat

    /** As quatro quinas da imagem de referência, em pixels (TL, TR, BR, BL). */
    val corners: Mat

    /**
     * As mesmas quinas no **referencial do marcador**, em metros: X = largura,
     * Y = normal (zero, o marcador é plano) e Z = altura NA imagem.
     */
    val objectPoints: Mat

    /** `false` quando a imagem é lisa demais para o ORB (ex.: uma parede). */
    val hasFeatures: Boolean

    /**
     * Bytes da imagem em tons de cinza e o ponteiro nativo que o `Mat` referencia.
     *
     * Guardar o `BytePointer` é **obrigatório**: o `Mat` abaixo é uma visão sobre a
     * memória nativa dele (não há cópia), e o `Mat` não mantém vivo o objeto Java.
     * Se o ponteiro sair de escopo, o coletor de lixo pode devolver essa memória
     * enquanto o OpenCV ainda a lê — o resultado é comportamento indefinido
     * (descritores inválidos, matriz vazia ou interrupção do processo).
     */
    private val grayBytes: ByteArray
    private val grayPointer: BytePointer

    init {
        val width = marker.image.width
        val height = marker.image.height
        grayBytes = ByteArray(width * height)
        marker.image.toGrayBytes(grayBytes, IntArray(width * height))
        grayPointer = BytePointer(*grayBytes)
        val gray = Mat(height, width, CV_8UC1, grayPointer)

        // A imagem de referência é REDUZIDA para uma escala próxima da que a câmera vê
        // de fato: a 30–60 cm, um marcador de 15 cm ocupa de ~150 a ~650 px no quadro,
        // enquanto o arquivo tem 1000 px. O ORB é invariante a escala pela pirâmide,
        // mas ela cobre ~3,6× (8 níveis de 1,2); sem esta redução, a diferença de ~6×
        // deixa o marcador **irreconhecível a meio metro** e a detecção só funciona com
        // a figura colada na webcam. Depois da redução, a faixa coberta passa a ser de
        // ~170 px a ~2100 px — folgadamente a faixa de uso.
        val scale = minOf(1.0, REFERENCE_MAX_SIDE.toDouble() / maxOf(width, height))
        val referenceWidth = maxOf(1, (width * scale).roundToInt())
        val referenceHeight = maxOf(1, (height * scale).roundToInt())

        val scaled = Mat()
        try {
            if (referenceWidth != width || referenceHeight != height) {
                // `INTER_AREA` é a interpolação correta para REDUZIR (a `LINEAR`
                // amostraria só alguns pixels e criaria ruído nos módulos do QR).
                resize(gray, scaled, Size(referenceWidth, referenceHeight), 0.0, 0.0, INTER_AREA)
            } else {
                gray.copyTo(scaled)
            }

            val keyPoints = KeyPointVector()
            val computed = Mat()
            try {
                orb.detectAndCompute(scaled, Mat(), keyPoints, computed)
                hasFeatures = !computed.empty() && !keyPoints.empty()

                descriptors = Mat()
                points = Point2fVector()
                if (hasFeatures) {
                    computed.copyTo(descriptors)
                    KeyPoint.convert(keyPoints, points)
                }

                val halfWidth = marker.physicalWidthMeters / 2f
                // As quinas são as da imagem REDUZIDA: é ela que a homografia usa para
                // levar um ponto da referência até o quadro da câmera.
                corners = markerCornersMat(referenceWidth, referenceHeight)
                objectPoints = markerObjectPointsMat(halfWidth, halfWidth)
            } finally {
                computed.release()
                keyPoints.clear()
            }
        } finally {
            scaled.release()
            gray.release()
        }
    }

    /** Libera a memória nativa (descritores, matrizes de quinas e a imagem em cinza). */
    fun close() {
        descriptors.release()
        points.close()
        corners.release()
        objectPoints.release()
        grayPointer.close()
    }
}

/**
 * Duas detecções são a **mesma figura física**?
 *
 * Comparar a translação da pose (o critério do app Android, com 3 cm) não basta: no
 * casamento cruzado — a mesma figura impressa casando com duas referências cadastradas —
 * as poses ficam parecidas sem serem idênticas. Aqui o critério é o que o usuário vê:
 * **o mesmo lugar na imagem e o mesmo tamanho aparente**.
 */
private fun isSamePhysicalFigure(a: List<Vec2>, b: List<Vec2>): Boolean {
    if (a.size < 4 || b.size < 4) return false

    val centerA = quadCenter(a)
    val centerB = quadCenter(b)
    val apparentSize = quadMeanSide(a).coerceAtLeast(1f)
    val centerDistance = kotlin.math.hypot(
        (centerA.x - centerB.x).toDouble(),
        (centerA.y - centerB.y).toDouble(),
    )
    if (centerDistance > SAME_FIGURE_CENTER_RATIO * apparentSize) return false

    val areaA = quadArea(a)
    val areaB = quadArea(b)
    if (areaA <= 0.0 || areaB <= 0.0) return false

    return minOf(areaA, areaB) / maxOf(areaA, areaB) >= SAME_FIGURE_AREA_RATIO
}

/** Centro de um quadrilátero (média dos quatro cantos). */
private fun quadCenter(corners: List<Vec2>): Vec2 = Vec2(
    x = corners.sumOf { it.x.toDouble() }.toFloat() / corners.size,
    y = corners.sumOf { it.y.toDouble() }.toFloat() / corners.size,
)

/** Comprimento médio das arestas — o "tamanho aparente" do marcador no quadro. */
private fun quadMeanSide(corners: List<Vec2>): Float {
    val sides = corners.indices.map { index ->
        val a = corners[index]
        val b = corners[(index + 1) % corners.size]
        kotlin.math.hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
    }
    return (sides.sum() / sides.size).toFloat()
}

/** Área de um polígono simples (fórmula do cadarço). */
private fun quadArea(corners: List<Vec2>): Double {
    var doubleArea = 0.0
    for (index in corners.indices) {
        val current = corners[index]
        val next = corners[(index + 1) % corners.size]
        doubleArea += current.x.toDouble() * next.y - next.x.toDouble() * current.y
    }
    return kotlin.math.abs(doubleArea) / 2.0
}

/** Proporção entre o deslocamento dos centros e o tamanho aparente (mesma figura). */
private const val SAME_FIGURE_CENTER_RATIO = 0.35f

/** Proporção mínima entre as áreas de dois quadriláteros da mesma figura. */
private const val SAME_FIGURE_AREA_RATIO = 0.5

/** Peso do valor novo na suavização dos cantos (metade novo, metade antigo). */
private const val MARKER_SMOOTHING = 0.5f

/**
 * Lado máximo da imagem de referência usada para extrair os pontos de interesse.
 *
 * 600 px põe a referência dentro da faixa em que a câmera vê o marcador na distância
 * de uso (30–60 cm ⇒ ~150–650 px no quadro), mantendo a diferença de escala dentro do
 * alcance da pirâmide do ORB (8 níveis de 1,2 ≈ 3,6×).
 *
 * Sem isso, uma referência de 1000 px só casaria com o marcador a menos de ~25 cm da
 * webcam: é o tipo de defeito que passa nos testes sintéticos e falha na mão do
 * usuário — por isso existe o teste `reconhece o marcador na distancia de uso tipica`.
 */
private const val REFERENCE_MAX_SIDE = 600

/** Monta um `Mat` N×1 de `CV_32FC2` a partir de pares x,y intercalados. */
internal fun pointsMat(coordinates: FloatArray, count: Int): Mat {
    val points = Mat(count, 1, CV_32FC2)
    val indexer = points.createIndexer<FloatIndexer>()
    try {
        for (index in 0 until count) {
            indexer.put(index.toLong(), 0L, 0L, coordinates[index * 2])
            indexer.put(index.toLong(), 0L, 1L, coordinates[index * 2 + 1])
        }
    } finally {
        indexer.release()
    }
    return points
}

/**
 * As quatro quinas de uma imagem, em pixels, na ordem superior-esquerdo,
 * superior-direito, inferior-direito, inferior-esquerdo — a MESMA ordem das quinas
 * em metros ([markerObjectPointsMat]), que é o que o `solvePnP` exige para casar
 * ponto de objeto com ponto de imagem.
 */
internal fun markerCornersMat(width: Int, height: Int): Mat {
    val coordinates = floatArrayOf(
        0f, 0f,
        width.toFloat(), 0f,
        width.toFloat(), height.toFloat(),
        0f, height.toFloat(),
    )
    return pointsMat(coordinates, 4)
}

/**
 * As quatro quinas no referencial do marcador, em metros.
 *
 * O referencial é o do ARCore para imagens — X = largura, Y = **normal** (por isso
 * zero aqui: o marcador é plano) e Z = altura NA imagem. Manter esta convenção é o
 * que permite reaproveitar o `ModelMetrics.anchorPosition` do app Android sem
 * mudança nenhuma.
 */
private fun markerObjectPointsMat(halfWidth: Float, halfHeight: Float): Mat {
    val coordinates = floatArrayOf(
        -halfWidth, 0f, -halfHeight,
        halfWidth, 0f, -halfHeight,
        halfWidth, 0f, halfHeight,
        -halfWidth, 0f, halfHeight,
    )

    val points = Mat(4, 1, CV_32FC3)
    val indexer = points.createIndexer<FloatIndexer>()
    try {
        for (index in 0 until 4) {
            for (axis in 0 until 3) {
                indexer.put(index.toLong(), 0L, axis.toLong(), coordinates[index * 3 + axis])
            }
        }
    } finally {
        indexer.release()
    }
    return points
}
