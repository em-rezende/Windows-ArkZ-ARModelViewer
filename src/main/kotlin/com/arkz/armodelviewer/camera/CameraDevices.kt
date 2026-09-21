/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.camera

import org.bytedeco.opencv.global.opencv_videoio.CAP_ANY
import org.bytedeco.opencv.global.opencv_videoio.CAP_DSHOW
import org.bytedeco.opencv.global.opencv_videoio.CAP_MSMF
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_videoio.VideoCapture

/**
 * Uma webcam disponível no computador.
 *
 * @property index índice usado pelo OpenCV (`VideoCapture(índice)`).
 * @property width/height resolução do primeiro quadro capturado — serve para a
 *   interface mostrar algo concreto ("1280×720") ao usuário escolher o dispositivo.
 */
data class CameraDevice(
    val index: Int,
    val width: Int,
    val height: Int,
) {
    /** Rótulo exibido na lista de dispositivos. */
    val label: String get() = "Câmera ${index + 1} (${width}×${height})"
}

/**
 * Enumeração das webcams.
 *
 * No Android a câmera era escolhida pelo sistema (ARCore abria a câmera traseira);
 * no desktop é comum haver mais de uma (a integrada do notebook e uma USB) e o
 * usuário precisa poder escolher — inclusive quando a integrada está coberta pela
 * tampa do notebook ou desativada no Windows.
 *
 * A enumeração **não** descobre nomes amigáveis: o OpenCV só expõe índices. Os
 * nomes aparecem no `tools/list-cameras.ps1`, que consulta o Windows (PnP) em
 * paralelo, para o usuário conseguir associar "Câmera 2" ao dispositivo físico.
 */
object CameraDevices {

    /** Índices testados quando o usuário não pede uma busca mais ampla. */
    const val DEFAULT_MAX_INDEX = 4

    /**
     * Backends tentados, em ordem.
     *
     * `CAP_MSMF` (Media Foundation) é o caminho moderno do Windows e o que dá
     * melhor desempenho; `CAP_DSHOW` (DirectShow) é o mais compatível e resolve
     * boa parte das webcams antigas/baratas que o MSMF recusa; `CAP_ANY` deixa o
     * próprio OpenCV decidir.
     */
    val backends: List<Int> = listOf(CAP_MSMF, CAP_DSHOW, CAP_ANY)

    /**
     * Lista as câmeras que conseguem abrir e entregar um quadro.
     *
     * Abrir uma câmera custa centenas de milissegundos; por isso a enumeração é
     * feita **fora** da thread de interface e limitada por [maxIndex].
     */
    fun enumerate(maxIndex: Int = DEFAULT_MAX_INDEX): List<CameraDevice> {
        val found = mutableListOf<CameraDevice>()

        for (index in 0..maxIndex) {
            val device = probe(index) ?: continue
            found.add(device)
        }

        return found
    }

    /**
     * Escolhe qual câmera usar.
     *
     * Regras (na ordem): a que o usuário escolheu antes, se ainda existir; a
     * primeira disponível; `null` quando não há nenhuma.
     */
    fun preferred(devices: List<CameraDevice>, savedIndex: Int?): CameraDevice? =
        devices.firstOrNull { it.index == savedIndex } ?: devices.firstOrNull()

    /** Tenta abrir a câmera [index]; devolve `null` quando nenhum backend funciona. */
    private fun probe(index: Int): CameraDevice? {
        backends.forEach { backend ->
            val capture = VideoCapture()
            try {
                if (!capture.open(index, backend) || !capture.isOpened()) return@forEach

                val frame = Mat()
                try {
                    // `read` (e não só `open`): uma câmera "aberta" que não entrega
                    // quadro nenhum não serve para o app — e é exatamente o caso de
                    // um dispositivo ocupado por outro programa.
                    if (capture.read(frame) && !frame.empty()) {
                        return CameraDevice(index = index, width = frame.cols(), height = frame.rows())
                    }
                } finally {
                    frame.release()
                }
            } catch (_: Throwable) {
                // Um backend que estoura não pode impedir a tentativa dos outros.
            } finally {
                runCatching { capture.release() }
            }
        }

        return null
    }
}
