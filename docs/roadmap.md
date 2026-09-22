# Roadmap — ArkZ ARModelViewer Desktop

Port da versão Android (`https://github.com/em-rezende/Android-ArkZ-ARModelViewer`)
para Windows 10/11, com **fidelidade comportamental**: o usuário deve reconhecer o
mesmo aplicativo — as mesmas ações, os mesmos textos, os mesmos números.

> **Concluídas e validadas: etapas 0 a 9.** As etapas 0 a 4 entregaram o esqueleto, a
> lógica portável + i18n, a câmera, a detecção de marcador, a ancoragem do modelo com a
> projeção derivada das intrínsecas da detecção, a leitura/conversão dos seis formatos e o
> **backend Filament desenhando a cena fora da tela**. A **etapa 5** trouxe a interface
> (barra de menus com as seis áreas, ajustes em tempo real num diálogo arrastável, gerenciar
> marcador e escala automática) e a **etapa 6**, os gestos e o zoom (roda, `Ctrl`+roda,
> teclado, botões `−`/`+`, pinça de touchpad e arrasto com o mouse). As duas foram
> **conferidas em uso real, no notebook do cliente**: vídeo da webcam como fundo, caixa ciano
> sobre o marcador reconhecido, **modelo 3D carregado pelo diálogo nativo aparecendo
> ancorado**, zoom e arrasto respondendo, e as ações conferidas uma a uma. A **etapa 9**
> fechou a documentação, o `CHANGELOG.md`, o `NOTICE` (licença e créditos na interface e no
> pacote), o `.gitattributes` e a publicação deste repositório. **210 testes unitários
> verdes**, dos quais **três desenham quadros de verdade na GPU**. As **etapas 7 (captura de tela
> sem interface, com as três qualidades) e 8 (instalador `.msi`)** também estão fechadas: a captura
> usa a mesma função de desenho num alvo maior, e o `.msi` (jpackage + WiX, que o Gradle baixa sozinho) traz atalho no menu
> Iniciar, desinstalador e `upgradeUuid` fixo — o pacote portátil continua sendo o caminho para
> levar o aplicativo a outro computador **sem instalar nada**.

| Etapa | Entrega | Como é validada |
|---|---|---|
| **0** ✅ | Esqueleto Gradle (Kotlin DSL + `libs.versions.toml`), wrapper 9.7.1, `.gitignore`, `LICENSE` (GPL-3.0), `NOTICE`, assets (marcadores e modelos de teste) | `gradlew.bat build` verde e a janela abrindo |
| **1** ✅ | Lógica 100% portável: `MarkerCatalog`, `MarkerGenerator`, `CustomMarkerStore`, `ModelLoadState`, `ModelMetrics`, `ModelFileStaging`, `MarkerPrintSheet`, `GallerySaver`, `AppPreferences`, `AppDirectories`, `ScreenCapture`, `AppLinks`, i18n em 8 idiomas (112 chaves com paridade completa) | 80 testes unitários (JUnit 5): determinismo do gerador, `anchorPosition`, paridade das chaves de i18n, folha de impressão, preferências, cópias de modelos |
| **2** ✅ | Câmera: `opencv_videoio.VideoCapture` (MSMF → DirectShow → padrão), thread de captura própria, enumeração e escolha de dispositivo, vídeo ao vivo na janela | `gradlew.bat test` + `gradlew.bat -q listCameras` (1 câmera, índice 0) + vídeo na janela |
| **3** ✅ | Detecção de marcador: ORB + `BFMatcher` (Hamming) + homografia RANSAC + `solvePnP` (IPPE), com a interface equivalente ao `AugmentedImage` (id, name, pose, extentX, extentZ, trackingState); caixa ciano sobre o marcador | 11 testes com quadro **sintetizado** (homografia conhecida): cantos, pose, distância real de uso (30–60 cm), sem falso positivo, `TRACKING → PAUSED → fora da cena` e falhas nativas tratadas — veja `docs/marker-detection.md` |
| **4** ✅ | Renderização: Filament (filament-kmp) com `SceneRenderer` (interface) e backend alternativo LWJGL/OpenGL; importação por diálogo nativo com validação por extensão; Assimp converte `.obj/.stl/.ply/.3mf` para GLB. **Feito até aqui:** `render/ModelPlacement.kt` — a matriz que leva a geometria do arquivo ao referencial do mundo (pose do marcador ∘ [ancoragem · rotação · escala]), 7 testes; `render/AssimpModelLoader.kt` — leitura dos seis formatos, caixa envolvente em unidades do arquivo, conversão para GLB ao lado do original e mensagens de erro tratáveis, 8 testes com os modelos do repositório; `render/SceneRenderer.kt` — a descrição de cena (`RenderScene`: fundo, marcador, modelo, ajustes) e o contrato do renderizador, com desenho **fora da tela** + leitura de pixels, 8 testes; `render/FilamentSpikeTest` — **o motor gráfico do Filament inicializa e desenha um quadro offscreen de 256×256 nesta máquina** (a decisão 2 deixou de ser suposição). **Feito também:** `render/FilamentRenderer.kt` — o backend Filament completo (cena, câmera com a projeção das intrínsecas da detecção, duas luzes direcionais, plano de fundo com o vídeo da câmera e o modelo pelo `gltfio`), com 3 testes que **desenham na GPU** e conferem os pixels. **E a ligação com a janela:** `SceneComposer` (laço de desenho numa thread própria), `ArSceneSection` (tela, botão de carregar e painel de ajustes), `ModelPicker` (diálogo nativo) e `SceneImageCache` (conversões de imagem) — 8 testes. | Os seis formatos (`.glb`, `.gltf`, `.obj`, `.stl`, `.ply`, `.3mf`) ancorados no marcador, com a mesma ancoragem do Android. Já validado: a conta de ancoragem (base no plano, elevação na normal, rotação no referencial do marcador), a conversão de cada formato (cabeçalho GLB conferido byte a byte) e a inicialização do motor gráfico e a **compilação do material de fundo em tempo de execução** (`MaterialSpikeTest`); e **validado na janela, em hardware real**: vídeo da webcam como fundo, caixa ciano sobre o marcador reconhecido e **modelo 3D carregado pelo diálogo do Windows aparecendo ancorado** |
| **5** 🟡 | Interface completa, **organizada em menus** (pedido do usuário, para separar assuntos): **Idioma** (a lista dos oito idiomas), **Modelo** (Carregar modelo · Ajustes do modelo · Escala automática · Redefinir configurações), **Câmera** (Reiniciar · Capturar tela), **Escolher marcador** (Gerenciar · Criar · Carregar marcador), **Ajuda** (Ajuda · Sobre · Qualidade da captura · Folha de impressão · Copiar diagnóstico) e **Sair**; mais o **rodapé de estado** com três linhas: programa/ambiente (versão, sistema, idioma, renderizador), modelo carregado (e a última mensagem) e marcador escolhido (com "●" quando a câmera está lendo a figura). **Feito:** `ui/ArMenuBar.kt` (os seis menus e os itens como dados), `ui/ArDialogs.kt` (ajuda, sobre, qualidade, **gerenciar marcador** e **criar marcador**), `ui/ArSceneSection.kt` (a tela, com o compositor como fonte única dos ajustes e o rodapé), `render/MarkerSelection.kt` (5 testes) e `render/AutoScale.kt` (4 testes). A tela de RA passou a ser a **tela principal** (a antiga tela de verificação das etapas 0–2 saiu, como o roadmap previa). **Conferido na janela:** Idioma, Reiniciar, Ajustes do modelo, Escala automática, Sair, o arrasto do modelo e o redimensionamento. **Falta:** conferir "Escolher marcador" (com duas figuras à mão), "Capturar tela" e o diálogo de ajustes; o zoom por `Ctrl`+roda **já foi confirmado na janela**. **Correções do teste em campo (decisões 21 e 22):** o painel de ajustes virou **diálogo** (aberto abaixo do vídeo, ele empurrava o modelo para fora da janela), a barra ficou na ordem **Modelo, Câmera, Escolher marcador, Idioma, Ajuda, Sair**, e entraram os botões **Ajustes do modelo**, **Escala automática** e **Capturar tela** ao lado de "Carregar modelo" | Reprodução de todos os fluxos do app Android |
| **6** ✅ | Interações: pinça em tela sensível ao toque, pinça em touchpad de precisão, `Ctrl`+roda, `+`/`-`, arrastar com o mouse (modo livre). **Feito:** `render/InteractiveInput.kt` (a conversão de gesto em tamanho e deslocamento, 11 testes: zoom multiplicativo de 10% por passo com a faixa inteira em ~49 passos, o fator da pinça e os passos da roda dando o mesmo resultado, arrasto proporcional ao tamanho do modelo, sinal do eixo vertical e limite de 50 cm), o painel de `RenderScene`/`ModelPlacement` com o **deslocamento do arrasto** — a largura da figura e o frente–trás (decisão 36) — e a montagem na área do vídeo: `Ctrl`+roda, `+`/`=`/`-`, pinça de toque e arrasto. **O ponto de plataforma:** no desktop a pinça de touchpad de precisão **chega como `Ctrl`+roda** — os dois gestos são o mesmo caminho de código. **Conferido na janela:** `Ctrl`+roda (o zoom funciona) e o arrasto com o mouse — com **uma correção**: o sentido vertical estava invertido ("para a frente" levava o modelo "para trás") e foi corrigido pelo teste em campo, com o teste do sentido atualizado (decisão 21); o arrasto foi reconferido em campo outras duas vezes, e nesta última o **eixo** do movimento vertical foi corrigido (1.0.6, decisão 36). A pinça de touchpad **não é verificável nesta máquina** (sem touchpad e sem tela de toque) — e a **pinça de dois dedos na tela sensível ao toque não funciona**, nem tem como funcionar nesta plataforma: a entrada do Compose Desktop no Windows não recebe toque múltiplo (decisão 31, com a medição que comprova). Em lugar dela, o zoom por toque ganhou os botões `−`/`+` na fileira de comandos, além do cursor de tamanho nos ajustes e da escala automática. | Zoom entre 0,02 m e 2,0 m nos dois tipos de entrada |
| **7** ✅ | Captura de tela em `Pictures/ArkZ ARModelViewer/` **sem a interface** (render offscreen), com as três qualidades (1280, 1920, máxima) e som de disparo | PNG sem interface, com câmera + modelo — gravado nas máquinas de teste |
| **8** ✅ | Instalador `.msi` (jpackage + WiX, que o Gradle baixa sozinho), ícone, atalho e menu Iniciar, com `upgradeUuid` fixo | `gradlew.bat packageMsi` gera o `.msi` em `build/compose/binaries/main/msi/`; a instalação em pasta limpa e a execução pelo menu Iniciar são conferidas na máquina de destino. **Pendência registrada:** o `.msi` não leva as DLLs do Visual C++ que o portátil leva ao lado do `.exe` (medido: 227 arquivos contra 230) — o conserto é `appResourcesRootDir` mais `AddDllDirectory` na abertura do app |
| **9** ✅ | Documentação (README, `docs/`), scripts de apoio em `tools/`, notas de release (`CHANGELOG.md`), `NOTICE` com a licença e os créditos, `.gitattributes` e a publicação no GitHub | Revisão final e teste com webcam |

## Decisões já tomadas (e o porquê)

1. **SceneView não é usável no desktop.** No Maven Central só existem AARs Android
   (`io.github.sceneview:sceneview` / `arsceneview`). Existe
   `io.github.sceneview:sceneview-compose-desktop`, mas o próprio projeto o
   descreve como *"viewer subset only — No AR, no custom materials, no
   post-processing"*, e a página oficial de plataformas chama o Desktop de
   *"Placeholder"*. Portanto: **reaproveitamos as convenções e a matemática do
   SceneView, não o código**.
2. **Filament no desktop vem do binding comunitário** `io.github.erkko68.filament`
   (Apache-2.0, FFM/Panama, JDK 22+). O Google removeu o suporte Java/desktop em
   2021 (`google/filament#4263`) e fechou o pedido de KMP desktop como *not
   planned* (`google/filament#7558`).
3. **JDK 22+** (o projeto usa o toolchain 25) porque FFM exige Java 22. É a única
   exigência de JDK acima de 21 do projeto.
4. **Detecção de marcador própria** (OpenCV), com a **mesma interface** do
   `AugmentedImage` do ARCore, para que a lógica da tela de RA seja portada quase
   sem alteração.
5. **Os formatos não-glTF passam pelo Assimp** (binding oficial no LWJGL) para
   virar GLB, exatamente como o SceneView Android faz em Kotlin puro. A leitura usa
   `aiProcess_PreTransformVertices` (as transformações dos nós já vêm aplicadas nos
   vértices, o que torna a caixa envolvente correta sem compor matrizes) **e**
   `aiProcess_GenBoundingBoxes` — sem esta última a caixa vem zerada, e o sintoma é o
   pior possível: o modelo carrega e não aparece. Verificado nos testes: com a flag a
   caixa tem a mesma medida nos três formatos do mesmo logo.
6. **Materiais são compilados em tempo de execução** pelo módulo `filamat` do próprio
   binding (`MaterialBuilder`). Motivo: os jars do binding **não trazem nenhum
   `.filamat`**, e o `matc` (compilador oficial) só existe como executável — depender
   dele significaria baixar o SDK do Filament no build. Com o `filamat` em processo, o
   material do plano de fundo (o vídeo da câmera) sai de algumas linhas de Kotlin, sem
   asset externo e sem etapa de build. Os *uber shaders* do `gltfio`
   (`UbershaderProvider`) vêm **embutidos na biblioteca nativa** — basta passar o
   `Engine` —, então a leitura de `.glb`/`.gltf` também não precisa de arquivo de apoio.
7. **O desenho é feito fora da tela, com leitura de pixels** (`SwapChain` offscreen →
   `readPixels` → imagem no canvas do Compose). Três razões: encaixe com o Compose (a
   tela já desenha o vídeo da câmera como imagem, e embutir janela nativa no Compose no
   Windows custa recorte e sincronia), a **captura de tela sem interface** da etapa 7
   passa a ser a mesma função num alvo maior, e o caminho inteiro — motor gráfico, cena e
   quadro — fica testável sem janela, como o `FilamentSpikeTest` demonstra. O custo é a
   cópia do quadro a cada desenho (≈3,7 MB em 1280×720), com buffer reaproveitado.
8. **A projeção da câmera sai das MESMAS intrínsecas da detecção** (`CameraProjection`), e
   não de um campo de visão aproximado. A pose do marcador veio de um `solvePnP` com
   aqueles números: projetar com outra abertura deslocaria o modelo do marcador, pouco no
   centro da imagem e muito nas bordas. O teste projeta pontos 3D pela matriz e confere
   contra `u = fx·X/Z + cx` — a igualdade com o modelo pinhole da detecção.
9. **O material compilado em tempo de execução é gerado para todas as plataformas e APIs**
   (`Platform.ALL` + `TargetApi.ALL`). O motor escolhe o backend sozinho (aqui, **Vulkan**)
   e o padrão do `filamat` é material de celular em OpenGL — só GLSL. Um material sem
   SPIR-V num motor Vulkan **aborta o processo** com uma asserção nativa
   (*"the material was not built for any of the Vulkan backend's supported shader languages
   (SPIR-V)"*), e não com uma exceção: o aplicativo morre sem mensagem amigável. Compilar
   para todos os alvos custa milissegundos e tira o aplicativo da dependência de um backend.
10. **O renderizável do plano de fundo declara a própria caixa envolvente** e a entidade é
    destruída **antes** dos recursos que ela usa. As duas regras vieram das asserções
    nativas que os testes de GPU encontraram: sem caixa explícita, o Filament aborta com
    *"AABB can't be empty"*; e destruir o material (ou o vértice, ou a textura) com o
    renderizável ainda vivo aborta com *"destroying MaterialInstance ... which is still in
    use by Renderable"*. Ambas seriam fatais em produção e nenhuma aparece em teste de
    lógica.
11. **As normais são geradas na conversão** (`aiProcess_GenNormals`). É a **segunda** armadilha
    do tipo "o modelo carrega e não aparece": o Filament **descarta as primitivas sem
    normais** na carga, sem erro nenhum. O teste que lê o JSON de dentro do GLB exportado
    é a rede que pega essa regressão.
12. **Todas as chamadas ao Filament acontecem numa thread única e dedicada** (`arkz-render`,
    dentro do `SceneComposer`). O motor **não** aceita chamadas concorrentes: com o desenho
    e os pedidos de tamanho/intrínsecos saindo do `Dispatchers.Default` (um conjunto de
    threads), duas tarefas caem em threads diferentes e chamam o motor ao mesmo tempo — e
    um `resize` (que destrói e recria o `SwapChain`) no meio de um `beginFrame` de outra
    thread **derruba o processo**, sem exceção, sem mensagem na tela e sem rastro além de
    um *dump* do Windows. Foi o primeiro defeito que só apareceu com a janela de verdade
    (os testes desenham num laço apertado, sem tarefas concorrentes): o log mostrava
    `Renderizador pronto` e, milissegundos depois, o processo morria — antes mesmo de o
    usuário tocar em qualquer botão. O nome da thread no log (`arkz-render`, em vez de
    `DefaultDispatcher-worker-N`) é a assinatura de que a serialização está valendo.
13. **Os ajustes do modelo têm um dono só** — o `SceneComposer`, que publica tamanho, rotação
    e elevação como `StateFlow`. O painel lê esses fluxos e escreve de volta neles, em vez de
    manter um estado próprio espelhado. É o que faz a **escala automática** mover o slider de
    tamanho (e não deixar o painel mostrando um valor enquanto a cena usa outro) e o que
    limita tamanho, escala automática e o que a cena aceita à mesma faixa (2 cm a 2 m).
14. **Texto de interface que não existia no Android**: quando um diálogo precisa de um botão
    de fechar e não há chave nos 112 textos herdados (mantidos com paridade exata entre os
    oito idiomas, verificada por teste), o rótulo é **"OK"** — a única palavra que se lê
    igual nos oito —, e não uma chave nova em um idioma só. Mesmo critério para o aviso
    sonoro da captura: o Windows não tem um som padrão de obturador, então o aplicativo usa
    o aviso sonoro do sistema, e o código diz por quê.
15. ~~**O referencial do marcador é: plano em XY e normal em Z.**~~ **ERRADA — o referencial
    correto é o do ARCore, e é o que a decisão 34 fechou com o projeto Android: X = largura,
    Y = a NORMAL, Z = altura NA imagem.** A primeira versão do port
    supôs o referencial de imagem do ARCore (normal no Y) e o defeito só apareceu na
    primeira sessão de uso com hardware, em dois sintomas que o usuário descreveu com
    precisão: os modelos chegavam **deitados** sobre a figura e o controle **Elevação Z**
    empurrava o modelo **de través**, dentro do plano. A pose que o `solvePnP` produz a
    partir dos quatro cantos tem o plano em XY — como o app Android sempre documentou. Além
    da ancoragem (base e elevação na normal), o `ModelPlacement` passou a aplicar uma
    **rotação de apoio** que põe em pé um modelo cujo "para cima" é o +Y (a convenção do
    glTF e da maioria dos CAD): o zero dos sliders passou a significar "em pé", e não foi
    mais preciso girar 90° em X à mão em cada modelo. Testes que codificavam a convenção
    antiga foram corrigidos junto — e um teste de GPU passou a distinguir "em pé" (o modelo
    aparece de fio, se for uma chapa) de "deitado" (a face enche a área central).
16. **A pasta de imagens é perguntada ao Windows, e não presumida** (`AppDirectories`). A
    primeira versão testava `%USERPROFILE%\Pictures` e, quando ele não existia, caía num plano
    B silencioso — foi o que aconteceu na primeira máquina, onde as capturas foram para a
    pasta do usuário e ninguém as achou. A segunda versão tentou adivinhar por
    `%OneDrive%\Pictures` — e estava errada de novo: nesta máquina a pasta "Imagens" foi
    **movida para outro disco** (`D:\Imagens`), então a pasta do OneDrive existia, mas não era
    a biblioteca de imagens. A resposta certa é a do próprio sistema: a chave
    `User Shell Folders` do registro (`My Pictures`), lida pelo `reg.exe`, com as variáveis
    expandidas e tempo limite. Se ela não servir, a ordem é `%USERPROFILE%\Pictures`,
    depois o **`Personal` do registro (Documentos)** e, por último,
    `%USERPROFILE%\Documents` — o usuário foi explícito: "não salve em OneDrive; se não der na
    Imagens, use Documentos". Cada candidata ainda passa por uma prova de **escrita de
    verdade** (criar e apagar um arquivo, porque o `canWrite()` do Java não enxerga recusa de
    permissão no Windows), e o caminho escolhido vai para o log a cada execução e a cada
    captura. Verificado nesta máquina: `Pasta de imagens: D:\Imagens\ArkZ ARModelViewer`.
17. **No desktop, "pinça no touchpad" e `Ctrl`+roda são o mesmo caminho de código.** O Windows
    não entrega um evento de pinça para aplicativos comuns: a pinça de um touchpad de precisão
    chega como **roda com `Ctrl` pressionado**. Implementar `Ctrl`+roda atende, portanto, os
    dois gestos de uma vez — e a pinça de tela sensível ao toque entra pelo gesto de
    transformação de dois toques, que é outro caminho (e o único impossível de verificar na
    máquina em que isto foi desenvolvido, por não ter touchpad nem tela de toque). O zoom é
    **multiplicativo** (10% por passo) para que a sensação seja a mesma de 2 cm a 2 m, e o
    fator da pinça é convertido em passos (`stepsFor`), de modo que os dois gestos cheguem ao
    mesmo tamanho — há teste para isso.
18. **O arrasto move o modelo no plano da figura** (`offsetMeters`, X e Y do marcador), e não
    em torno do próprio eixo: é o que faz sentido no "modo livre" — tirar o modelo do centro
    da figura e pô-lo ao lado, sobre a mesa. O deslocamento por pixel acompanha o tamanho do
    modelo (arrastar "uma tela" move a mesma proporção da tela, com 5 cm ou com 2 m), é
    limitado a 50 cm (o modelo não se perde de vista), não mexe na normal e não tira a base do
    plano — três propriedades com teste. **Revisto pela decisão 36 (1.0.6):** a pedido do teste
    em campo, o arrasto vertical passou a andar **na normal** — o frente–trás —, e o Z (a altura
    NA imagem) saiu do arrasto: era ele que, com a figura de frente para o usuário, fazia o
    modelo subir e descer. O arrasto continua limitado a 50 cm e continua sem girar com os
    cursores de rotação (agora com teste próprio). Observação de fidelidade: o clone de referência do
    projeto Android foi removido pelo Windows durante o desenvolvimento, então esta
    interpretação do "modo livre" está documentada como interpretação, e não como cópia
    verificada do comportamento original.
19. **Os menus são organizados por assunto** (decisão do usuário, na etapa 5): Idioma, Modelo,
    Câmera, Escolher marcador, Ajuda e Sair — com o rodapé de estado em três linhas
    (programa/ambiente, modelo e marcador). A versão anterior misturava tudo numa fila de
    botões mais um menu ⋮, em que "zoom" ficava ao lado de "exportar folha de impressão". Os
    itens dos menus são **dados** (`ArMenuItem`: rótulo + ação + estados), e cada menu guarda o
    próprio estado de aberto — abrir um fecha o outro sem nenhuma coordenação. A escolha de
    idioma deixou de ser diálogo: virou o próprio menu. Consequência estrutural: a tela de RA
    passou a ser a **tela principal**, e a antiga tela de verificação das etapas 0–2
    (`ui/StartupScreen.kt`) foi removida — suas informações úteis estão no rodapé, na lista de
    idiomas do menu e no diálogo de marcadores.
20. **A roda do mouse sobre o vídeo é consumida quando está com `Ctrl`** — e a área do vídeo
    saiu de dentro de uma coluna rolável. O defeito relatado ("`Ctrl`+roda rola a tela em vez
    de dar zoom") tinha duas causas possíveis, e o código agora **distingue as duas**: um
    registro no log, uma vez por execução, diz se o evento de roda chegou e se o `Ctrl` foi
    reconhecido. Na verificação feita durante o desenvolvimento o evento chegou com
    `ctrl=false` (roda sem `Ctrl`, rolando a página como deve); com `Ctrl` o zoom é aplicado e
    o evento é consumido, o que impede a página de rolar junto. Se algum dia o zoom não
    funcionar, o log responde em que lado está o problema — no modificador ou no evento.
21. **O teste em campo corrigiu o sentido do arrasto e transformou o painel de ajustes em
    diálogo.** Duas correções que só aparecem com o aplicativo na mão, e que valem registro
    pelo que ensinam:
    (a) **o arrasto vertical estava invertido** — arrastar "para a frente" levava o modelo
    "para trás". O arrasto horizontal estava certo, e a correção ficou isolada no sinal do Y
    (`InteractiveInput.panOffset`); o teste do sentido agora guarda a direção que o usuário
    confirmou, e não a que eu deduzi do eixo da tela. Lição para o roadmap: sinal de arrasto
    é coisa de teste em campo, não de raciocínio sobre eixos;
    (b) **o painel de ajustes era uma faixa abaixo do vídeo** e, ao abrir, empurrava a área do
    modelo para fora da janela — o usuário relatou "o menu principal sumiu". Virou **diálogo**
    (`AdjustModelDialog`), com os mesmos cinco deslizadores e o "Redefinir"; os ajustes
    continuam sendo aplicados em tempo real, porque o diálogo lê e escreve no `SceneComposer`
    (não guarda estado próprio). O estado do painel é o próprio `dialog`, e não um sinalizador
    separado. `Ctrl`+roda, aliás, foi confirmado na janela: o zoom funciona.
22. **A ordem dos menus e os botões ao lado do vídeo** (também pedido no teste em campo): a
    barra ficou **Modelo, Câmera, Escolher marcador, Idioma, Ajuda, Sair** — os assuntos do
    trabalho primeiro, o idioma da interface depois, e a ajuda e o sair no fim. O rótulo do
    menu de marcador continua sendo o texto do app Android, **"Escolher marcador"** (a chave
    `action_choose_marker`): encurtar para "Marcador" exigiria inventar uma tradução nova nos
    oito idiomas, e o texto existente já diz o que o menu faz.
    Abaixo do vídeo, ao lado de **"Carregar modelo 3D"**, entraram os três comandos do mesmo
    assunto — **"Ajustes do modelo (rotação e tamanho)"** (abre o diálogo), **"Escala
    automática (ajustar ao marcador)"** e **"Capturar tela"** — para quem já carregou o modelo
    não precisar abrir menu. Os rótulos são os mesmos dos itens de menu, com os parênteses que
    já existiam, e ficam desabilitados enquanto não há modelo carregado.
23. **Os diálogos viraram um componente próprio** (`ui/ArDialogCard.kt`), por quatro pedidos do
    teste em campo, todos aplicados a **todos** os diálogos do projeto:
    (a) **arrastável** — pela barra de título (o ponteiro vira uma mão, e o símbolo "⠿" indica
    a alça), porque o diálogo cobre a imagem da câmera e o usuário precisa vê-la enquanto mexe
    nos controles. O limite do arrasto é a função pura `clampDialogDrag`, com testes: o cartão
    sai do caminho mas nunca da janela;
    (b) **botões visíveis** — os botões eram `TextButton` sem borda nem fundo, e não se lia que
    eram botões sem passar o mouse. A convenção passou a ser `Button` (preenchido) para a ação
    principal e `OutlinedButton` (com borda) para as demais;
    (c) **véu leve** (18%) — escurecer a câmera demais anularia a razão de arrastar o diálogo;
    o cartão se distingue por borda ciano e sombra, e o véu **consome** os cliques (o modelo
    não se mexe atrás do diálogo aberto), mas **não** fecha o diálogo por clique acidental;
    (d) **miniatura do marcador** no diálogo de gerenciar (`Image` da própria
    `MarkerDefinition.image`, antes do nome): o nome pode repetir, a figura não.
    Detalhe de implementação: o cartão vive num `Popup` ancorado na janela, e não dentro da
    coluna da tela de RA — assim ele cobre também a barra de menus e o rodapé, e não depende de
    um `Box` que a tela teria de ter em volta.
24. **A fileira de botões quebra linha, em vez de rolar.** A tentativa anterior (rolagem
    horizontal) **não foi percebida** no teste em campo — e isso é a informação importante:
    ninguém adivinha que há botão escondido à direita, e um botão que não se vê é um botão que
    não existe. Com `FlowRow`, os quatro botões passam para a linha de baixo quando a janela é
    estreita, e todos continuam visíveis.
25. **Seis correções do teste em campo, todas em volta do mesmo assunto — legibilidade e
    sobreposição:**
    (a) **texto dos diálogos** — o cartão não declarava `contentColor`, e o Material caía no
    preto padrão: título preto sobre cinza escuro, e a Ajuda "quase impossível de ler". O
    aplicativo é escuro de propósito (a imagem da câmera é a superfície principal), então o
    conteúdo dos cartões é claro. **Não** é seguir o tema claro/escuro do Windows: a paleta do
    aplicativo é uma decisão própria, e mudá-la é outra tarefa;
    (b) **os botões do "Gerenciar marcador" saíram da lista que rola** — "Criar marcador" e
    "Carregar marcador" apareciam cortados na parte de baixo, e a rolagem num diálogo não é
    evidente, então ficavam inalcançáveis. Agora os três botões (Criar, Carregar e OK) ficam
    fixos no rodapé do cartão, e só a lista rola;
    (c) **o rodapé de estado some quando a janela está maximizada** (`WindowPlacement.Maximized`
    → `showStatus = false`): com a tela toda, o espaço é melhor usado pelo vídeo;
    (d) **maximizar não pode esconder os menus** — o vídeo passou a `clipToBounds()` (nada dele
    pinta fora da própria área) e a barra de menus desenha com `zIndex(1f)` por cima; eram as
    duas maneiras de o vídeo, ao crescer, cobrir a barra;
    (e) **uma caixa ciano, e não duas** — a sobreposição também desenhava os marcadores em
    `PAUSED`, com o rótulo "… · última pose". Com dois marcadores na mesa saíam **duas caixas**
    sobrepostas (uma com a medida, outra com "última pose"), exatamente o que o usuário
    relatou. Agora só os marcadores **rastreados neste quadro** ganham caixa;
    (f) **com o modelo carregado, a caixa some** — ela é o aviso de que a detecção funciona
    enquanto não há nada ancorado; depois disso, o modelo ancorado já diz o mesmo, e a caixa só
    poluía a figura.
26. **Modo tela cheia** (pedido do teste em campo): o botão **"Tela cheia"** (chave nova
    `action_fullscreen`, traduzida nos oito idiomas) coloca a **janela inteira** em
    `WindowPlacement.Fullscreen`, e a tela de RA esconde a barra de menus, o rodapé e a fileira
    de comandos — o modo existe para ver **só a câmera com o modelo**. Nesse modo os comandos
    passam a **sobrepor** a imagem, numa faixa de 84 dp no rodapé.
    O fade é de **alfa animado** (`animateFloatAsState`), e não `AnimatedVisibility`: a barra
    vive no escopo de uma `Column` desta tela, e ali a sobrecarga de `ColumnScope` vence a
    resolução (a versão sem escopo exigiria receptor explícito). Com o alfa em zero a barra sai
    da composição — botão invisível que ainda recebe clique é armadilha.
    A barra aparece ao entrar no modo, **some depois de 3 s sem o mouse passar sobre a faixa** e
    volta assim que ele passa por ali: só "houve movimento" é sinal confiável (não há
    `Enter`/`Exit`), e a faixa é mais alta que os botões para não ser preciso acertar o mouse
    exatamente sobre um deles. Os comandos não são escondidos de vez justamente porque "tirar
    uma captura no modo tela cheia" é uma das razões de o modo existir.
    A saída é a tecla **`Esc`** — tratada na janela (`Window(onKeyEvent)`) para funcionar mesmo
    com o foco num botão, e também na área do vídeo quando é ela que tem o foco; como cada
    caminho só age se for o que recebeu a tecla, nunca há dois toggles para a mesma tecla. O
    próprio botão "Tela cheia" também sai do modo, para quem não lembra da tecla.
27. **Texto na tela: menos ruído, e o estado no lugar do estado** (pedido do teste em campo).
    Três mudanças, todas de conteúdo — nenhuma delas esconde função nova:
    (a) **saiu o texto de apresentação** que ficava logo abaixo da imagem da câmera
    (`about_description`). Ele é o mesmo texto do diálogo **Sobre** (menu Ajuda), onde faz
    sentido: quem quer ler a apresentação abre a Ajuda; quem está usando o aplicativo quer a
    imagem;
    (b) **a mensagem de marcador deixou de ser desenhada sobre a imagem** em ciano ("Marcador
    carregado: Marcador A — carregue um modelo 3D para exibi-lo") e passou a ser a **primeira
    linha do rodapé**. Estado pertence ao rodapé, não à figura que o usuário está olhando — e
    sobre a imagem ela competia com o próprio modelo. Com modelo carregado, a mesma linha passa
    a dizer `status_marker_tracking` (é a variante que o app Android previa, e que até aqui só
    existia no caminho de mensagem que saiu);
    (c) **saiu a linha de ambiente do rodapé** (nome, versão, sistema, idioma e renderizador):
    é informação de diagnóstico, e ficava competindo com o que interessa no uso. Quem precisa
    desses dados continua tendo o **"Copiar diagnóstico"** (menu Ajuda), que os leva inteiros
    para quem for investigar um problema.
    No lugar da linha de ambiente, o rodapé ficou com: **aviso de leitura do marcador** (ciano,
    só quando há marcador na frente da câmera), **modelo carregado** (ou a última mensagem) e
    **marcador escolhido**.
28. **Refinamentos de rótulo, aviso e ícone** (pedido do teste em campo):
    (a) **os rótulos perderam os parênteses**. `action_auto_scale` passou de "Escala automática
    (ajustar ao marcador)" para **"Ajustar ao marcador"**, e `action_adjust_model` de "Ajustes
    do modelo (rotação e tamanho)" para **"Ajustes do modelo"** — nos oito idiomas ("Fit to
    marker", "Ajuster au marqueur", "Am Marker ausrichten", "Adatta al marcatore", "适配标记"…).
    Os parênteses explicavam o que o botão faz quando ele ainda era novidade; com o aplicativo
    em uso, viram ruído em cada botão. Como **o menu e os botões usam a mesma chave**, o
    renomeio vale para os dois de uma vez — era o argumento de ter uma chave só;
    (b) **aviso sobre a imagem ao aplicar a escala automática**: um "slot" escuro com cantos
    arredondados no **alto** da imagem, com o texto `status_auto_scale_applied` ("Escala
    automática aplicada"), que aparece com fade e some sozinho em 2,5 s. Ele responde "o botão
    funcionou?" onde o usuário está olhando, sem obrigá-lo a procurar a linha certa no rodapé —
    que continua mostrando a **medida** aplicada, que é o dado a conferir depois. Chave nova, nos
    oito idiomas (114 → 115, e o teste de paridade cobra o número);
    (c) **ícone da janela**: o `Ark-Z_Logo.ico` que estava em `build/resources/main/icons/` foi
    trazido para **`src/main/resources/icons/`** — a pasta dentro de `build` é saída do Gradle,
    então um `clean` apagaria o ícone e ele não entraria no jar. A leitura **não** usa
    `painterResource`: o compilador do Compose proíbe chamada `@Composable` dentro de
    `runCatching`, e sem essa proteção um ícone que não decodifica impediria o aplicativo de
    abrir. O arquivo é lido do classpath e decodificado pelo Skia (o mesmo caminho do quadro da
    câmera), e a falha vira uma linha no log — a janela abre sem ícone, em vez de não abrir.
29. **Ícone reaplicado ao sair da tela cheia, e um pacote portátil** (dois pedidos do teste em
    campo):
    (a) **o ícone da janela sumia** ao voltar da tela cheia. A causa é do Windows: a mudança de
    modo reconstrói as decorações da janela e o ícone se perde. Passar o ícone de novo ao Compose
    não resolve — para o Compose, nada mudou. A saída é escrever direto no `Frame` do AWT
    (`window.iconImage`) **a cada mudança de `windowState.placement`** (`LaunchedEffect`), com o
    ícone convertido para imagem do AWT (`toAwtImage`);
    (b) **pacote portátil** (`gradlew createDistributable`): uma pasta autossuficiente
    (`build/compose/binaries/main/app/ArkZ ARModelViewer/`, ~258 MB com o JRE junto) que roda
    **sem Java instalado e sem instalação** em outro computador — copiar e executar o `.exe`. É o
    caminho para testar num notebook com **touchpad e tela sensível ao toque**, onde a pinça (que
    chega como `Ctrl`+roda no touchpad e como gesto de dois toques na tela) finalmente pode ser
    verificada, depois de tantas rodadas documentada como "não verificável nesta máquina".
    Aproveitou-se para apontar `windows.iconFile` no `build.gradle.kts`: sem isso, o `.exe` sairia
    com o ícone padrão do Java (o ícone da janela que o `Main.kt` carrega só vale em runtime).
30. **"Não consigo carregar o modelo 3D" no notebook — duas causas, as duas de empacotamento.** O
    sintoma relatado foi a mensagem `Could not initialize class org.lwjgl.assimp.Assimp` no rodapé,
    com a câmera funcionando. Essa frase é o invólucro que o JVM cria quando o inicializador
    estático de uma classe falha: o motivo está na **causa**, e a interface mostrava só o invólucro.
    A investigação foi por evidência, não por suspeita:
    (a) **módulo ausente (a causa principal)**: reproduzida **nesta máquina**, no pacote. O `.exe`
    empacotado falhava com `NoClassDefFoundError: sun/misc/Unsafe` dentro de
    `org.lwjgl.system.MemoryUtil`. O runtime montado pelo jpackage trazia **sete** módulos
    (`java.base java.datatransfer java.xml java.prefs java.desktop java.logging jdk.crypto.ec`) e
    **não** trazia o `jdk.unsupported`, que é onde vive o `sun.misc.Unsafe` exigido pelo LWJGL. É a
    explicação de o `gradlew run` funcionar (JDK completo) e o pacote não — e de o defeito só
    aparecer no computador do usuário. Corrigido em `nativeDistributions.modules("jdk.unsupported")`;
    (b) **runtime do Visual C++**: a tabela de importação das DLLs do próprio pacote mostra que a
    `assimp.dll` e a `draco.dll` (assim como as `opencv_*` e as `jni*` do JavaCPP) importam
    `msvcp140.dll`, `vcruntime140.dll` e `vcruntime140_1.dll` — o runtime do **Visual C++
    2015-2022**. Num Windows sem ele, o carregador não acha as dependências e a DLL não carrega.
    O OpenCV escapava porque o **JavaCPP extrai a própria cópia do runtime** junto das DLLs dele —
    é o que explica a câmera funcionar e só a conversão falhar. O `createDistributable` agora copia
    as três de `runtime/bin` (elas já vêm no JRE empacotado) para junto do `.exe`, que é o lugar que
    o Windows procura depois da pasta da própria DLL;
    (c) **o diagnóstico que faltava**: `util/NativeLibraries.kt` desembrulha a causa profunda e diz o
    que fazer (interface e log deixam de mostrar o invólucro), o `AppLog.start()` avisa na abertura
    quando o runtime não está presente, e o modo de linha de comando
    `"ArkZ ARModelViewer.exe" --check-model "arquivo"` (ou `gradlew modelCheck`) converte um modelo
    de verdade e grava `logs/diagnostico-modelo.txt` com versões, pastas, presença das DLLs e o
    motivo da falha — o pacote é um programa de janela, sem console, então `println` não apareceria
    em lugar nenhum.
    **Verificação:** o **mesmo** `.exe` empacotado que falhava passou a sair com código `0` e o
    relatório `--- resultado: OK ---` (converteu o `.obj` do repositório: 1812 triângulos, caixa
    envolvente conferida); o pacote abre, detecta **dois** marcadores em `TRACKING` e não gera crash
    dump; o repositório ficou com **197 testes** (9 novos em `NativeLibrariesTest`, montados com a
    cadeia de exceções real do notebook, e três deles conferindo a presença das DLLs em pastas
    temporárias).
31. **A pinça de dois dedos na tela sensível ao toque não funciona — e não é defeito do
    aplicativo: é a camada de entrada do desktop.** O usuário relatou no teste em campo ("me parece
    que a pinça na tela touchscreen não funcionou") e pediu conferência. A conferência foi por
    **inspeção dos jars que o pacote usa**, não por suposição: nem o `skiko-awt-0.150.1` nem o
    `ui-desktop-1.12.0` têm qualquer referência a `TouchListener`, `TouchEvent`, `TouchAdapter` ou
    `onTouch` — a entrada do desktop escuta mouse, teclado e roda, e nada de toque. No Windows, o
    toque chega ao aplicativo **convertido em mouse**, de um contato só: uma pinça de dois dedos
    entrega ao `detectTransformGestures` **um** ponteiro, o `zoom` fica sempre em `1f` e nada
    acontece. O mesmo motivo explica por que o **arrasto de um dedo** funciona (é um mouse
    arrastando) e por que a pinça de **touchpad** chega ao aplicativo (`Ctrl`+roda, o caminho que o
    Windows usa para touchpads de precisão — o mesmo da roda do mouse, já confirmado na janela).
    O que foi feito a partir disso:
    (a) **botões `−` e `+`** na fileira de comandos — e na barra da tela cheia, que usa a mesma
    fileira: zoom com **um dedo só**, pelo mesmo caminho de código da roda e das teclas
    (`InteractiveInput.zoomedSize`), desabilitados sem modelo. O texto é só o sinal, então não há
    texto novo para traduzir nos oito idiomas nem mudança no teste de paridade de chaves;
    (b) **medição no log**: cada mudança do que está em contato com o vídeo grava uma linha
    (`Pontos em contato com o video: 1 (Mouse).`), observando a passagem `Initial` para não
    interferir nos gestos logo abaixo na cadeia. É o que torna a afirmação acima **verificável na
    máquina do usuário**: se um dia aparecer `2 (Touch)`, a pinça deveria estar funcionando — e o
    defeito passaria a ser nosso;
    (c) o gesto de dois dedos **continua no código** de propósito: é a implementação correta e
    passaria a valer no dia em que a plataforma entregar toque (outra versão do Compose/Skiko, ou
    outro sistema). O que esta decisão corrige é a **promessa**: o KDoc do `InteractiveInput` e o
    README diziam que a pinça de tela era apenas "não verificada", quando na verdade ela não tem
    como acontecer nesta plataforma.
    O que o usuário de tela sensível ao toque tem à mão: os botões `−`/`+`, o cursor de **Tamanho**
    nos ajustes do modelo (um dedo só) e a **Escala automática**.
32. **O que o teste em campo confirmou, entrada por entrada** (o fecho da etapa 6). O usuário
    respondeu à medição da decisão 31 com o notebook na mão: **no touchscreen o aplicativo se
    comporta como mouse** — o arrasto de um dedo desloca o modelo e o clique funciona, e nenhum
    evento de toque múltiplo chega; **no touchpad a pinça de dois dedos amplia o modelo** (é a
    conversão em `Ctrl`+roda que o Windows faz, o caminho da decisão 20); **`Ctrl`+roda está
    correto**; e **o botão esquerdo arrasta o modelo sobre o marcador** — que era justamente o
    item que ficou pendente de reconferência na decisão 21, depois da correção do sentido
    vertical. Com isso a **etapa 6 passa a ✅** e o zoom na tela sensível ao toque fica com os
    **botões `−`/`+`** (decisão 31), que é o que um contato só consegue operar.
33. **Licença, créditos e publicação.** O pedido foi deixar explícito o direito autoral e a
    licença, e publicar em `https://github.com/em-rezende/Windows-ArkZ-ARModelViewer`, com as
    quatro linhas de crédito (licença, direito autoral, site e e-mail). O que ficou decidido, e
    por quê:
    (a) **a GPL-3.0 já estava no lugar certo** desde a etapa 0 — `LICENSE` com o texto
    completo e `NOTICE` com as licenças de terceiros. O que faltava era o aviso **visível para
    quem usa o aplicativo**, que é o que a seção 5 da GPL pede de uma interface interativa;
    (b) por isso o diálogo **Sobre** passou a mostrar, além da descrição e da versão, a linha
    `Copyright (C) 2026 Ark-Z Arquitetura Ltda`, o **desenvolvedor**, o **site**, o **e-mail**
    e a **licença**. `about_developer` **mantém o nome da chave do Android** (e o valor do
    Android no `strings.xml` do pt-BR) e passa a ser a linha de direito autoral: no Android o
    aviso vem da ficha da Play Store, aqui tem de estar na própria tela. A divergência está em
    `tools/i18n-overrides/_parity.properties` e na tabela de `docs/i18n-parity.md`;
    (c) `about_license` é a **única chave nova** (115 → **116** chaves), e é a única das linhas
    de crédito que **muda de idioma** — por isso ela mora nos oito arquivos por idioma e não no
    `_parity.properties`. O teste de paridade cobra os oito, e o comentário dele diz o que
    fazer ao acrescentar a próxima;
    (d) o **pacote** leva a mesma informação: o `copyright` do `nativeDistributions` ganhou o
    desenvolvedor (o `vendor`, a `description` e o `licenseFile` já estavam declarados desde a
    decisão 30), e é isso que aparece nas propriedades do `.exe` e no instalador;
    (e) **publicar virou um passo próprio**, com os arquivos que um repositório público pede:
    `.gitattributes` (o irmão Android já tinha um — e sem ele um `.stl`/`.ply` binário pode ser
    corrompido pela conversão de fim de linha), `CHANGELOG.md` (Keep a Changelog, 1.0.0),
    `SECURITY.md`, `CONTRIBUTING.md` e a release `v1.0.0`. Os passos estão em
    `docs/development.md`;
    (f) **a promessa falsa da pinça foi corrigida no texto de Ajuda dos oito idiomas**, não só
    no pt-BR: os textos herdados do Android falavam de `adb logcat` e da pinça de tela. Os
    `help_body` corrigidos ficaram **também** em `tools/i18n-overrides/`, para o pipeline de
    idiomas continuar idempotente (a Ajuda do Android mencionava o Logcat em seis idiomas).
34. **O referencial do marcador: o que a análise diz, o que o campo aprovou e o que fica
    adiado.** O usuário relatou ("quando giro o marcador pela sua normal, o modelo gira no
    eixo errado") e pediu verificação. A análise do código é esta:
    (a) as quatro quinas que o detector entrega ao `solvePnP`
    (`MarkerDetector.markerObjectPointsMat`) têm **Y = 0** — o marcador é plano nesse eixo —, e
    o KDoc do `Pose` e os nomes `extentX`/`extentZ` do `AugmentedImage` dizem o mesmo: o
    referencial que sai da detecção tem **X = largura, Y = NORMAL, Z = altura NA imagem**;
    (b) a cadeia de renderização confirma esse referencial: o `CameraProjection` monta a matriz
    das intrínsecas com o Y para cima e o `FilamentRenderer` usa `camera.lookAt(…, 0, 1, 0)`;
    (c) o `ModelPlacement`, porém, aplica uma **rotação de apoio de 90° em X** e a ancoragem do
    `ModelMetrics` supõe o plano em **XY** — o "para cima" do modelo acaba no eixo **Z** (a
    altura NA imagem), e é por isso que girar a folha pela normal **deita** o modelo.
    **A correção foi aplicada em etapas — 1.0.1, revertida na 1.0.2 e fechada na 1.0.3** — e é
    isso que a versão atual entrega:
    (1) o **referencial físico** (o de cima): apoio da base **na normal** (Y), **sem** a rotação
    de apoio antiga, e arrasto no plano verdadeiro (X e Z, com a normal intocada);
    (2) o **eixo "para cima" do arquivo**: o teste em campo mostrou que o modelo do usuário —
    e os desta família, exportados de CAD/SketchUp ou convertidos pelo Assimp — tem o **Z para
    cima**. Com o referencial corrigido e o modelo presumido Y-up (a 1.0.1), ele carregava
    **deitado** e exigia "Rotação em X = 90°" à mão. O `ModelPlacement` passou a aplicar
    `Rx(−90°)` como **apoio**, levando o +Z do arquivo exatamente para a normal do marcador, e o
    `ModelMetrics.anchorPosition` a apoiar a base no **Z do arquivo** e a centrar a profundidade
    (o Y do arquivo) na altura da imagem.
    **O que isso resolve, e o que o teste cobre:** com o "para cima" sobre a normal, **girar a
    folha pela normal gira o modelo em torno de si mesmo, no mesmo sentido da folha** — em vez de
    deitá-lo —, o modelo carrega **de pé** (o zero dos sliders é "em pé") e o arrasto anda **no
    plano** do papel, sem tirar o modelo dele. O teste `girar o marcador pela sua normal…` gira a
    folha em 12 posições e cobra o "para cima" sempre sobre a normal (0,2 m de altura).
    **Fechada na 1.0.4 com a referência do app Android.** O usuário apontou o projeto Android
    finalizado (`D:\PROGRAMACAO\ARK-Z ANDROID APP\ArkZ ARModelViewer`) e disse que a solução
    estava lá — e estava: em `ui/ARViewScreen.kt`, o `anchorPosition` (linhas 1508–1512) usa
    `x = -center.x · escala`, `y = -(center.y - halfExtent.y) · escala + elevação` e
    `z = -center.z · escala`, com o referencial documentado logo acima (**X = largura, Y = a
    NORMAL, Z = altura NA imagem** — o do ARCore para imagens, citando os nomes `extentX`/
    `extentZ`), e **não existe rotação de apoio nenhuma**: o `ModelNode` recebe apenas
    `Rotation(rotationX, rotationY, rotationZ)` (linha 800) — a orientação do arquivo mais o que
    o usuário ajustar nos cursores.
    A versão Windows ficou **igual**: mesma ancoragem, **sem apoio embutido**, e o arrasto (que o
    Android não tem) andando no plano X–Z, com a normal intocada. O que tinha sobrado de
    diferença era invenção minha — o apoio de 90° que a 1.0.2/1.0.3 embutia para "pôr o modelo
    de pé" — e era ele que mudava a altura de carga e o eixo do giro.
    **O que fica como característica, e não como defeito:** o modelo carrega na orientação do
    arquivo, então um arquivo com outro eixo para cima pede o cursor **Rotação X** em 90° —
    exatamente como no Android, e é o mesmo número nos dois aplicativos.
    **Revisto pela decisão 38 (não publicado):** "a orientação do arquivo" deixou de ser uma suposição
    e passou a ser uma **correspondência de eixos escrita** no código (`ModelPlacement.MODEL_ORIENTATION`):
    o plano XY do arquivo é o plano da figura e o +Z do arquivo é a normal, o que põe o modelo de pé
    **com a folha de frente para a webcam** — a situação do desktop, e a que faltava. A ancoragem do
    `ModelMetrics` continua a mesma (base apoiada no plano, centrada na largura e na altura da imagem);
    o que mudou é que ela recebe a caixa **já orientada**.
35. **A rotação inicial do modelo é 90° em X — e agora está no código, não no usuário.** Os
    arquivos desta família (CAD/SketchUp, e tudo o que o Assimp converte de
    `.obj`/`.stl`/`.ply`/`.3mf`) têm o **Z para cima**: com a rotação inicial em zero o modelo
    carregava **deitado** sobre a figura, e o usuário tinha de girar o cursor X em 90° à mão
    **em cada modelo carregado**. Como é o mesmo número no app Android, não se trata de defeito
    desta versão — era um **padrão que faltava**. `RenderScene.DEFAULT_ROTATION_DEGREES` passa a
    ser esse valor: o cursor de Rotação X abre em **90°** e o "Redefinir" volta para lá, e o
    usuário continua livre para ajustar X, Y e Z a partir dele. Um teste cobra o valor inicial.
    **Segunda etapa, fechada na 1.0.6 (decisão 36):** o **arrasto vertical**. A hipótese
    registrada nesta linha — "hoje o movimento do mouse no eixo Y mexe a normal do marcador" —
    foi **medida e é falsa**: o arrasto escrevia o **Z**, e passou a escrever o **Y** (veja a
    decisão 36).

    **Corrigida na 1.0.7 (decisão 37):** a premissa do **Z para cima** **não se confirmou** nos
    arquivos do repositório (medidos: o para cima deles é o **+Y**), e, com o modelo já **de pé**
    no zero dos cursores, os 90° em X **deitavam** o modelo no plano da folha — o giro da folha
    passava a **rolá-lo** ali dentro. A rotação inicial voltou a **zero**.
36. **O arrasto vertical anda no frente–trás (a normal), e não na altura da imagem.** O relato do
    usuário, no notebook e com o "Marcador A" impresso e **virado para ele**, foi: o arrasto
    vertical do mouse **sobe e desce o modelo** em vez de movê-lo frente–trás sobre a figura; o
    horizontal estava certo. A hipótese registrada na 1.0.5 era que o arrasto estivesse escrevendo
    a **normal** do marcador; o pedido foi *medir* antes de mexer. A medição mostrou outra coisa —
    e é este o valor desta decisão:
    (a) **o código andava no plano**, sim: escrevia **X** (a largura) e **Z** (a "altura NA
    imagem") e forçava Y = 0, então o modelo não saía do papel por construção. A hipótese da
    "incoerência" no mapeamento estava errada;
    (b) o que decide o resultado não é a intenção do código, e sim **o que a pose entrega**. Num
    quadro sintetizado com a figura real de 0,15 m **de frente** para a câmera, a 21 cm, as
    colunas da rotação do `solvePnP` — no referencial do mundo (X para a direita, Y para cima,
    câmera olhando para −Z) — são: **X do marcador = (0,99, −0,05, 0,12)** (a largura, para a
    direita), **Y = (−0,13, −0,38, 0,92)** (a **normal**, apontando para a câmera) e
    **Z = (0,00, −0,92, −0,38)** (a altura NA imagem — e, nesta pose, o **chão do mundo**). O
    teste `de frente para a camera a pose entrega a normal no Y e a altura na imagem no Z`
    (`MarkerDetectorTest`) refaz essa medição a cada execução da suíte: se a detecção mudar de
    referencial, é ali que o teste quebra primeiro;
    (c) a conta do arrasto, medida em `InteractiveInputTest` com essa pose e um modelo de 50 cm
    (100 px = 0,1 m): escrito no **Z** o modelo andava **92 mm na vertical do mundo** e só 38 mm
    em profundidade — era este o "sobe e desce" —, e escrito no **Y** ele anda **92 mm
    frente–trás** (se aproximando da câmera) e 38 mm na vertical.
    **A correção é uma troca de eixo, e nada mais:** o arrasto vertical passou a escrever o **Y**
    (a normal — o mesmo eixo do cursor de **Elevação**, com o mesmo sentido: arrastar para baixo
    traz o modelo para a frente) e o **Z ficou em zero** (`InteractiveInput.panOffset` e
    `SceneComposer.setOffsetMeters`). O horizontal não mudou: continua a largura (X). A rotação
    inicial, a ancoragem e o referencial do marcador **não** foram tocados — o que estava aprovado
    em campo continua igual (`ModelPlacement` segue com `local = T · R · S`, sem apoio embutido — hoje
    `local = T · ORIENTAÇÃO · R · S`, com a correspondência de eixos da decisão 38).
    **O que se abre mão, e por quê:** com o arrasto na largura e no frente–trás, o modelo deixa de
    ser movido **na altura da figura** por gesto — que é exatamente o movimento que o usuário
    recusou. Para uma figura deitada **na mesa**, o frente–trás passa a ser a normal da mesa (o
    modelo levanta e baixa do papel): é o mesmo eixo, com outro significado conforme a figura
    esteja em pé ou deitada. Se o campo pedir o contrário nesse caso, a troca é de uma linha, e o
    teste do eixo (`o arrasto vertical anda no frente-tras, e nao na altura da imagem`) é quem
    guarda a decisão.
    **A lição, para o próximo relato de arrasto:** "anda no plano" não descreve o que o usuário
    vê. Quem decide o sentido de um gesto é a **pose medida** — e medi-la é barato (um quadro
    sintetizado, como o `MarkerDetectorTest` já fazia desde a etapa 3). A hipótese registrada na
    1.0.5 apontava o **eixo errado**, e a diferença entre o eixo suposto e o eixo real é
    exatamente a que se mediu: 92 mm de vertical contra 38 mm de profundidade.

37. **A rotação inicial voltou a ser zero: o modelo carrega de pé e o giro da folha gira o modelo em
    torno da vertical.** O relato do usuário, com as capturas do painel e da folha na mesa, foi:
    *o giro do marcador não gira o modelo, e nenhuma das combinações de ajuste resolveu* — e ele
    apontou o lugar certo: *não sei por que o cursor **Rotação X** está em 90°; acho que ele deveria
    zerar*. Duas coisas se somaram:
    (a) **os dois ajustes de teste da 1.0.6 não existiam no código.** `DEFAULT_SEGUIR_A_FOLHA` e
    `DEFAULT_EM_PE_NA_VERTICAL` eram **parâmetros de `worldMatrix` sem nenhuma implementação**:
    qualquer que fosse o valor, a matriz saía igual. Era por isso que **nenhuma** das combinações
    conferidas em campo mudava o que o usuário via — e era por isso que **dois testes falhavam**
    desde então (eles cobravam o comportamento que não estava lá). Os dois foram **removidos**: com o
    modelo de pé, o giro da folha já é o prato giratório que o campo pediu. Um modo "vertical do
    mundo" só faria falta para uma folha **inclinada de propósito** (apoiada num livro, por exemplo),
    em que o modelo teria de ficar em pé na vertical do mundo em vez de seguir a normal da folha —
    fica como **possibilidade registrada**, não como pendência: hoje o modelo se comporta como um
    objeto **colado na folha**, que é o que o app Android faz e o que a decisão 34 fixou.
    (b) **a premissa dos 90° em X era falsa, e a medição a desfez.** A decisão 35 supôs que estes
    arquivos têm o **Z para cima**. Medidos os modelos do repositório, o "para cima" deles é o **+Y**:
    o **`ArkZ_logo.obj`** (a fonte do logotipo) mede **419,0 no X**, **140,9 no Y** e **10,0 no Z** —
    a altura está no Y —, o `ArkZ_logo.glb` que o Assimp gera dele tem exatamente as mesmas medidas, o
    `House.glb` (Blender) mede 5,0 × 4,5 × 5,0 com a **porta vermelha na face +Z** e o telhado no topo
    do Y — é a casa de referência dos testes de GPU desde a decisão 38. Como o `ModelPlacement`
    ancora na orientação do arquivo, **sem apoio embutido** (decisão 34), o zero dos cursores é o
    estado **de pé** — e os 90° em X o **deitavam**.
    **A medição, feita na GPU** (motor do Filament, a folha deitada na mesa a 0,6 m de uma câmera 25°
    abaixo da horizontal, o `House.glb` em 0,13 m, a folha girada de 90° em 90°), com o topo do modelo
    — o ponto mais alto do arquivo — projetado no quadro de 640 × 360 px:
    | Rotação inicial | Altura do topo **pela normal da folha** | Altura do topo no mundo | O que o giro da folha faz |
    |---|---|---|---|
    | 90° em X (1.0.5–1.0.6) | **0,000 m** — dentro do plano | −0,0549 m a 90° contra +0,0541 m a 0° | **rola**: o topo anda 115 px de lado (320 → 428) |
    | **zero** (agora) | **0,117 m** — a altura do modelo | **igual nos quatro giros** | **gira**: o topo cai no mesmo pixel (320, 73) nos quatro giros |
    O teste que guarda isso é `com a folha deitada na mesa, girar a folha gira o modelo em torno da
    vertical` (`ModelPlacementTest`), que mede as duas colunas do meio da tabela e que **falha** com os
    90° em X de volta: *com a folha a 0,0° o modelo tem de continuar DE PÉ (subiu 3,7e-9 m pela
    normal, e não 0,13 m)*.
    **A correção é de uma linha** — `RenderScene.DEFAULT_ROTATION_DEGREES = Vec3.ZERO` — e nada mais
    foi tocado: a ancoragem (`ModelMetrics.anchorPosition`), o referencial do marcador, o arrasto
    (decisão 36) e os cursores seguem iguais. Um arquivo com outro eixo para cima continua pedindo o
    cursor **Rotação X** em 90°, como no app Android.
    **A lição:** a premissa "estes arquivos têm o Z para cima" atravessou três versões (1.0.3, 1.0.5 e
    o texto da decisão 35) **sem uma única medição** — e era ela que explicava o defeito que a 1.0.5
    dizia corrigir. Medir os arquivos do próprio repositório (o `.obj`, o `.glb` do Blender e o do
    SimLab) custou um comando, e teria evitado a volta inteira.
    **Revisto pela decisão 38 (não publicado):** a decisão 37 corrigiu o valor **inicial** dos cursores
    (de 90° em X para zero), mas a correspondência de eixos continuou a de **identidade** — o que só dá
    "de pé" com a folha **deitada na mesa**, que era a situação das capturas. Com a folha **de frente
    para a webcam** (a situação do relato seguinte) o modelo carregava deitado de costas e girava no eixo
    errado; é a decisão 38 que fixa a correspondência (o plano XY do arquivo é o plano da figura e o +Z
    do arquivo é a normal). O zero dos cursores continua valendo — agora como "em pé, de frente".

38. **O modelo carrega DE PÉ com a face virada para quem olha: o plano XY do ARQUIVO é o plano da figura, e
    o +Z do arquivo é a normal do marcador.** O relato, com o "Marcador A" impresso e **virado para o
    usuário** (a situação do desktop: a webcam fica no monitor e a folha é erguida à frente dela): *"giro o
    marcador em torno da sua normal e o modelo gira no eixo Y em vez do Z"* e, no mesmo quadro, *"os modelos
    estão sendo carregados deitados"*. Os dois sintomas têm uma causa só — e ela não está em nenhum dos dois
    lugares onde a busca começou (a rotação inicial do painel e o arquivo do modelo):
    (a) **a causa.** O `ModelPlacement` ancorava o modelo com a correspondência de **identidade** (como o
    app Android, que herda o referencial do ARCore): o **+Y** do arquivo (a altura, no glTF) ia para o
    **+Y do marcador**, que é a **NORMAL**. Isso só dá "de pé" quando a normal é a vertical do mundo — isto
    é, com a folha **apoiada na mesa**, o caso que a decisão 37 mediu na GPU. Com a folha de frente para a
    câmera a normal é **horizontal**: o modelo carrega **deitado de costas** (o telhado apontando para quem
    olha) e o giro da folha em torno da normal — que é o giro em torno do **Y do marcador** — roda o modelo
    em torno do **próprio Y**, e não do eixo correspondente;
    (b) **a correção.** Uma **correspondência de eixos fixa** entre o arquivo e o marcador
    (`ModelPlacement.MODEL_ORIENTATION`, uma rotação de −90° em X), que é exatamente o referencial que o
    relato descreve: **X do arquivo → X do marcador** (a largura), **Y do arquivo → −Z do marcador** (a
    altura NA imagem, para cima) e **Z do arquivo → Y do marcador** (a normal). Com ela o plano **XY** do
    arquivo (a face do modelo) é o plano da figura, o **+Z** do arquivo (a face que "olha") cai na normal,
    e girar a folha pela normal gira o **+Z do arquivo** — eixo por eixo, como pedido;
    (c) **a ancoragem entrou junto, e não é detalhe.** O `ModelMetrics.anchorPosition` apoia no plano a face
    de **trás** do modelo e o centra na largura e na altura da imagem — mas ele trabalha no referencial do
    **marcador** e recebia a caixa do **arquivo**. Com a orientação, o "Y" da caixa passa a ser a
    **espessura** do modelo e o "Z" a **altura** dele: sem `ModelPlacement.orientedBounds` a ancoragem
    apoiaria a face errada (o modelo ficaria metade à frente e metade atrás da figura). O deslocamento do
    arrasto, a elevação e o referencial do marcador **não** mudaram;
    (d) **os cursores giram nos eixos do arquivo** (X = largura, Y = "para cima", Z = o "frente"), na ordem
    `T · ORIENTAÇÃO · R · S`: é o que dá sentido ao painel — "Rotação Z" gira em torno do eixo que sai da
    folha (a normal), que é o que se vê — e é o que mantém o **zero** como "em pé, de frente" (a decisão 37
    continua valendo: o painel abre em zero);
    (e) **a medição, feita na GPU** (o motor do Filament, a folha de frente para a câmera a 0,5 m, num
    quadro de 96 × 96, com os modelos do próprio repositório):

    | Modelo (sem giro nenhum) | Pixels desenhados | O que se vê |
    |---|---|---|
    | `House.glb` — a referência: 5,0 × 4,5 × 5,0, telhado no topo do **Y** e a **porta vermelha na face +Z** | **1915**, iluminado | a casa **de pé**, com a porta virada para a câmera |
    | `ArkZ_logo.stl` — a chapa do logo: a altura no **Z** e a espessura no **Y** (a convenção do **STL**, que não tem metadado de eixo) | **0** | a chapa fica **de perfil**; com **90° no cursor X** ela mostra a face (**146** pixels) |

    Os testes que guardam isso: `de frente para a camera o modelo aparece de pe com a frente virada para quem
    olha` e `girar o marcador pela normal gira o modelo no proprio eixo Z, sem deita-lo` (em
    `ModelPlacementTest`, com as medidas do `House.glb` de referência) e o teste de GPU
    `o modelo aparece ancorado no marcador e some sem rastreio` (`FilamentRendererTest`, que agora usa a casa
    de referência e o logo do repositório, e cobra que a **porta vermelha** apareça no quadro).
    **Conferido por mutação:** com a correspondência de identidade de volta (`MODEL_ORIENTATION` sem o
    −90° em X), **9 testes falham** — entre eles o da GPU, com *"a porta vermelha da casa tem de aparecer
    virada para a câmera"* (a casa até aparece, mas vista de cima, com o telhado verde para a câmera);
    (f) **o que se abre mão, e por quê.** O modelo é **colado à figura**, e não orientado pela vertical do
    mundo: com a folha apoiada na mesa ele aparece **deitado com a face para cima** (o teste
    `com a folha apoiada na mesa o modelo fica deitado com a face para cima` mede isso). É o preço de a
    correspondência ser **fixa** — e ela precisa ser fixa para o giro da folha chegar ao eixo
    correspondente, que é o pedido do relato. Um arquivo com outro eixo para cima continua pedindo o cursor
    correspondente: com esta correspondência, um arquivo **Z-para-cima** fica de pé com **−90°** no cursor X
    (o teste `um arquivo exportado com o Z para cima fica de pe com menos 90 graus no cursor X`).
    **A lição:** o par "modelo deitado / giro no eixo errado" já tinha aparecido na decisão 34 e voltou na 37
    porque a **situação de campo** mudou (a folha na mesa × a folha na mão, de frente para a webcam) sem que
    a correspondência de eixos estivesse escrita em lugar nenhum. Agora ela está: uma constante
    (`MODEL_ORIENTATION`), um KDoc e quatro testes.

39. **O modelo aparecia de cabeça para baixo em relação ao vídeo: a leitura do quadro era invertida e a
    textura do fundo compensava.** O relato seguinte ao da decisão 38, com o "Marcador A" virado para a
    webcam: *"a casa apareceu de cabeça para baixo, com o telhado apoiado na folha e o piso para cima;
    girando o marcador 180° ela fica em pé"* e *"o arrasto frente–fundo está invertido"*. O **modelo** e o
    **vídeo** discordavam entre si — e a causa era **antiga**, invisível até a decisão 38:
    (a) **a causa: duas inversões verticais que se cancelavam só para o vídeo.** A leitura do quadro
    aplicava `flippedVertically()` e as coordenadas de textura do plano de fundo (`QUAD_VERTICES`) punham a
    linha de cima da imagem no topo do mundo com `v = 1`. O par fazia o **vídeo** aparecer certo no quadro
    (é o que o teste de orientação do fundo sempre cobrou ✓) enquanto tudo o que é ancorado no **mundo** —
    o **modelo** — aparecia **espelhado na vertical**. Enquanto o modelo carregava "deitado" (o plano XY
    dele no plano da folha, até a 1.0.7) o espelho caía no plano horizontal e não aparecia; com o modelo
    **de pé** (decisão 38) ele ficou óbvio — e o sintoma descrito ("de cabeça para baixo, e 180° de giro
    consertam") é exatamente um espelho vertical;
    (b) **a correção, um par de duas linhas:** a leitura passa a entregar o quadro **como o motor devolve**
    (sem inverter — neste backend, **Vulkan**, medido, ele já vem de cima para baixo) e o plano de fundo
    passa a levar `v = 0` embaixo e `v = 1` em cima. Juntas: o vídeo **continua** de pé no quadro e o
    modelo passa a cair **do mesmo lado** que ele;
    (c) **a medição** (GPU, o `House.glb` a 0,5 m, quadro de 96×96, com deslocamentos conhecidos nos eixos
    do marcador — o offset entra na ancoragem, que é soma pura e não passa por rotação):

    | Deslocamento no marcador | Antes (1.0.7 + decisão 38) | Agora |
    |---|---|---|
    | **Z** +0,1 m (a "altura NA imagem", para baixo) | o modelo ia para **CIMA** (linha 59,7 → 27,2) | vai para **BAIXO** (67,3 → 99,8) |
    | **X** +0,1 m (a largura, para a direita) | à direita (coluna 63,2 → 98,0) | à direita (coluna 63,2 → 98,0) |
    | **Y** +0,1 m (a normal, para a frente) | aproxima (n.º de pixels desenhados: 3 429 → 7 712) | aproxima (igual) |
    | **porta vermelha** do `House.glb` (a parte de baixo da casa, na exportação de então) | desenhada **acima** do centro do modelo (linha 33,9, contra 44,6 do modelo) | **abaixo** (é o que o teste cobrava; hoje quem mede a vertical é o **cubo do telhado**, porque a porta do `House.glb` re-exportado ficou na face do X, fora do campo da câmera de frente) |

    > **Nota (22/09/2026):** o `House.glb` do repositório foi **re-exportado do Blender em +Y para cima**
    > depois desta medição. A casa passou a **5,0 × 5,0 × 4,5 centrada na origem** (o chão em y = −2,5, e
    > não mais em y = 0) e a porta foi para a face do **X**. O que estas decisões fixaram continua valendo
    > e continua coberto — o **+Y do arquivo é a altura**, o plano XY do arquivo é o plano da figura e o
    > quadro não é espelhado —, mas a medida da vertical no teste de GPU passou a ser o **cubo
    > verde-amarelo do telhado** (medido: cubo na linha 37,0 contra 47,5 do modelo, num quadro de 96 × 96),
    > e o `AssimpModelLoaderTest` guarda as medidas novas da casa (`o House glb de referencia tem a altura
    > no Y e o teto no topo`).

    **Conferido por mutação:** restaurando o par antigo (a leitura invertida + as texturas como estavam),
    os **dois** testes do modelo falham — *"o cubo do telhado tem de ficar ACIMA do centro do modelo (cubo
    em 58,0, modelo em 47,5 linhas)"* (**37,0** no estado correto) e *"o Z do marcador tem de levar o modelo
    para BAIXO: (47,5, 47,5) → (47,5, 24,0)"* — e o **teste do vídeo continua passando**, que é a
    assinatura do defeito: o vídeo certo, o modelo de cabeça para baixo. (Reconferido em 22/09, com o
    `House.glb` re-exportado: na medição original, a **porta vermelha** fazia a primeira medida — *"porta em
    33,9, modelo em 44,6 linhas"*.)
    (d) **o arrasto:** a inversão do frente–trás relatada **não** se confirmou como defeito do arrasto — o
    eixo (a normal) e o sentido ("arrastar para baixo **traz** o modelo para a frente", decisão 36) não
    mudaram, e o que o espelho fazia era inverter o **quadro**, e com ele a leitura que o usuário faz do
    movimento. Com o quadro no sentido certo, a percepção passa a bater com a decisão 36; se o campo ainda
    pedir o contrário, é o sinal de **uma linha** em `InteractiveInput.panOffset` (e o teste do sentido é
    quem guarda a decisão — nada foi mudado por conta própria);
    (e) **a lição:** o par "vídeo certo + modelo errado" atravessou **três** decisões (34, 37 e 38) porque o
    defeito ficava escondido no plano horizontal. O teste que o pegaria **não** é o da orientação do vídeo, e
    sim um que meça **onde** o modelo cai *em relação ao vídeo* — com deslocamentos conhecidos nos eixos do
    marcador. É o que `FilamentRendererTest` passou a fazer (`os eixos do marcador caem no quadro do mesmo
    lado que o video mostra`), junto do que mede o **cubo do telhado** acima do centro do modelo (a
    **porta vermelha** fazia essa medida até a re-exportação do `House.glb` — veja a nota acima).
