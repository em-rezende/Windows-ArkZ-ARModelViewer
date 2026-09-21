# Roadmap — ArkZ ARModelViewer Desktop

Port da versão Android (`https://github.com/em-rezende/Android-ArkZ-ARModelViewer`)
para Windows 10/11, com **fidelidade comportamental**: o usuário deve reconhecer o
mesmo aplicativo — as mesmas ações, os mesmos textos, os mesmos números.

> **Concluídas e validadas: etapas 0 a 6 e 9.** As etapas 0 a 4 entregaram o esqueleto, a
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
> pacote), o `.gitattributes` e a publicação deste repositório. **199 testes unitários
> verdes**, dos quais **três desenham quadros de verdade na GPU**. Continuam **abertas as
> etapas 7 (captura de tela sem interface, com as três qualidades) e 8 (instalador `.msi`)**:
> nenhuma das duas bloqueia o uso — o pacote portátil é o que o teste em campo usou, e o
> instalador só acrescenta o atalho do menu Iniciar.

| Etapa | Entrega | Como é validada |
|---|---|---|
| **0** ✅ | Esqueleto Gradle (Kotlin DSL + `libs.versions.toml`), wrapper 9.7.1, `.gitignore`, `LICENSE` (GPL-3.0), `NOTICE`, assets (marcadores e modelos de teste) | `gradlew.bat build` verde e a janela abrindo |
| **1** ✅ | Lógica 100% portável: `MarkerCatalog`, `MarkerGenerator`, `CustomMarkerStore`, `ModelLoadState`, `ModelMetrics`, `ModelFileStaging`, `MarkerPrintSheet`, `GallerySaver`, `AppPreferences`, `AppDirectories`, `ScreenCapture`, `AppLinks`, i18n em 8 idiomas (112 chaves com paridade completa) | 80 testes unitários (JUnit 5): determinismo do gerador, `anchorPosition`, paridade das chaves de i18n, folha de impressão, preferências, cópias de modelos |
| **2** ✅ | Câmera: `opencv_videoio.VideoCapture` (MSMF → DirectShow → padrão), thread de captura própria, enumeração e escolha de dispositivo, vídeo ao vivo na janela | `gradlew.bat test` + `gradlew.bat -q listCameras` (1 câmera, índice 0) + vídeo na janela |
| **3** ✅ | Detecção de marcador: ORB + `BFMatcher` (Hamming) + homografia RANSAC + `solvePnP` (IPPE), com a interface equivalente ao `AugmentedImage` (id, name, pose, extentX, extentZ, trackingState); caixa ciano sobre o marcador | 11 testes com quadro **sintetizado** (homografia conhecida): cantos, pose, distância real de uso (30–60 cm), sem falso positivo, `TRACKING → PAUSED → fora da cena` e falhas nativas tratadas — veja `docs/marker-detection.md` |
| **4** ✅ | Renderização: Filament (filament-kmp) com `SceneRenderer` (interface) e backend alternativo LWJGL/OpenGL; importação por diálogo nativo com validação por extensão; Assimp converte `.obj/.stl/.ply/.3mf` para GLB. **Feito até aqui:** `render/ModelPlacement.kt` — a matriz que leva a geometria do arquivo ao referencial do mundo (pose do marcador ∘ [ancoragem · rotação · escala]), 7 testes; `render/AssimpModelLoader.kt` — leitura dos seis formatos, caixa envolvente em unidades do arquivo, conversão para GLB ao lado do original e mensagens de erro tratáveis, 8 testes com os modelos do repositório; `render/SceneRenderer.kt` — a descrição de cena (`RenderScene`: fundo, marcador, modelo, ajustes) e o contrato do renderizador, com desenho **fora da tela** + leitura de pixels, 8 testes; `render/FilamentSpikeTest` — **o motor gráfico do Filament inicializa e desenha um quadro offscreen de 256×256 nesta máquina** (a decisão 2 deixou de ser suposição). **Feito também:** `render/FilamentRenderer.kt` — o backend Filament completo (cena, câmera com a projeção das intrínsecas da detecção, duas luzes direcionais, plano de fundo com o vídeo da câmera e o modelo pelo `gltfio`), com 3 testes que **desenham na GPU** e conferem os pixels. **E a ligação com a janela:** `SceneComposer` (laço de desenho numa thread própria), `ArSceneSection` (tela, botão de carregar e painel de ajustes), `ModelPicker` (diálogo nativo) e `SceneImageCache` (conversões de imagem) — 8 testes. | Os seis formatos (`.glb`, `.gltf`, `.obj`, `.stl`, `.ply`, `.3mf`) ancorados no marcador, com a mesma ancoragem do Android. Já validado: a conta de ancoragem (base no plano, elevação na normal, rotação no referencial do marcador), a conversão de cada formato (cabeçalho GLB conferido byte a byte) e a inicialização do motor gráfico e a **compilação do material de fundo em tempo de execução** (`MaterialSpikeTest`); e **validado na janela, em hardware real**: vídeo da webcam como fundo, caixa ciano sobre o marcador reconhecido e **modelo 3D carregado pelo diálogo do Windows aparecendo ancorado** |
| **5** 🟡 | Interface completa, **organizada em menus** (pedido do usuário, para separar assuntos): **Idioma** (a lista dos oito idiomas), **Modelo** (Carregar modelo · Ajustes do modelo · Escala automática · Redefinir configurações), **Câmera** (Reiniciar · Capturar tela), **Escolher marcador** (Gerenciar · Criar · Carregar marcador), **Ajuda** (Ajuda · Sobre · Qualidade da captura · Folha de impressão · Copiar diagnóstico) e **Sair**; mais o **rodapé de estado** com três linhas: programa/ambiente (versão, sistema, idioma, renderizador), modelo carregado (e a última mensagem) e marcador escolhido (com "●" quando a câmera está lendo a figura). **Feito:** `ui/ArMenuBar.kt` (os seis menus e os itens como dados), `ui/ArDialogs.kt` (ajuda, sobre, qualidade, **gerenciar marcador** e **criar marcador**), `ui/ArSceneSection.kt` (a tela, com o compositor como fonte única dos ajustes e o rodapé), `render/MarkerSelection.kt` (5 testes) e `render/AutoScale.kt` (4 testes). A tela de RA passou a ser a **tela principal** (a antiga tela de verificação das etapas 0–2 saiu, como o roadmap previa). **Conferido na janela:** Idioma, Reiniciar, Ajustes do modelo, Escala automática, Sair, o arrasto do modelo e o redimensionamento. **Falta:** conferir "Escolher marcador" (com duas figuras à mão), "Capturar tela" e o diálogo de ajustes; o zoom por `Ctrl`+roda **já foi confirmado na janela**. **Correções do teste em campo (decisões 21 e 22):** o painel de ajustes virou **diálogo** (aberto abaixo do vídeo, ele empurrava o modelo para fora da janela), a barra ficou na ordem **Modelo, Câmera, Escolher marcador, Idioma, Ajuda, Sair**, e entraram os botões **Ajustes do modelo**, **Escala automática** e **Capturar tela** ao lado de "Carregar modelo" | Reprodução de todos os fluxos do app Android |
| **6** ✅ | Interações: pinça em tela sensível ao toque, pinça em touchpad de precisão, `Ctrl`+roda, `+`/`-`, arrastar com o mouse (modo livre). **Feito:** `render/InteractiveInput.kt` (a conversão de gesto em tamanho e deslocamento, 10 testes: zoom multiplicativo de 10% por passo com a faixa inteira em ~49 passos, o fator da pinça e os passos da roda dando o mesmo resultado, arrasto proporcional ao tamanho do modelo, sinal do eixo vertical e limite de 50 cm), o painel de `RenderScene`/`ModelPlacement` com o **deslocamento no plano** (o arrasto não mexe na normal nem tira a base do plano) e a montagem na área do vídeo: `Ctrl`+roda, `+`/`=`/`-`, pinça de toque e arrasto. **O ponto de plataforma:** no desktop a pinça de touchpad de precisão **chega como `Ctrl`+roda** — os dois gestos são o mesmo caminho de código. **Conferido na janela:** `Ctrl`+roda (o zoom funciona) e o arrasto com o mouse — com **uma correção**: o sentido vertical estava invertido ("para a frente" levava o modelo "para trás") e foi corrigido pelo teste em campo, com o teste do sentido atualizado (decisão 21); **falta** reconferir o arrasto depois da correção. A pinça de touchpad **não é verificável nesta máquina** (sem touchpad e sem tela de toque) — e a **pinça de dois dedos na tela sensível ao toque não funciona**, nem tem como funcionar nesta plataforma: a entrada do Compose Desktop no Windows não recebe toque múltiplo (decisão 31, com a medição que comprova). Em lugar dela, o zoom por toque ganhou os botões `−`/`+` na fileira de comandos, além do cursor de tamanho nos ajustes e da escala automática. | Zoom entre 0,02 m e 2,0 m nos dois tipos de entrada |
| **7** | Captura de tela em `Pictures/ArkZ ARModelViewer/` **sem a interface** (render offscreen), com as três qualidades (1280, 1920, máxima) e som de disparo | PNG sem interface, com câmera + modelo |
| **8** | Instalador `.msi` (jpackage + WiX), ícone, atalho e menu Iniciar | Instalação em pasta limpa e execução pelo menu Iniciar |
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
15. **O referencial do marcador é: plano em XY e normal em Z.** (É a convenção **em vigor**, a
    que o teste em campo aprovou; a análise da decisão 34 mostra por que ela é questionável — o
    `solvePnP` devolve a normal no Y —, e a correção ficou **adiada** lá.) A primeira versão do port
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
    plano — três propriedades com teste. Observação de fidelidade: o clone de referência do
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
    **A correção foi escrita, medida e depois revertida.** Ela funcionava: o teste
    `girar o marcador pela sua normal…` passou a medir 0,2 m **sobre a normal** (antes caía no
    plano, com 1,2e-17). Mas, no mesmo pacote, ela mudou **duas coisas que o teste em campo já
    havia aprovado**: o modelo passou a carregar **deitado** na máquina do usuário (que então
    precisava de "Rotação em X = 90°") e o **arrasto vertical** trocou de eixo. O usuário pediu,
    com razão, **uma correção por vez**: *"Corrija apenas o trecho que envolve a posição do
    Modelo 3D, para que possamos confirmar se ficou correto e prosseguir com as outras
    correções."*
    Por isso, na **1.0.2**, o trecho de posição (ancoragem, apoio e arrasto) voltou ao estado
    aprovado em campo, e a correção do giro fica **preservada como pendência**:
    - o teste que a cobra está em `ModelPlacementTest`, **desativado** com `@Disabled` e o
      comentário da pendência — ele foi escrito para **falhar** no código de hoje; basta
      reativá-lo junto com a correção;
    - a análise acima é a receita da correção: apoio da base **na normal** (Y), sem a rotação de
      apoio, e o arrasto no plano verdadeiro (X e Z).
    **O que ainda não se sabe** — e é o que a próxima sessão com o notebook precisa decidir, com
    medição e não com suposição: por que, com o "para cima" sobre a normal (fisicamente o
    correto), o usuário viu o modelo **deitado**. As duas hipóteses em aberto são o eixo "para
    cima" do próprio **arquivo do modelo** (exportado de CAD, onde o Z é o habitual — a caixa
    envolvente do modelo usado dá 419 × 141 × 10 unidades, com a base em Y = 0, o que tanto
    pode ser um edifício Y-up quanto uma planta) e a **direção do arrasto** na tela.
