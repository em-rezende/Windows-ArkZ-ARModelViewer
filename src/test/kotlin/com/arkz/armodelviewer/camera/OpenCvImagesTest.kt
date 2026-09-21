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

import org.bytedeco.opencv.global.opencv_core.CV_8UC1
import org.bytedeco.opencv.global.opencv_core.CV_8UC2
import org.bytedeco.opencv.global.opencv_core.CV_8UC3
import org.bytedeco.opencv.global.opencv_core.CV_8UC4
import org.bytedeco.opencv.opencv_core.Mat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Testes da conversão quadro OpenCV → imagem da interface.
 *
 * Estes testes carregam os **nativos do OpenCV** (a biblioteca é compartilhada e
 * carregada na primeira criação de um `Mat`), então eles também provam que o
 * classpath nativo está completo — o erro mais provável de um `msi` mal montado.
 *
 * A ordem dos canais é o ponto sensível: o OpenCV usa **BGR** e a interface espera
 * **RGB**. Trocar os dois deixa o vídeo com vermelho e azul invertidos — um defeito
 * que passa por "configuração da webcam" e engana qualquer um.
 */
class OpenCvImagesTest {

    @Test
    fun `converte BGR em RGB na ordem certa`() {
        val mat = Mat(1, 2, CV_8UC3)
        try {
            // Dois pixels: B=10,G=20,R=30 e B=40,G=50,R=60.
            val bytes = byteArrayOf(10, 20, 30, 40, 50, 60)
            mat.data().put(bytes, 0, bytes.size)

            val image = mat.toArgbImage()

            assertEquals(2, image.width)
            assertEquals(1, image.height)
            assertEquals(0xFF1E140A.toInt(), image.getRGB(0, 0), "esperado R=30,G=20,B=10")
            assertEquals(0xFF3C3228.toInt(), image.getRGB(1, 0), "esperado R=60,G=50,B=40")
        } finally {
            mat.release()
        }
    }

    @Test
    fun `converte BGRA ignorando o canal alfa`() {
        val mat = Mat(1, 1, CV_8UC4)
        try {
            val bytes = byteArrayOf(10, 20, 30, 40)
            mat.data().put(bytes, 0, bytes.size)

            val image = mat.toArgbImage()

            assertEquals(0xFF1E140A.toInt(), image.getRGB(0, 0))
        } finally {
            mat.release()
        }
    }

    @Test
    fun `converte tons de cinza para cinza`() {
        val mat = Mat(1, 2, CV_8UC1)
        try {
            val bytes = byteArrayOf(0x80.toByte(), 0xFF.toByte())
            mat.data().put(bytes, 0, bytes.size)

            val image = mat.toArgbImage()

            assertEquals(0xFF808080.toInt(), image.getRGB(0, 0))
            assertEquals(0xFFFFFFFF.toInt(), image.getRGB(1, 0))
        } finally {
            mat.release()
        }
    }

    @Test
    fun `um quadro vazio e recusado com mensagem clara`() {
        val empty = Mat()
        try {
            val error = assertFailsWith<IllegalArgumentException> { empty.toArgbImage() }
            assertEquals("Quadro vazio: a câmera não entregou imagem.", error.message)
        } finally {
            empty.release()
        }
    }

    @Test
    fun `um numero de canais inesperado e recusado`() {
        val twoChannels = Mat(1, 1, CV_8UC2)
        try {
            assertFailsWith<IllegalStateException> { twoChannels.toArgbImage() }
        } finally {
            twoChannels.release()
        }
    }
}
