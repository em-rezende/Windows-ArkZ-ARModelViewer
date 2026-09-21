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

package com.arkz.armodelviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkz.armodelviewer.tools.ModelDiagnostics
import com.arkz.armodelviewer.ui.AppLocaleProvider
import com.arkz.armodelviewer.ui.ArSceneSection
import com.arkz.armodelviewer.util.AppDirectories
import com.arkz.armodelviewer.util.AppLog
import com.arkz.armodelviewer.util.AppPreferences
import com.arkz.armodelviewer.util.appName
import com.arkz.armodelviewer.util.appVersionLabel
import kotlin.system.exitProcess

/**
 * Ponto de entrada do ArkZ ARModelViewer (Windows).
 *
 * Espelha a `MainActivity` do app Android no que importa para o usuário: uma única
 * janela, tema escuro (o mesmo `darkColorScheme` da Activity), o idioma escolhido
 * aplicado a toda a interface e a tela de Realidade Aumentada ocupando tudo.
 *
 * Diferenças de plataforma: não há pedido de permissão de câmera (o Windows não
 * exige) nem `ARCoreApk.install`. A câmera é aberta direto; a ausência de webcam —
 * ou uma webcam ocupada por outro programa — vira uma tela de estado com a
 * explicação (veja `ui/CameraSection.kt`).
 *
 * O **diagnóstico pela linha de comando** (`--check-model`) vem antes de tudo: ele precisa
 * rodar sem abrir a janela, para investigar um problema na máquina do usuário
 * (`tools/ModelDiagnostics.kt`).
 */
fun main(args: Array<String>) {
    ModelDiagnostics.handle(args)?.let { codigo -> exitProcess(codigo) }

    openWindow()
}

private fun openWindow() = application {
    AppLog.start()
    // Registrado no log porque "onde foi parar a captura?" é a primeira pergunta quando
    // alguém não encontra a imagem — e a pasta de imagens do Windows pode estar em outro
    // disco (como nesta máquina, em D:\Imagens).
    AppLog.info("Pasta de dados: ${AppDirectories.root}")
    AppLog.info("Pasta de imagens: ${AppDirectories.pictures}")

    // O ícone é lido uma vez, no início: o arquivo não muda enquanto o aplicativo está aberto.
    val windowPainter = remember {
        runCatching { loadWindowIcon() }
            .onFailure { error -> AppLog.warn("Ícone da janela não carregou: ${error.message}") }
            .getOrNull()
    }

    // O mesmo ícone como imagem do AWT, para **reaplicá-lo** quando a janela é redecorada (veja
    // `loadWindowIconAwt`).
    val windowIconAwt = remember {
        runCatching { loadWindowIconAwt() }
            .onFailure { error -> AppLog.warn("Ícone (AWT) não carregou: ${error.message}") }
            .getOrNull()
    }

    val windowState = rememberWindowState(width = 1100.dp, height = 900.dp)
    var fullscreen by remember { mutableStateOf(false) }

    // Entra/sai do modo tela cheia: a janela passa a ocupar a tela toda, sem as bordas do
    // sistema, e a tela de RA esconde menus, rodapé e a fileira de botões (que passa a
    // sobrepor a imagem, com fade).
    fun toggleFullscreen() {
        fullscreen = !fullscreen
        windowState.placement = if (fullscreen) {
            WindowPlacement.Fullscreen
        } else {
            WindowPlacement.Floating
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "${appName()} — ${appVersionLabel()}",
        state = windowState,
        icon = windowPainter,
        // `Esc` sai da tela cheia — a tecla que o usuário já espera. O tratamento vive aqui, na
        // janela, e não só na área do vídeo: assim funciona mesmo quando o foco está num botão
        // (a área do vídeo consome a tecla quando é ela que tem o foco, e aí esta não dispara).
        onKeyEvent = { event ->
            if (event.key == Key.Escape && fullscreen) {
                toggleFullscreen()
                true
            } else {
                false
            }
        },
    ) {
        // O ícone é reaplicado a cada mudança de modo da janela (tela cheia ↔ janela): o Windows
        // reconstrói as decorações e **descarta o ícone** no caminho. Foi o defeito relatado
        // ("após carregar a tela cheia e voltar, o ícone da barra de título desaparece").
        LaunchedEffect(windowState.placement) {
            windowIconAwt?.let { image -> window.iconImage = image }
        }

        // O "Sair" da barra de RA fecha a aplicação pelo mesmo caminho do X da janela: o
        // `exitApplication` pertence a este escopo, e é daqui que ele desce até a tela.
        App(
            onExit = { exitApplication() },
            // Maximizada, a janela tem espaço de sobra: o rodapé de estado sai de cena e o
            // vídeo fica com a altura toda (pedido do teste em campo).
            showStatus = windowState.placement != WindowPlacement.Maximized,
            fullscreen = fullscreen,
            onToggleFullscreen = { toggleFullscreen() },
        )
    }
}

@Composable
private fun App(onExit: () -> Unit, showStatus: Boolean, fullscreen: Boolean, onToggleFullscreen: () -> Unit) {
    // O idioma vive aqui, como no Android vivia na Activity: a troca no menu
    // reescreve a composição inteira sem recriar a câmera nem a cena.
    val preferences = remember { AppPreferences() }
    var languageTag by remember { mutableStateOf(preferences.languageTag) }

    AppLocaleProvider(languageTag) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            // A tela de RA é a tela principal: barra de menus no topo, o vídeo ocupando o
            // espaço que sobra e o rodapé de estado embaixo.
            //
            // Isto também resolveu um defeito: antes esta tela ficava dentro da coluna
            // rolável da tela de verificação, e a roda do mouse (com `Ctrl`) era consumida
            // por ela — a página rolava em vez de o modelo crescer.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WindowBackground)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ArSceneSection(
                    languageTag = languageTag,
                    onLanguageChange = { tag ->
                        languageTag = tag
                        preferences.languageTag = tag
                    },
                    // O "Sair" do menu chega de cima, do escopo da aplicação.
                    onExit = onExit,
                    showStatus = showStatus,
                    fullscreen = fullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Fundo da janela (o mesmo tom escuro do tema, sem depender do `Surface`). */
private val WindowBackground = Color(0xFF12141A)

/**
 * O ícone da janela — o mesmo que o instalador da etapa 8 vai usar.
 *
 * O arquivo vive em `src/main/resources/icons/`, e **não** em `build/resources/main/icons/`: a
 * pasta dentro de `build` é saída do Gradle, então um `clean` apaga o ícone e ele também não
 * entra no jar. (Foi de lá que o arquivo foi trazido para o projeto.)
 *
 * A leitura **não** usa `painterResource`: o compilador do Compose proíbe chamada `@Composable`
 * dentro de `runCatching`, e sem essa proteção um ícone que não decodifica impediria o
 * aplicativo de abrir. Lendo o arquivo do classpath e decodificando com o Skia (que já é usado
 * no desenho do quadro da câmera), a leitura é uma função comum, e a falha vira uma linha no
 * log em vez de uma janela que não abre.
 */
private fun loadWindowIcon(): Painter? =
    loadWindowIconImage()?.let { image -> BitmapPainter(image.toComposeImageBitmap()) }

/**
 * O mesmo ícone, como imagem do AWT — para **reaplicá-lo** quando a janela é redecorada.
 *
 * A mudança de modo (tela cheia ↔ janela) faz o Windows reconstruir as decorações da janela, e o
 * ícone se perde no caminho: o defeito apareceu no teste em campo ("após carregar a tela cheia e
 * voltar, o ícone da barra de título desaparece"). Passar o ícone de novo ao Compose não resolve,
 * porque para ele nada mudou; o caminho é escrever direto no `Frame` do AWT
 * (`window.iconImage`), que é de onde o sistema lê o ícone.
 */
private fun loadWindowIconAwt(): java.awt.Image? =
    loadWindowIconImage()?.toComposeImageBitmap()?.toAwtImage()

/** Bytes do ícone → imagem do Skia (a decodificação do `.ico`). */
private fun loadWindowIconImage(): org.jetbrains.skia.Image? {
    val bytes = AppDirectories::class.java.classLoader
        .getResourceAsStream(ICON_RESOURCE)
        ?.use { stream -> stream.readBytes() }
        ?: return null

    return org.jetbrains.skia.Image.makeFromEncoded(bytes)
}

/** Caminho do ícone dentro dos recursos do projeto. */
private const val ICON_RESOURCE = "icons/Ark-Z_Logo.ico"
