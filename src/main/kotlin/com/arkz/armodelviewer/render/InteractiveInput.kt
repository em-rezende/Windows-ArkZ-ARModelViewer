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
 * para pôr o modelo ao lado da figura ou à frente dela, sem o risco de perdê-lo de vista.
 *
 * ## O arrasto anda na largura (X) e no frente–trás (Y)
 *
 * O arrasto do "modo livre" anda nos dois eixos que o usuário vê como "mover o modelo": a
 * **largura da figura** (o X do marcador, que é o eixo horizontal da imagem) e a **normal do
 * marcador** (o Y) — que é o **frente–trás**, o eixo que sai do papel na direção de quem olha.
 *
 * O eixo **Z** — a "altura NA imagem" — **não** entra no arrasto. Era ele que fazia o arrasto
 * vertical subir e descer o modelo pelo mundo: com o marcador de frente para o usuário (o caso
 * do relato em campo) o Z da figura **é** o eixo vertical do mundo, então escrever ali movia o
 * modelo para cima e para baixo em vez de para a frente e para trás.
 *
 * **A medição que fixou isso** (o quadro é sintetizado com a figura real e a pose vem do
 * detector de verdade — veja `MarkerDetectorTest` e `InteractiveInputTest`): com o "Marcador A"
 * de frente para a câmera, a 21 cm, a pose entrega **X = largura**, **Y = a NORMAL** e
 * **Z = altura NA imagem** — e nesta pose o Z da figura aponta para o **chão do mundo**. Um
 * arrasto vertical de 0,1 m escrito no Z andava **92 mm na vertical do mundo** e só 38 mm em
 * profundidade: era este o "o modelo sobe e desce" do relato. Escrito no Y, o mesmo arrasto anda
 * **92 mm frente–trás** (o modelo se aproxima da câmera) e 38 mm na vertical — o pedido.
 *
 * O sinal é o da tela: **arrastar para baixo traz o modelo para a frente** (na direção de quem
 * olha) e arrastar para cima o afasta — o mesmo sentido do cursor de **Elevação**, que também
 * desloca no Y. O deslocamento continua no referencial do marcador e **não** gira com os
 * cursores de rotação: quem arrasta o modelo continua movendo-o sobre a figura impressa.
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

    /** Limite do deslocamento do arrasto, em metros — na largura (X) e no frente–trás (Y). */
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
     * Deslocamento do modelo a partir de um arrasto em pixels da tela.
     *
     * O arrasto anda em **dois eixos do marcador** (o referencial do ARCore, o mesmo do
     * `ModelMetrics.anchorPosition` e o que o `solvePnP` produz):
     *
     *  * **X — a largura da figura** (o eixo horizontal da imagem): arrastar para a direita
     *    leva o modelo para a direita;
     *  * **Y — a NORMAL**, que é o **frente–trás**: arrastar para baixo **traz** o modelo para a
     *    frente (na direção de quem olha) e para cima o afasta. É o eixo do cursor de
     *    **Elevação**, com o mesmo sentido.
     *
     * O **Z (a "altura NA imagem") fica em zero**: era ele que fazia o arrasto vertical subir e
     * descer o modelo pelo mundo quando a figura está de frente para o usuário — a medição e a
     * decisão 36 estão no KDoc deste objeto.
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
            z = 0f,
        )
    }
}

/** Potência de `Float` sem depender de `Double` no caminho quente. */
private fun Float.pow(exponent: Float): Float =
    Math.pow(this.toDouble(), exponent.toDouble()).toFloat()
