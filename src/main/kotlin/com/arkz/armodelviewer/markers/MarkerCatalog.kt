/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3, publicada pela Free Software
 * Foundation. Este programa é distribuído na esperança de que seja útil, mas SEM
 * NENHUMA GARANTIA; sem mesmo a garantia implícita de COMERCIABILIDADE ou
 * ADEQUAÇÃO A UM PROPÓSITO ESPECÍFICO. Veja o arquivo LICENSE na raiz do projeto.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 * Versão Windows do app Android:
 * https://github.com/em-rezende/Android-ArkZ-ARModelViewer
 */

package com.arkz.armodelviewer.markers

import java.awt.image.BufferedImage
import java.io.InputStream
import javax.imageio.ImageIO

/**
 * Um marcador pronto para uso — a imagem de referência que o detector procura no
 * feed da câmera.
 *
 * É o equivalente desktop do `MarkerDefinition` do app Android: lá a imagem é um
 * `Bitmap` (exigência do `AugmentedImageDatabase` do ARCore, que só aceita
 * `ARGB_8888`); aqui é um [BufferedImage], que serve tanto para a impressão
 * quanto para a detecção por visão computacional.
 *
 * @property id Nome do marcador; é o valor devolvido pela detecção quando o
 *   marcador é reconhecido (precisa ser único).
 * @property label Rótulo exibido na interface.
 * @property physicalWidthMeters Largura FÍSICA real da imagem, em metros.
 * @property image Imagem de referência já normalizada em `TYPE_INT_ARGB`.
 * @property isCustom `true` para marcadores adicionados pelo usuário (podem ser
 *   removidos pela interface).
 */
data class MarkerDefinition(
    val id: String,
    val label: String,
    val physicalWidthMeters: Float,
    val image: BufferedImage,
    val isCustom: Boolean = false,
)

/** Marcador embutido no app (imagem empacotada em `resources/`). */
private data class BundledMarker(
    val id: String,
    val label: String,
    val resourcePath: String,
    val physicalWidthMeters: Float,
)

/**
 * Marcadores que acompanham o app.
 *
 * As imagens são QR codes (alto contraste, muitos cantos, padrão não repetitivo)
 * — o perfil que o ARCore recomenda e que também é o melhor caso para a
 * detecção por pontos de interesse usada nesta versão. Os arquivos são
 * exatamente os do app Android (`augmented_images/marker_a.png` e
 * `marker_b.png`), para que um marcador já impresso continue valendo.
 */
object MarkerCatalog {

    /** Largura física padrão considerada para um marcador (15 cm). */
    const val DEFAULT_PHYSICAL_WIDTH_METERS = 0.15f

    private val bundled = listOf(
        BundledMarker(
            id = "marker_a",
            label = "Marcador A",
            resourcePath = "augmented_images/marker_a.png",
            physicalWidthMeters = DEFAULT_PHYSICAL_WIDTH_METERS,
        ),
        BundledMarker(
            id = "marker_b",
            label = "Marcador B",
            resourcePath = "augmented_images/marker_b.png",
            physicalWidthMeters = DEFAULT_PHYSICAL_WIDTH_METERS,
        ),
    )

    /**
     * Carrega os marcadores embutidos do classpath.
     *
     * Um marcador que não puder ser lido é simplesmente omitido (como no Android,
     * onde um asset ausente não derruba o app) — mas a falha fica registrada no
     * log do aplicativo, porque "o marcador não é detectado" costuma começar com
     * "a imagem não carregou".
     */
    fun loadBundled(log: (String) -> Unit = {}): List<MarkerDefinition> = bundled.mapNotNull { marker ->
        runCatching {
            val stream = MarkerCatalog::class.java.classLoader
                .getResourceAsStream(marker.resourcePath)
                ?: error("imagem não encontrada no classpath: ${marker.resourcePath}")
            stream.use { decodeArgb(it) }.let { image ->
                MarkerDefinition(
                    id = marker.id,
                    label = marker.label,
                    physicalWidthMeters = marker.physicalWidthMeters,
                    image = image,
                )
            }
        }.onFailure { error ->
            log("Falha ao carregar o marcador '${marker.id}': ${error.message}")
        }.getOrNull()
    }
}

/** Rótulo de um marcador a partir do nome devolvido pela detecção. */
fun List<MarkerDefinition>.labelFor(id: String): String =
    firstOrNull { it.id == id }?.label ?: id

/** Decodifica uma imagem garantindo o formato `TYPE_INT_ARGB`. */
fun decodeArgb(input: InputStream): BufferedImage {
    val decoded = ImageIO.read(input)
        ?: error("Não foi possível decodificar a imagem.")
    return decoded.toArgb()
}

/**
 * Converte para `TYPE_INT_ARGB` (o equivalente desktop do `ARGB_8888`).
 *
 * Manter um único formato simplifica as duas pontas que consomem a imagem: a
 * folha de impressão (que desenha em `Graphics2D`) e a detecção (que converte
 * para tons de cinza).
 */
fun BufferedImage.toArgb(): BufferedImage =
    if (type == BufferedImage.TYPE_INT_ARGB) {
        this
    } else {
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { converted ->
            val graphics = converted.createGraphics()
            try {
                graphics.drawImage(this, 0, 0, null)
            } finally {
                graphics.dispose()
            }
        }
    }
