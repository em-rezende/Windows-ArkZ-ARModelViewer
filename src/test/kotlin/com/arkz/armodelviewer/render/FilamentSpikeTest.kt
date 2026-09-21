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

import com.arkz.armodelviewer.util.AppLog
import io.github.erkko68.filament.Camera
import io.github.erkko68.filament.Engine
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **Spike** do renderizador: o Filament inicializa e desenha um quadro nesta máquina?
 *
 * Este é o risco que está registrado desde o começo do projeto: o Google **removeu o
 * suporte a Java/desktop do Filament** e a única fonte de biblioteca nativa para Windows é
 * o binding comunitário `filament-kmp` (ver a decisão 2 do README). Se este teste passa,
 * o caminho de renderização está viável; se falha, o backend de reserva em OpenGL/LWJGL
 * deixa de ser hipótese e passa a ser o plano.
 *
 * O teste faz o mínimo que prova o essencial, **sem janela e sem GPU visível**:
 *
 *  1. cria o `Engine` (é aqui que as bibliotecas nativas e o FFM são exercitados);
 *  2. cria cena, view e câmera, e liga os três;
 *  3. cria um `SwapChain` **offscreen** de 256×256;
 *  4. desenha **um quadro** (`beginFrame` → `render` → `endFrame`);
 *  5. libera tudo na ordem inversa.
 *
 * O mesmo `SwapChain` offscreen é o que vai servir à captura de tela sem interface
 * (etapa 7) e ao desenho da cena sobre o vídeo da câmera.
 */
class FilamentSpikeTest {

    @Test
    fun `o Filament inicializa e desenha um quadro fora da tela`() {
        val engine = Engine.create()
        AppLog.info("Filament: engine criado.")

        try {
            val scene = engine.createScene()
            val camera = engine.createCamera()
            // 45° verticais, proporção 1:1, plano próximo a 10 cm e distante a 100 m —
            // valores de uma cena de RA (o mesmo campo de visão que a webcam assume).
            camera.setProjection(45.0, 1.0, 0.1, 100.0, Camera.Fov.VERTICAL)
            camera.lookAt(0.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 1.0, 0.0)

            val view = engine.createView()
            view.scene = scene
            view.camera = camera

            val swapChain = engine.createSwapChain(CANVAS, CANVAS, 0L)
            val renderer = engine.createRenderer()

            try {
                val started = renderer.beginFrame(swapChain, System.nanoTime())
                assertTrue(started, "o Filament não conseguiu começar o quadro")

                renderer.render(view)
                renderer.endFrame()
                AppLog.info("Filament: quadro de ${CANVAS}×$CANVAS desenhado offscreen.")
            } finally {
                engine.destroyRenderer(renderer)
            }

            engine.destroySwapChain(swapChain)
            engine.destroyView(view)
            engine.destroyCamera(camera)
            engine.destroyScene(scene)
        } finally {
            // O `Engine` é o dono dos recursos nativos: destruí-lo é o que evita o
            // travamento no encerramento do JVM (um cuidado que a documentação do
            // binding cita explicitamente para o Windows).
            engine.destroy()
            AppLog.info("Filament: engine destruído.")
        }
    }

    private companion object {
        /** Lado do quadro de teste (pequeno: só precisa provar que desenha). */
        const val CANVAS = 256
    }
}
