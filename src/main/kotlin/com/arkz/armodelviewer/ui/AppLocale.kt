/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale
import java.util.ResourceBundle

/**
 * Textos do idioma escolhido no menu ⋮ → **Idioma**.
 *
 * Substitui o `stringResource(R.string.…)` do Android por um acesso direto ao
 * bundle: `strings["action_reset"]` ou, quando o texto tem argumentos,
 * `strings.format("status_model_loaded", nome, segundos, dimensao)`. Os textos são
 * os mesmos arquivos gerados a partir dos `strings.xml` do app Android (veja
 * `docs/i18n-parity.md`), com a mesma sintaxe de argumentos (`%1$s`, `%2$d`).
 */
@Immutable
class Strings internal constructor(
    private val bundle: ResourceBundle,
    /** Idioma efetivamente usado (o do sistema quando nenhum foi forçado). */
    val locale: Locale,
) {
    /** Texto de [key]. Uma chave ausente é um erro de programação, não de runtime. */
    operator fun get(key: String): String = bundle.getString(key)

    /** Texto de [key] com os argumentos posicionais (`%1$s`, `%2$d`, …). */
    fun format(key: String, vararg args: Any?): String =
        String.format(locale, bundle.getString(key), *args)

    /** Chaves disponíveis (usado pelos testes de paridade). */
    val keys: Set<String> get() = bundle.keys.toList().toSet()
}

/**
 * Bundle **sem fallback para o idioma do sistema**.
 *
 * Sem `getNoFallbackControl`, um idioma sem tradução cairia no bundle do idioma do
 * sistema (que pode ser qualquer coisa) em vez do bundle padrão do projeto — que é
 * o pt-BR, exatamente como o `values/` do Android. Foi essa troca de fallback que
 * fez o app Android aparecer em português de Portugal num aparelho brasileiro.
 */
private val bundleControl: ResourceBundle.Control =
    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)

/**
 * Resolve os textos de [languageTag] (`null`/vazio = idioma do sistema).
 *
 * Função pura, sem Compose, para poder ser testada: é ela que decide o idioma.
 */
fun stringsFor(languageTag: String?): Strings {
    val locale = languageTag
        ?.takeIf { it.isNotBlank() }
        ?.let(Locale::forLanguageTag)
        ?: Locale.getDefault()
    return Strings(ResourceBundle.getBundle("i18n/strings", locale, bundleControl), locale)
}

/**
 * Uma opção do diálogo "Idioma".
 *
 * @property tag código BCP-47 (`pt-BR`, `zh-CN`…) ou `null` para o sistema.
 * @property labelKey chave do rótulo exibido — em geral o **endônimo** (o nome do
 *   idioma no próprio idioma), para o usuário reconhecer a opção sem tradução.
 */
data class AppLanguage(val tag: String?, val labelKey: String)

/**
 * Idiomas oferecidos. A ordem é a das traduções disponíveis; manter esta lista e as
 * chaves `language_*` dos bundles em sincronia é o único cuidado necessário para
 * acrescentar um idioma novo.
 */
val appLanguages: List<AppLanguage> = listOf(
    AppLanguage(tag = null, labelKey = "language_system"),
    AppLanguage(tag = "pt-BR", labelKey = "language_pt_br"),
    AppLanguage(tag = "pt-PT", labelKey = "language_pt_pt"),
    AppLanguage(tag = "en", labelKey = "language_en"),
    AppLanguage(tag = "es", labelKey = "language_es"),
    AppLanguage(tag = "fr", labelKey = "language_fr"),
    AppLanguage(tag = "de", labelKey = "language_de"),
    AppLanguage(tag = "it", labelKey = "language_it"),
    AppLanguage(tag = "zh-CN", labelKey = "language_zh"),
)

/** Chave do rótulo do idioma escolhido (o endônimo, ou o texto de "Sistema"). */
fun appLanguageLabelKey(languageTag: String?): String =
    appLanguages.firstOrNull { it.tag == languageTag }?.labelKey ?: "language_system"

/**
 * Fornece os textos do idioma escolhido a todo o conteúdo Compose.
 *
 * A troca de idioma **não descarta a subárvore**: o provider está sempre presente e
 * só o objeto `Strings` muda (`remember(languageTag)`), então a câmera, o
 * renderizador e o modelo carregado continuam onde estavam — o mesmo cuidado que o
 * `AppLocaleProvider` do Android documenta para não recriar a sessão de RA.
 */
@Composable
fun AppLocaleProvider(languageTag: String?, content: @Composable () -> Unit) {
    val strings = remember(languageTag) { stringsFor(languageTag) }
    CompositionLocalProvider(LocalStrings provides strings) {
        content()
    }
}

/**
 * Textos do idioma atual.
 *
 * `staticCompositionLocalOf` (e não `compositionLocalOf`) porque o valor muda
 * apenas quando o usuário troca de idioma — nesse caso recompor tudo é o
 * comportamento desejado, e a leitura fica mais barata em cada quadro.
 */
val LocalStrings = staticCompositionLocalOf<Strings> {
    error("Nenhum AppLocaleProvider acima deste ponto da composição.")
}
