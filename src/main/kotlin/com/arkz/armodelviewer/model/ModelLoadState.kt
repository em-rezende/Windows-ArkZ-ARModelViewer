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
 * Resultado do carregamento de um arquivo de modelo escolhido pelo usuário.
 *
 * Máquina de estados Idle → Loading → Loaded/Failed, exatamente como no app
 * Android. A diferença é o que `Loaded` carrega: lá era o `ModelInstance` do
 * Filament (recurso nativo, liberado com `destroyModel` numa `DisposableEffect`);
 * aqui o estado é **puro dado**, e o recurso do renderizador fica com quem o
 * criou (`render/`), o que permite testar a máquina de estados sem abrir GPU.
 */
sealed interface ModelLoadState {

    /** Nenhum arquivo escolhido ainda. */
    data object Idle : ModelLoadState

    /** Lendo/convertendo o arquivo (a UI mostra o indicador de progresso). */
    data object Loading : ModelLoadState

    /** Pronto para ser renderizado na cena. */
    data class Loaded(
        /** Quanto tempo o carregamento levou (diagnóstico na interface). */
        val durationMillis: Long,
        /**
         * Maior dimensão medida na bounding box do arquivo, em **unidades do
         * próprio arquivo** (0 quando o asset não informa dimensões).
         *
         * O glTF define metros, mas exportadores de CAD/SketchUp e a conversão de
         * OBJ gravam **milímetros** — por isso o número exibido na barra de status
         * pode ser "estranho" (ex.: 870 para um modelo de mesa). É exatamente esse
         * valor que explica um modelo que carrega e não aparece: o app normaliza a
         * maior dimensão para 1 m antes de aplicar o tamanho escolhido, então a
         * unidade do arquivo não afeta o resultado — a não ser que o deslocamento
         * de ancoragem seja calculado sem essa escala (veja [ModelMetrics]).
         */
        val largestDimensionUnits: Float,
    ) : ModelLoadState

    /** Falhou — a [message] é exibida ao usuário. */
    data class Failed(
        val message: String,
        val durationMillis: Long,
    ) : ModelLoadState
}

/**
 * Rede de segurança contra travamento real: só interrompe carregamentos
 * absurdamente longos (5 minutos).
 *
 * Um timeout curto é prejudicial: modelos de CAD com milhares de nós são abertos
 * pelo Filament na thread principal e levam dezenas de segundos — cancelar cedo
 * faria o usuário ver "falha" num arquivo que só era grande.
 */
const val LOAD_WATCHDOG_MILLIS: Long = 5L * 60L * 1_000L
