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
 * Vetor de três coordenadas em `Float`.
 *
 * É o equivalente desktop do `Position`/`Rotation`/`Scale` do SceneView (que são
 * `Float3` do Filament). A versão Windows precisa deste tipo próprio porque as
 * camadas portáveis — como o [ModelMetrics] — não podem depender do renderizador:
 * a mesma conta (`anchorPosition`) tem de funcionar tanto no backend Filament
 * quanto no backend de reserva em OpenGL, e também nos testes unitários, sem
 * carregar biblioteca nativa nenhuma.
 *
 * A conversão para o tipo do Filament é feita na fronteira do renderizador
 * (`render/FilamentRenderer.kt`, etapa 4).
 */
data class Vec3(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
    }
}
