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

import com.arkz.armodelviewer.model.Vec3

/**
 * Converte gestos em valores da cena — a parte da etapa 6 que **não** depende de tela.
 *
 * As três entradas que o aplicativo aceita acabam nas mesmas duas grandezas: o **tamanho**
 * do modelo e o **deslocamento** dele sobre o plano da figura.
 *
 * ## As entradas, e por que `Ctrl`+roda é a pinça
 *
 * | Gesto | Como chega ao aplicativo |
 * |---|---|
 * | **Pinça** em touchpad de precisão | o Windows entrega como **`Ctrl`+roda** — não existe evento de pinça separado no desktop |
 * | **Pinça** em tela sensível ao toque | **não funciona no Windows**: a entrada do desktop recebe o toque já convertido em mouse, de um contato só (decisão 31 do roadmap) — na tela, o zoom é pelos botões `−`/`+`, pelo cursor de tamanho ou pela escala automática |
 * | `Ctrl`+roda do mouse | roda com a tecla `Ctrl` pressionada |
 * | `+` / `-` no teclado | tecla a tecla |
 *
 * Ou seja: no desktop, "pinça no touchpad" e "`Ctrl`+roda" são **o mesmo caminho de código**.
 * Foi assim que o zoom ficou implementado uma vez só — e é por isso que o aplicativo num
 * computador com touchpad de precisão responde exatamente como o usuário espera, mesmo sem
 * uma tela sensível ao toque.
 *
 * ## Zoom multiplicativo, e não por soma
 *
 * O tamanho é multiplicado (10% por passo), e não somado: somar faria cada passo valer coisas
 * diferentes conforme o tamanho atual — um passo de 1 cm é muito num modelo de 5 cm e nada
 * num modelo de 2 m. Multiplicando, a sensação é a mesma em toda a faixa.
 *
 * ## O arrasto acompanha o tamanho do modelo
 *
 * O deslocamento por pixel é proporcional ao **tamanho atual** do modelo: assim arrastar
 * "uma tela" move o modelo na mesma proporção da tela, quer ele esteja com 5 cm ou com 2 m.
 * O deslocamento é limitado a [MAX_PAN_METERS] para fora do centro da figura — o suficiente
 * para pôr o modelo ao lado ou à frente do marcador, sem o risco de perdê-lo de vista.
 */
object InteractiveInput {

    /**
     * Fator de zoom por passo: 10% de aumento (ou redução) por "clique" da roda.
     *
     * Com 32 passos se vai de 2 cm a 2 m (a faixa inteira), o que dá uma volta de roda
     * confortável para atravessar tudo.
     */
    const val ZOOM_STEP = 1.1f

    /** Deslocamento por pixel de arrasto, como fração do tamanho do modelo. */
    const val PAN_FRACTION_PER_PIXEL = 0.002f

    /** Limite do deslocamento no plano da figura, em metros. */
    const val MAX_PAN_METERS = 0.5f

    /**
     * Tamanho depois de [steps] passos de zoom (negativo aproxima).
     *
     * O resultado é limitado à faixa do painel — a mesma que os sliders e a escala automática
     * respeitam, para que nenhum caminho de entrada leve a cena a um tamanho que o painel não
     * saiba mostrar.
     */
    fun zoomedSize(currentMeters: Float, steps: Float): Float {
        val factor = ZOOM_STEP.pow(steps)
        return (currentMeters * factor).coerceIn(
            RenderScene.MIN_SIZE_METERS,
            RenderScene.MAX_SIZE_METERS,
        )
    }

    /**
     * Converção do fator de uma pinça em passos de zoom.
     *
     * A pinça chega como um **fator** (1,05 = afastar os dedos 5%), e a roda chega como
     * passos discretos. Passando o fator por aqui, os dois gestos caem na mesma conta —
     * e a sensação de zoom é a mesma nos dois.
     */
    fun stepsFor(factor: Float): Float {
        if (factor <= 0f || factor == 1f) return 0f
        return (Math.log(factor.toDouble()) / Math.log(ZOOM_STEP.toDouble())).toFloat()
    }

    /**
     * Deslocamento do modelo no **plano da figura** (os eixos X e Y do marcador), a partir de
     * um arrasto em pixels da tela.
     *
     * O sinal do eixo vertical foi **corrigido pelo teste em campo** (decisão 21): arrastar o
     * mouse para cima **traz** o modelo para a frente na figura, e arrastar para baixo o
     * afasta — a expectativa de quem arrasta. Vale registrar por que a primeira versão ficou
     * ao contrário: eu justifiquei o sinal pelo eixo da tela (que cresce para baixo) contra o
     * Y do marcador (que cresce para cima) e escolhi o lado errado da mesma conta. Como o
     * arrasto horizontal já estava certo, a correção ficou isolada no sinal do Y.
     *
     * @param current deslocamento atual, em metros.
     * @param dxPixels arrasto horizontal (pixels; positivo = para a direita).
     * @param dyPixels arrasto vertical (pixels; positivo = para baixo, como vem do sistema).
     * @param sizeMeters tamanho atual do modelo — define a escala do movimento.
     */
    fun panOffset(
        current: Vec3,
        dxPixels: Float,
        dyPixels: Float,
        sizeMeters: Float,
    ): Vec3 {
        val metersPerPixel = sizeMeters.coerceAtLeast(RenderScene.MIN_SIZE_METERS) *
            PAN_FRACTION_PER_PIXEL

        return Vec3(
            x = (current.x + dxPixels * metersPerPixel).coerceIn(-MAX_PAN_METERS, MAX_PAN_METERS),
            y = (current.y + dyPixels * metersPerPixel).coerceIn(-MAX_PAN_METERS, MAX_PAN_METERS),
            z = current.z,
        )
    }
}

/** Potência de `Float` sem depender de `Double` no caminho quente. */
private fun Float.pow(exponent: Float): Float =
    Math.pow(this.toDouble(), exponent.toDouble()).toFloat()
