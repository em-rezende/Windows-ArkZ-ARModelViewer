/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.model

/**
 * Métricas da bounding box do asset carregado, em **unidades do próprio arquivo**
 * (milímetros nos GLB exportados por CAD/SketchUp e por muitos conversores de OBJ,
 * metros em boa parte dos glTF modernos).
 *
 * Servem para duas coisas:
 *  1. normalizar o tamanho (`normalization` → a maior dimensão mede 1 m);
 *  2. apoiar o modelo no marcador (`anchorPosition`).
 *
 * **Por que não usar o "centralizar na origem" do renderizador?**
 * No SceneView 4.38 o `centerOrigin` é aplicado UMA única vez, no construtor do
 * nó, com a escala que ele tem naquele instante — que ainda é 1, porque a escala
 * declarativa do Compose só é aplicada depois. O deslocamento fica então gravado
 * em unidades CRUAS do arquivo: num modelo em milímetros com 10 m de lado isso
 * empurra o modelo centenas de metros para fora da cena — ele carrega, mas
 * **nunca aparece**.
 *
 * Fazendo a conta aqui, multiplicada pela escala ATUAL, o alinhamento acompanha o
 * slider de tamanho (e a pinça) e o modelo aparece exatamente sobre o marcador.
 * Esta é uma das decisões que a versão Windows mantém idêntica ao app Android.
 *
 * @property center Centro da bounding box (posição do pivô do asset).
 * @property halfExtent Metade da extensão da bounding box em cada eixo.
 */
class ModelMetrics(
    val center: Vec3,
    val halfExtent: Vec3,
) {
    /** Maior dimensão da bounding box, em unidades do arquivo. */
    val largestDimension: Float
        get() = maxOf(halfExtent.x, halfExtent.y, halfExtent.z) * 2f

    /** Fator que faz a MAIOR dimensão medir 1 m (unidades do arquivo → metros). */
    val normalization: Float
        get() = if (largestDimension > 0.0001f) 1f / largestDimension else 1f

    /**
     * Deslocamento do nó para ancorar o modelo no marcador.
     *
     * **O referencial do marcador é o do ARCore** — e é o que o `solvePnP` produz a partir das
     * quatro quinas montadas em `MarkerDetector.markerObjectPointsMat` (nelas o Y é **zero**):
     * **X = largura**, **Y = a NORMAL** (sai do papel, na direção de quem olha) e **Z = altura
     * NA imagem**. É o mesmo referencial dos nomes `extentX`/`extentZ` do `AugmentedImage`,
     * mantido de propósito para que a conta do app Android valha sem alteração.
     *
     * Com ele, o "para cima" do arquivo (**+Y**, a convenção do glTF) **já é a normal do
     * marcador** — e é por isso que não existe rotação de apoio: o modelo carrega de pé.
     *
     * Logo:
     *  - `x = -center.x * scale` → centraliza na largura da figura;
     *  - `y = -(center.y - halfExtent.y) * scale + elevationMeters` → apoia a **base** do
     *    modelo no plano (este é o eixo da normal) e soma a **elevação**, o único deslocamento
     *    que tira o modelo do plano;
     *  - `z = -center.z * scale` → centraliza na altura NA imagem.
     *
     * Tudo multiplicado pela escala atual, de modo que o alinhamento acompanha o slider de
     * tamanho e a escala automática.
     *
     * > **Histórico (decisão 34 do roadmap).** A primeira versão desta função trocou Y e Z,
     * > acreditando que o plano do marcador fosse XY. O defeito apareceu com o marcador na mão:
     * > girando a folha pela normal, o "para cima" do modelo (que caía dentro do plano) girava
     * > em torno do eixo errado — o modelo deitava. O referencial correto é o de cima, e
     * > `ModelPlacementTest` gira a folha e cobra que o modelo continue de pé.
     */
    fun anchorPosition(scale: Float, elevationMeters: Float): Vec3 = Vec3(
        x = -center.x * scale,
        y = -(center.y - halfExtent.y) * scale + elevationMeters,
        z = -center.z * scale,
    )
}
