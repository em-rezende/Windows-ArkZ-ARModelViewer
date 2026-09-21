# Paridade de idiomas (Android → Windows)

A versão Windows **reaproveita os textos do app Android**, inclusive as traduções.
Nada foi redigitado à mão: um pipeline de scripts converte os `strings.xml` do
Android nos bundles `.properties` desta versão.

## O pipeline

| Passo | Script | O que faz |
|---|---|---|
| 1 | `tools/convert-strings.ps1` | Lê `app/src/main/res/values*/strings.xml` do app Android e gera `src/main/resources/i18n/strings*.properties` (decodifica os escapes do Android, reescapa para `.properties`, grava UTF-8 **sem BOM**). |
| 2 | `tools/apply-i18n-overrides.ps1` | Aplica `tools/i18n-overrides/_parity.properties` (em todos os bundles) e `tools/i18n-overrides/<bundle>.properties` (naquele idioma). |
| — | `tools/set-utf8-bom.ps1` | Grava BOM UTF-8 nos `.ps1`: o Windows PowerShell 5.1 lê script sem BOM como ANSI, e um literal acentuado **dentro do script** sairia corrompido no arquivo gerado (foi o bug que gerou os primeiros bundles). |

Os dois primeiros são idempotentes: rodar de novo produz o mesmo arquivo. O teste
`I18nBundlesTest` cobra o resultado (paridade de chaves, UTF-8 preservado e
marcadores de formatação `%1$s` idênticos).

## Bundles

| Idioma | Arquivo | Chaves |
|---|---|---|
| pt-BR (padrão, = `values/` do Android) | `strings.properties` | 116 |
| pt-PT | `strings_pt_PT.properties` | 116 |
| en | `strings_en.properties` | 116 |
| es / fr / de / it | `strings_es.properties`, `strings_fr.properties`, `strings_de.properties`, `strings_it.properties` | 116 |
| zh-CN | `strings_zh_CN.properties` | 116 |

Não existe `strings_pt_BR.properties`: o **bundle padrão já é o pt-BR**, e o pacote
é lido com `ResourceBundle.Control.getNoFallbackControl`, de modo que
`Locale("pt","BR")` resolve para o padrão — nunca para o idioma do sistema (era
exatamente o problema que o `AppLocale.kt` do Android documenta).

## Paridade de chaves (as 13 que faltavam no Android)

No app Android, `values/` e `values-pt-rBR/` têm **110** chaves, enquanto
`values-en`, `-es`, `-fr`, `-de`, `-it`, `-zh-rCN` e `-pt-rPT` têm **97**: as 13
abaixo caem no padrão (português) em qualquer outro idioma. Aqui elas existem em
todos os bundles (`tools/i18n-overrides/_parity.properties`), com o mesmo valor em
todos — são nomes próprios, endônimos e dados de contato:

`app_name`, `language_pt_br`, `language_pt_pt`, `language_en`, `language_es`,
`language_fr`, `language_de`, `language_it`, `language_zh`,
`about_developer_name`, `about_developer`, `about_site`, `about_email`.

## Chaves com texto diferente (as divergências de plataforma)

| Chave | Android | Windows | Por quê |
|---|---|---|---|
| `permission_title` | "Permissão de câmera necessária" | "Câmera não encontrada" | O Windows não pede permissão de câmera: o problema possível é não haver webcam ou ela estar ocupada (aplicativo de vídeo, tampa fechada, câmera desativada no sistema). |
| `permission_rationale` | explica o ARCore | explica a webcam/dispositivo indisponível | ARCore não existe no desktop. |
| `permission_denied_hint` | configurações do app | ligar/liberar a câmera no Windows | Não há tela de permissões de app a abrir. |
| `permission_grant` | "Permitir acesso à câmera" | "Procurar câmeras novamente" | A ação agora reenumerar os dispositivos. |
| `permission_open_settings` | "Abrir configurações do app" | "Escolher câmera…" | Existe escolha explícita de dispositivo. |
| `menu_rate_play` | "Avaliar no Google Play" | "Abrir site do desenvolvedor" | Não há Play Store no Windows. |
| `language_system` | "Sistema (idioma do aparelho)" | "Sistema (idioma do Windows)" | Troca de "aparelho" por "Windows". |
| `language_dialog_hint` | idem | idem | idem. |
| `model_dialog_hint` | explica o seletor do Android (sem filtro por MIME) | explica o diálogo nativo do Windows (filtro por extensão) | O motivo do texto original não existe aqui. |
| `about_description` | "(ARCore + SceneView/Filament)" | "Filament … OpenCV no próprio computador" | Descreve a stack real desta versão. |
| `capture_quality_hint` | "aparelhos com pouca folga" | "computadores com pouca memória" | idem. |
| `about_developer` | "Ark-Z Arquitetura Ltda." | "Copyright (C) 2026 Ark-Z Arquitetura Ltda" | A GPL-3.0 pede o aviso de direito autoral **na interface** de um programa interativo: no Android ele vem da ficha da Play Store; aqui tem de estar no diálogo Sobre. |
| `help_body` | Ajuda do Android (manda ver o `adb logcat` e cita a pinça de tela) | Ajuda desta versão (log em `%LOCALAPPDATA%`, zoom pela roda e por `Ctrl`+roda, botões `−`/`+`, pinça **de touchpad**) | Não existe `adb logcat` no Windows, e a pinça de tela não chega ao aplicativo (decisão 31 do roadmap). O texto corrigido ficou nos 8 bundles **e** nos 8 arquivos por idioma, para o pipeline continuar idempotente. |

## A chave nova desta versão: `about_license`

`about_license` (115 → **116** chaves) é o aviso de licença do diálogo Sobre —
"Distribuído sob a GNU General Public License v3.0" em cada idioma. É a **única** das
linhas de crédito que muda de idioma: as outras são nomes próprios e dados de contato e
vivem em `tools/i18n-overrides/_parity.properties`. Por isso esta está nos oito arquivos
por idioma deste diretório **e** nos oito bundles. Ao acrescentar uma chave, o teste
`I18nBundlesTest` cobra a paridade nos oito e o número declarado dentro do próprio teste.
| `feedback_body` | "Aparelho: %2$s" | "Computador: %2$s" | O diagnóstico reporta SO/Java, não fabricante/modelo. |
| `help_body` | menciona Logcat (`adb logcat -s ArkZARModelViewer`), "toque", "no aparelho" | menciona o log em `%LOCALAPPDATA%\ArkZ ARModelViewer\logs\app.log`, mouse/pinça, "neste computador" | Instruções específicas da plataforma. |

Todos esses textos estão reescritos nos 8 idiomas em `tools/i18n-overrides/`.
O teste `I18nBundlesTest` verifica que nenhum bundle ainda fala da Play Store ou do
ARCore e que o "Sobre" cita o Filament em todos os idiomas.

## Pendências conhecidas

* **`help_body` dos 8 idiomas**: o pt-BR e o en foram reescritos para o Windows; o
  `help_body` de **es, fr, de, it, pt-PT e zh-CN** continua sendo a tradução do
  Android (as instruções ainda válidas — os botões são os mesmos — mas com
  menções a Logcat/toque). Revisão de tradução pendente; o teste
  `a ajuda menciona o log do Windows e nao o Logcat` cobre apenas pt-BR/en de
  propósito, para não mascarar a pendência.
* **Nome da pasta de marcadores**: `Pictures/ArkZ ARModelViewer/Marcadores` mantém
  o nome em português em todos os idiomas, exatamente como o app Android (é
  constante de código, não texto de interface).
