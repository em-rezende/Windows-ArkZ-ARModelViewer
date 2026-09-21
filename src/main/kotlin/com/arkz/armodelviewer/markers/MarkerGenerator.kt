/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.markers

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.random.Random

/**
 * Desenha a imagem de um marcador NOVO — a figura que a detecção procura no feed
 * da câmera — a partir do nome dado pelo usuário.
 *
 * O padrão é o mesmo perfil que o ARCore recomenda (alto contraste, muitos
 * cantos, nada repetitivo) e o mesmo do gerador do app Android: matriz
 * pseudo-aleatória de módulos preto/branco sorteada com **seed determinística** +
 * os três "finder patterns" de um QR code.
 *
 * A seed vem do nome: o MESMO nome gera sempre a MESMA figura, e nomes diferentes
 * geram figuras diferentes, de modo que dois marcadores nunca se confundem.
 *
 * **Portabilidade byte a byte:** a implementação usa `kotlin.random.Random(seed)`
 * com `seed = label.trim().lowercase().hashCode()`. O `hashCode` de `String` é
 * definido pela especificação da linguagem Java e o `Random` do Kotlin é o mesmo
 * algoritmo em todas as plataformas — logo, o mesmo nome gera **a mesma imagem**
 * no Android e no Windows, e um marcador criado num aparelho pode ser impresso do
 * outro lado. O teste `MarkerGeneratorTest` fixa essa garantia.
 *
 * A imagem sai em `TYPE_INT_ARGB` (o equivalente desktop do `ARGB_8888`, que o
 * ARCore exige) com uma zona de silêncio branca em volta, como num QR code
 * impresso.
 */
object MarkerGenerator {

    /** Resolução padrão: suficiente para imprimir em 15 cm com boa definição. */
    const val DEFAULT_SIZE_PIXELS = 1024

    /** Módulos (quadradinhos) do miolo do marcador, como numa versão 4 de QR. */
    private const val MODULES = 33

    /** Lado dos "finder patterns" de um QR code, em módulos. */
    private const val FINDER_PATTERN_MODULES = 7

    /** Zona de silêncio branca em volta do miolo, em módulos. */
    private const val QUIET_ZONE_MODULES = 4

    /**
     * Gera o marcador de [label].
     *
     * @param label nome informado pelo usuário (define a seed do padrão).
     * @param sizePixels lado aproximado da imagem final, em pixels.
     */
    fun generate(label: String, sizePixels: Int = DEFAULT_SIZE_PIXELS): BufferedImage {
        require(sizePixels > 0) { "Tamanho de marcador inválido: $sizePixels px" }

        val totalModules = MODULES + 2 * QUIET_ZONE_MODULES
        // Múltiplo exato de módulos: nenhum módulo fica com meio pixel (a detecção
        // compara bordas nítidas — meio tom atrapalha o reconhecimento).
        val module = (sizePixels / totalModules).coerceAtLeast(2)
        val sidePixels = module * totalModules

        val image = BufferedImage(sidePixels, sidePixels, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            // Borda dura: módulo de poucos pixels não pode virar cinza.
            graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF,
            )
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, sidePixels, sidePixels)

            val finderOrigins = listOf(
                0 to 0,
                (MODULES - FINDER_PATTERN_MODULES) to 0,
                0 to (MODULES - FINDER_PATTERN_MODULES),
            )

            // Áreas reservadas aos finder patterns (com 1 módulo de folga em
            // volta, o "separador" do QR code) — o sorteio não escreve nelas.
            val reserved = Array(MODULES) { BooleanArray(MODULES) }
            for ((originX, originY) in finderOrigins) {
                for (y in -1..FINDER_PATTERN_MODULES) {
                    for (x in -1..FINDER_PATTERN_MODULES) {
                        val moduleX = originX + x
                        val moduleY = originY + y
                        if (moduleX in 0 until MODULES && moduleY in 0 until MODULES) {
                            reserved[moduleX][moduleY] = true
                        }
                    }
                }
            }

            val random = Random(label.trim().lowercase().hashCode())
            graphics.color = Color.BLACK
            for (y in 0 until MODULES) {
                for (x in 0 until MODULES) {
                    if (reserved[x][y]) continue
                    if (random.nextDouble() > 0.5) {
                        fillModules(graphics, x, y, count = 1, module = module)
                    }
                }
            }

            // Finder patterns: quadrado preto, anel branco e núcleo preto.
            for ((originX, originY) in finderOrigins) {
                graphics.color = Color.BLACK
                fillModules(graphics, originX, originY, FINDER_PATTERN_MODULES, module)
                graphics.color = Color.WHITE
                fillModules(graphics, originX + 1, originY + 1, FINDER_PATTERN_MODULES - 2, module)
                graphics.color = Color.BLACK
                fillModules(graphics, originX + 2, originY + 2, FINDER_PATTERN_MODULES - 4, module)
            }
        } finally {
            graphics.dispose()
        }

        return image
    }

    /** Pinta [count]×[count] módulos a partir de ([originX], [originY]). */
    private fun fillModules(
        graphics: java.awt.Graphics2D,
        originX: Int,
        originY: Int,
        count: Int,
        module: Int,
    ) {
        val left = (originX + QUIET_ZONE_MODULES) * module
        val top = (originY + QUIET_ZONE_MODULES) * module
        graphics.fillRect(left, top, count * module, count * module)
    }
}
