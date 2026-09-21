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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes da enumeração de webcams.
 *
 * A última asserção é de integração de propósito: ela abre a câmera de verdade (se
 * existir) e prova que o OpenCV nativo carrega e que o caminho
 * "abrir → ler quadro → liberar" não estoura. Numa máquina sem webcam, ela apenas
 * confirma que a lista volta vazia — sem falhar.
 */
class CameraDevicesTest {

    private val devices = listOf(
        CameraDevice(index = 0, width = 640, height = 480),
        CameraDevice(index = 1, width = 1280, height = 720),
    )

    @Test
    fun `o dispositivo escolhido antes tem preferencia`() {
        assertEquals(1, CameraDevices.preferred(devices, savedIndex = 1)?.index)
    }

    @Test
    fun `uma escolha que desapareceu cai na primeira camera`() {
        assertEquals(0, CameraDevices.preferred(devices, savedIndex = 7)?.index)
        assertEquals(0, CameraDevices.preferred(devices, savedIndex = null)?.index)
    }

    @Test
    fun `sem cameras nao ha preferida`() {
        assertNull(CameraDevices.preferred(emptyList(), savedIndex = 0))
    }

    @Test
    fun `os backends sao tentados do mais moderno para o mais compativel`() {
        assertEquals(listOf(CAP_MSMF, CAP_DSHOW, CAP_ANY), CameraDevices.backends)
    }

    @Test
    fun `um limite negativo nao chega a tocar no hardware`() {
        assertTrue(CameraDevices.enumerate(maxIndex = -1).isEmpty())
    }

    @Test
    fun `o rotulo mostra o numero humano e a resolucao`() {
        assertEquals("Câmera 1 (640×480)", devices.first().label)
        assertEquals("Câmera 2 (1280×720)", devices[1].label)
    }

    @Test
    fun `abrir a camera do computador nao estoura e devolve dados coerentes`() {
        val found = CameraDevices.enumerate(maxIndex = 0)

        assertTrue(found.all { it.index == 0 })
        found.forEach { device ->
            assertTrue(device.width > 0 && device.height > 0, "resolução inválida: $device")
        }
    }
}
