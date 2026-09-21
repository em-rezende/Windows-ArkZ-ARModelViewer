/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.tools

import com.arkz.armodelviewer.camera.CameraDevices

/**
 * Lista as webcams vistas pelo OpenCV — a tarefa `listCameras` do Gradle.
 *
 * ```powershell
 * .\gradlew.bat -q listCameras
 * ```
 *
 * Por que uma tarefa do Gradle (e não só o PowerShell): o índice que o app usa é o
 * do `VideoCapture` do OpenCV, e **só o OpenCV sabe** quais índices abrem de fato
 * nesta máquina. O `tools/list-cameras.ps1` consulta o Windows (PnP) para mostrar
 * os nomes amigáveis e chama esta tarefa para mostrar os índices.
 */
fun main() {
    val maxIndex = System.getProperty("arkz.camera.maxIndex")?.toIntOrNull()
        ?: CameraDevices.DEFAULT_MAX_INDEX

    println("Procurando câmeras nos índices 0..$maxIndex (MSMF → DirectShow → padrão)…")
    println()

    val devices = CameraDevices.enumerate(maxIndex)

    if (devices.isEmpty()) {
        println("Nenhuma câmera respondeu.")
        println("Verifique se a webcam não está sendo usada por outro programa e se ela")
        println("não foi desativada em Configurações › Privacidade e segurança › Câmera.")
        return
    }

    println("${devices.size} câmera(s) encontrada(s):")
    devices.forEach { device ->
        println("  índice ${device.index}  →  ${device.width}×${device.height}  (${device.label})")
    }
    println()
    println("O app usa o índice; para fixar outra câmera, escolha-a no app (menu de câmera).")
}
