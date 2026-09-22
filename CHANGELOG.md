# Changelog

Todas as mudanças relevantes do **ArkZ ARModelViewer Desktop** são registradas neste
arquivo. O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/)
e o projeto usa [Versionamento Semântico](https://semver.org/lang/pt-BR/).

Antes da 1.0.0 não houve versão pública: o aplicativo foi construído por etapas
(descritas em [`docs/roadmap.md`](docs/roadmap.md)) e conferido em uso real antes de
ser publicado.

## [1.0.7] — 2026-09-22

**Primeiro release com instalador `.msi`: o modelo carrega DE PÉ, com a face virada para quem
olha e no MESMO SENTIDO do vídeo.**

A versão reúne as **três correções de posicionamento do modelo** — a rotação inicial de volta ao
zero (decisão 37), a correspondência de eixos entre o arquivo e o plano da figura (decisão 38) e o
espelho vertical do quadro (decisão 39) — e o **instalador Windows** (jpackage + WiX, com atalho no
menu Iniciar e `upgradeUuid` fixo, ao lado do pacote portátil que o teste em campo usou). O `.msi` monta
a própria imagem e **não** leva as três DLLs do Visual C++ que o pacote portátil leva ao lado do `.exe`:
para quem instala, o **Redistributable 2015-2022** é o único requisito (o portátil não precisa de nada).

- **O modelo aparecia de cabeça para baixo em relação ao vídeo — e a causa era antiga** (decisão 39 do
  roadmap). O quadro era lido com as linhas **invertidas** e as coordenadas de textura do plano de fundo
  punham a linha de cima da imagem no topo do mundo com `v = 1`: as duas inversões se cancelavam **só para o
  vídeo** (que sempre apareceu certo) e deixavam tudo o que é ancorado no **mundo** — o **modelo** —
  **espelhado na vertical**. Enquanto o modelo carregava deitado (até a 1.0.6) o espelho caía no plano
  horizontal e não aparecia; com o modelo de pé ele ficou óbvio: *"de cabeça para baixo, e 180° de giro do
  marcador consertam"* é a assinatura de um espelho vertical.
- **A correção é um par:** a leitura passa a entregar o quadro **como o motor devolve** — neste backend
  (Vulkan) ele já vem **de cima para baixo** — e o plano de fundo leva `v = 0` embaixo e `v = 1` em cima. O
  vídeo **continua** de pé e o modelo passa a cair **do mesmo lado** que ele.
- **Medido na GPU** (o `House.glb` a 0,5 m, num quadro de 96×96, com deslocamentos conhecidos nos eixos do
  marcador):

  | Deslocamento no marcador | Antes | Agora |
  |---|---|---|
  | **Z** +0,1 m (a "altura NA imagem", para baixo) | o modelo ia para **CIMA** (linha 59,7 → 27,2) | vai para **BAIXO** (67,3 → 99,8) |
  | **X** +0,1 m (a largura) | à direita | à direita |
  | **Y** +0,1 m (a normal) | aproxima | aproxima |
  | a **porta vermelha** da casa (a parte de baixo dela) | desenhada **acima** do centro do modelo | **abaixo** |

- **Conferido por mutação:** com o par antigo de volta (a leitura invertida e as texturas como estavam), os
  **dois** testes do modelo falham — *"o cubo do telhado tem de ficar ACIMA do centro do modelo (cubo em
  58,0, modelo em 47,5 linhas)"*, contra **37,0** no estado correto, e *"o Z do marcador tem de levar o modelo
  para BAIXO: (47,5, 47,5) → (47,5, 24,0)"* — e o teste do **vídeo continua passando**, que é a assinatura do
  defeito. (Reconferido em 22/09, com o `House.glb` re-exportado; na medição original a **porta vermelha**
  fazia esta medida.)
- **O arrasto não mudou.** O eixo (a normal) e o sentido ("arrastar para baixo traz o modelo para a frente",
  decisão 36) seguem iguais; o que o espelho invertia era o **quadro**, e com ele a leitura do movimento. Se
  o campo ainda pedir o contrário, é o sinal de **uma linha** em `InteractiveInput.panOffset` (com o teste do
  sentido guardando a decisão).
- **Testes:** `os eixos do marcador caem no quadro do mesmo lado que o video mostra` e a medida do **cubo
  do telhado** acima do centro do modelo, em `FilamentRendererTest` — junto da orientação do **vídeo**, que
  continua coberta pelo teste que já existia. A **decisão 39** do roadmap registra a medição e a lição.
  A suíte fecha a **1.0.7** com **210 testes**.

**O modelo carrega DE PÉ com a face virada para quem olha — e o giro do marcador gira o modelo no eixo
correspondente.**

- **O relato:** com o "Marcador A" impresso e **virado para o usuário** (no desktop a webcam fica no
  monitor, e a folha é erguida à frente dela), o modelo aparecia **deitado** e, girado o marcador em
  torno da sua normal, girava no **Y** em vez do **Z**.
- **A causa não estava no arquivo do modelo nem na rotação inicial do painel:** o `ModelPlacement`
  ancorava o modelo com a correspondência de **identidade** entre o referencial do arquivo e o do
  marcador, e o **+Y** do arquivo (a altura, no glTF) caía na **NORMAL** do marcador. Isso só dá "em
  pé" quando a normal é a vertical do mundo — com a folha **apoiada na mesa**. Com a folha **de frente
  para a câmera** a normal é horizontal: o modelo carrega deitado de costas e o giro da folha o roda no
  eixo errado.
- **A correção é uma correspondência de eixos fixa** (`ModelPlacement.MODEL_ORIENTATION`, −90° em X):
  o **plano XY do arquivo** (a face do modelo — no `House.glb`, a face da **porta vermelha**) é o
  **plano da figura**, e o **+Z do arquivo** é a **normal**. O modelo fica de pé, olhando para quem
  está vendo, e girar a folha pela normal gira o **+Z do arquivo**, eixo por eixo.
- **A ancoragem acompanha:** ela é calculada sobre a caixa **já orientada**
  (`ModelPlacement.orientedBounds`), então a face de **trás** apoia no plano da figura e o modelo fica
  centrado na largura e na altura da imagem. Os cursores passam a girar nos **eixos do arquivo**
  (X = largura, Y = "para cima", Z = o "frente") e continuam abrindo em **zero** — "em pé, de frente",
  como na 1.0.7.
- **Medido na GPU**, com a folha de frente para a câmera e os modelos do próprio repositório: o
  `House.glb` (a casa de referência, re-exportada em **+Y para cima**: 5,0 × 5,0 × 4,5, centrada
  desenha **1915 pixels** iluminados, **de pé e com a porta virada para a câmera**; a chapa do
  `ArkZ_logo.stl` (altura no **Z** e espessura no **Y** — a convenção do STL) fica de perfil e mostra a
  face com **90° no cursor X**.
- **A consequência, registrada:** o modelo é **colado à figura** — não é orientado pela vertical do
  mundo. Com a folha apoiada na mesa ele aparece **deitado com a face para cima**. Um modo "vertical do
  mundo" fica como **possibilidade** (decisões 37 e 38), e não como pendência.
- **Testes:** `de frente para a camera o modelo aparece de pe com a frente virada para quem olha`,
  `girar o marcador pela normal gira o modelo no proprio eixo Z, sem deita-lo`, `a caixa orientada leva
  a altura do arquivo para a altura da imagem`, `um arquivo exportado com o Z para cima fica de pe com
  menos 90 graus no cursor X` e `com a folha apoiada na mesa o modelo fica deitado com a face para
  cima` (`ModelPlacementTest`), `o House glb de referencia tem a altura no Y e a porta na face mais a
  frente` (`AssimpModelLoaderTest`) e o teste de GPU atualizado — que agora usa a casa de referência e
  cobra que o **cubo do telhado** apareça acima do centro do modelo (`FilamentRendererTest`).
  **Conferido por mutação:** com a correspondência de identidade de volta, **9 testes falham** — entre
  eles o de GPU `o modelo aparece ancorado no marcador e some sem rastreio` (medido de novo, com o
  `House.glb` re-exportado; na medição original a mensagem era *"a porta vermelha da casa tem de
  aparecer virada para a câmera"*). A **decisão 38**
  do roadmap registra a medição e o que se abre mão.

**A rotação inicial volta a ser zero: o modelo carrega de pé sobre a figura e o giro da folha gira
o modelo em torno da vertical — em vez de rolá-lo dentro do plano do papel.**

- **Os 90° em X que a 1.0.5 pôs como rotação inicial estavam errados, e a medição mostra por quê.**
  A decisão de lá partiu da premissa de que os arquivos desta família têm o **Z para cima** — e a
  premissa **não se confirmou**: medidos, o `.obj` do logotipo do repositório tem a altura no **Y**
  (140,9 de 419,0 de largura, com 10,0 de espessura no Z) e o `House.glb` do Blender mede
  5,0 × 4,5 × 5,0 com a porta na face **+Z** e o telhado no topo do **Y** (é a casa de referência
  dos testes de GPU desde a decisão 38). Como o `ModelPlacement` ancora o modelo na
  orientação do arquivo, **sem apoio embutido** (decisão 34), o **zero dos cursores é o "de pé"** —
  e os 90° em X **deitavam** o modelo.
- **A medição foi feita na GPU, com a situação do relato** (folha deitada na mesa, webcam de frente
  e um pouco acima, a 0,6 m; o `House.glb` em 0,13 m), girando a folha de 90° em 90°:

  | Rotação inicial | Altura do topo **pela normal da folha** | O que o giro da folha faz |
  |---|---|---|
  | **90° em X** (1.0.5–1.0.6) | **0,000 m** — o modelo dentro do plano | **rola** o modelo dentro do plano: o topo andou 115 px de lado (de 320 para 428 num quadro de 640 px) |
  | **zero** (agora) | **0,117 m** — a altura inteira do modelo | **gira** o modelo em torno da vertical: o topo caiu no **mesmo pixel** (320, 73) nos quatro giros |

- **É essa a diferença entre o que o usuário viu e o que ele pediu.** Girar a folha impressa sobre a
  mesa tem de girar o modelo como num **prato giratório** — e só existe prato giratório se o modelo
  estiver **de pé**: deitado no plano da folha, o giro o **rola** ali dentro (e, num modelo chapa
  como o logotipo, quase não se vê giro nenhum — era este o relato original).
- **Os dois ajustes de teste da 1.0.6 (`DEFAULT_SEGUIR_A_FOLHA` e `DEFAULT_EM_PE_NA_VERTICAL`) foram
  removidos.** Eles existiam como parâmetros de `ModelPlacement.worldMatrix` **sem implementação
  nenhuma**: qualquer que fosse o valor, a matriz saía igual — era por isso que **nenhuma** das
  combinações conferidas em campo mudava o que se via na janela, e era por isso que **dois testes
  falhavam** desde então (eles cobravam o comportamento que não estava lá). Com o modelo de pé, o
  giro da folha já é o prato giratório pedido; um modo "vertical do mundo" só faria falta para uma
  folha **inclinada de propósito** (apoiada num livro, por exemplo), e fica registrado como
  possibilidade na **decisão 37**, não como pendência.
- **A ancoragem, o referencial do marcador, o arrasto e os cursores ficaram intocados.** O zero dos
  cursores já era o "em pé" que o `ModelPlacement` documenta desde a decisão 34; o que mudou foi
  **qual valor o painel mostra ao abrir** e para onde o **Redefinir** volta. Um arquivo com outro
  eixo para cima continua pedindo o cursor **Rotação X** em 90°, como no app Android.
- **205 testes até aqui**, com um novo que fixa a situação do relato: `com a folha deitada na mesa, girar a
  folha gira o modelo em torno da vertical` (`ModelPlacementTest`) mede a altura do topo **pela
  normal da folha** (a altura inteira = de pé; zero = deitado) e a altura do topo no mundo nos
  quatro giros. Conferido por **mutação**: com os 90° em X de volta, ele falha com *"subiu
  3,7e-9 m pela normal, e não 0,13 m"*. A **decisão 37** do roadmap registra a medição e a correção.

## [1.0.6] — 2026-09-21

**Etapa 2 (de duas) da correção do posicionamento do modelo: o arrasto vertical.**

- **O arrasto vertical passou a mover o modelo no frente–trás** — a normal do marcador —, e não
  na altura da imagem. Com a figura de frente para o usuário (o caso do relato), o eixo que o
  arrasto escrevia, o **Z** ("a altura NA imagem"), **é o eixo vertical do mundo**: arrastar para
  cima e para baixo subia e descia o modelo em vez de movê-lo para a frente e para trás. Agora o
  arrasto vertical escreve o **Y** — o mesmo eixo do cursor de **Elevação**, com o mesmo sentido
  (arrastar para baixo traz o modelo para a frente) — e o **Z fica em zero**. O **arrasto
  horizontal não mudou**: continua a largura da figura.
- **A correção saiu de uma medição, e não de uma suposição.** Com o "Marcador A" de frente para a
  câmera, a 21 cm (quadro sintetizado, detector de verdade), a pose entrega **Y = a normal**
  (apontando para a câmera) e **Z = a altura NA imagem** (apontando para o chão do mundo). Um
  arrasto de 0,1 m num modelo de 50 cm andava **92 mm na vertical do mundo** e só 38 mm em
  profundidade; escrito no Y, anda **92 mm frente–trás** e 38 mm na vertical. A hipótese
  registrada na 1.0.5 — de que o arrasto estivesse escrevendo a normal — foi medida e **não** se
  confirmou: o código andava no plano; era o plano que, naquela pose, é vertical no mundo.
- **Rotação inicial, ancoragem e referencial do marcador intocados** — o que estava aprovado em
  campo (o modelo de pé, a base apoiada e o giro em torno da normal) continua igual.
- **204 testes**, com três novos: a medição do referencial que a pose entrega, a direção do
  arrasto vertical (frente–trás, e não cima–baixo) e o arrasto que **não** gira com os cursores de
  rotação. A **decisão 36** do roadmap registra a medição, a correção e o que se abre mão.

## [1.0.5] — 2026-09-21

**Etapa 1 (de duas) da correção do posicionamento do modelo.**

- **O modelo passa a carregar de pé: a rotação inicial é 90° em X.** Os arquivos desta família
  (CAD/SketchUp, e os convertidos pelo Assimp de `.obj`/`.stl`/`.ply`/`.3mf`) têm o **Z para
  cima**; com a rotação inicial em zero o modelo aparecia **deitado** sobre a figura e o usuário
  tinha de girar o cursor X à mão **em cada modelo carregado**. Agora
  `RenderScene.DEFAULT_ROTATION_DEGREES` é esse valor — o cursor de **Rotação X** abre em 90° e
  o **Redefinir** volta para lá. É o **mesmo número** que se usa no app Android para os mesmos
  arquivos, e o usuário continua livre para ajustar X/Y/Z a partir dele.
- **201 testes**, com um teste cobrindo o valor inicial e a volta do "Redefinir".
- **Em aberto (etapa 2, a pedido):** o **arrasto vertical** ainda anda na **normal** do marcador
  — o modelo sobe e desce saindo do papel. Ele deve andar no plano da figura, como já acontece
  no eixo X.

## [1.0.4] — 2026-09-21

- **A matemática de ancoragem passou a ser, linha por linha, a do app Android**
  (`ARViewScreen.kt` → `ModelMetrics.anchorPosition`): `x = -center.x · escala`,
  `y = -(center.y - halfExtent.y) · escala + elevação` e `z = -center.z · escala`, no referencial
  do ARCore para imagens (**X = largura, Y = a NORMAL, Z = altura NA imagem**), o mesmo que
  `Pose` e o `FilamentRenderer` já usavam.
- **A rotação de apoio embutida foi removida**: como no Android, o modelo carrega na orientação
  do arquivo e quem a ajusta são os cursores **Rotação X/Y/Z** (`Rotation(rotationX, rotationY,
  rotationZ)` lá). Nenhum apoio escondido mexe na altura de carga nem no eixo do giro — e é por
  isso que agora **girar a folha impressa pela normal gira o modelo em torno de si, no mesmo
  sentido da folha**.
- O **arrasto** (recurso desta versão, que o Android não tem) anda no **plano** da figura: X e Z,
  com a normal (Y) intocada.
- **200 testes**, incluindo o que gira o marcador em torno da normal e cobra o "para cima" sobre
  ela. A **decisão 34** do roadmap fecha com a referência do projeto Android.

## [1.0.3] — 2026-09-21

- **Girar o marcador pela normal agora gira o modelo em torno de si, no mesmo sentido do
  marcador** — em vez de deitá-lo. Duas correções juntas:
  1. o referencial do marcador passou a ser o que a detecção e o renderizador realmente usam —
     **X = largura, Y = NORMAL (sai do papel), Z = altura NA imagem** —, então o "para cima" do
     modelo vai para a normal e o arrasto anda no plano do papel (X e Z), com a normal intocada;
  2. o **eixo "para cima" do arquivo** passou a ser tratado: os modelos desta família
     (CAD/SketchUp, e os que o Assimp converte de `.obj`/`.stl`/`.ply`/`.3mf`) têm o **Z para
     cima**, e o `ModelPlacement` aplica `Rx(−90°)` como apoio, com o `ModelMetrics` apoiando a
     base no **Z do arquivo**. O modelo carrega **de pé**, sem o "Rotação em X = 90°" à mão.
- Teste que cobre o relato: `ModelPlacementTest` gira o marcador em torno da normal, de 30° em
  30°, e cobra que o "para cima" do modelo continue sobre a normal. **200 testes.**
- **Em aberto (decisão 34 do roadmap):** o eixo "para cima" do arquivo é uma **suposição** (Z).
  Se um modelo glTF Y-up aparecer deitado, o que falta é a escolha explícita desse eixo.

## [1.0.2] — 2026-09-21

- **O trecho de posição do modelo voltou ao comportamento aprovado em campo** — ancoragem,
  apoio da base e arrasto com o mouse. A correção de referencial da 1.0.1 havia mudado, junto
  com o giro pela normal, duas coisas que já estavam aprovadas: o modelo passou a carregar
  **deitado** (na máquina do usuário, exigindo "Rotação em X = 90°") e o arrasto vertical
  trocou de eixo. Como o pedido é corrigir **um comportamento por vez**, a posição foi
  restaurada e a correção do giro ficou adiada.
- A correção do giro pela normal fica **preservada como pendência**: o teste que a cobra está
  em `ModelPlacementTest` (desativado com `@Disabled`, escrito para falhar no código de hoje) e
  a receita da correção está na **decisão 34** do roadmap. **199 testes.**

## [1.0.1] — 2026-09-21

Correção de um defeito de **referencial** na ancoragem do modelo, relatado em uso real.

### Corrigido

- **O modelo girava em torno do eixo errado quando o marcador era girado pela sua normal.**
  O referencial do marcador é o do ARCore — **X = largura, Y = NORMAL, Z = altura NA imagem** —
  e o código supunha o plano em XY: a "rotação de apoio" jogava o "para cima" do modelo
  (o +Y do glTF) para dentro do plano, deixando-o deitado sobre a figura. A rotação de apoio
  foi removida, a ancoragem passou a apoiar a base **na normal** e o arrasto passou a andar no
  plano (X e Z, com a normal sempre intocada): arrastar não tira mais o modelo do papel.
- **Um teste que reproduz o relato**: `ModelPlacementTest` gira o marcador em torno da normal,
  de 30° em 30°, e cobra que o "para cima" do modelo continue sobre a normal — ele falhava
  antes da correção.
- Os testes que codificavam o referencial invertido foram reescritos e a documentação
  (`docs/rendering.md`, mais os KDocs de `ModelMetrics` e `InteractiveInput`), corrigida.
  **200 testes.** O sentido do arrasto vertical é o único comportamento que pede
  reconferência em campo (veja a decisão 34 do roadmap).

## [1.0.0] — 2026-09-21

Primeira versão pública: o fluxo completo de realidade aumentada no Windows —
carregar um modelo 3D, apontar a webcam para um marcador de imagem impresso e ver o
modelo fixo sobre ele, com ajuste fino de rotação, tamanho e elevação.

### Adicionado

**Realidade aumentada**
- Renderização dos modelos ancorados em **marcadores de imagem** pelo **Filament**
  (`filament-kmp`), com a cena desenhada **fora da tela** e apresentada pelo Compose.
- Detecção de marcador própria (**ORB + `BFMatcher` + homografia RANSAC + `solvePnP`**),
  com a **mesma interface** do `AugmentedImage` do ARCore (id, nome, pose, extensão,
  estado de rastreio), caixa ciano sobre a figura e `PAUSED` guardando a última pose.
- **Marcadores personalizados**: criar (o app desenha a figura e exporta o PNG de
  impressão em 15 cm), carregar de uma imagem e remover — além dos dois embutidos.
- **Escala automática** pela largura do marcador detectado.

**Modelos**
- Leitura de `.glb`, `.gltf`, `.obj`, `.ply`, `.stl` e `.3mf`, pelo diálogo nativo do
  Windows; os formatos não-glTF são convertidos para GLB pelo **Assimp** (LWJGL), ao
  lado do arquivo preparado, com validação por extensão e mensagens de erro tratáveis.
- Estados de carregamento (carregando / pronto / falhou) na linha de estado e no log.

**Câmera**
- Captura por **OpenCV** (`opencv_videoio`, backend MSMF com DirectShow como
  alternativa), enumeração e escolha de dispositivo, e tela própria quando não há
  câmera (ou quando ela está ocupada por outro programa).

**Interface (Compose for Desktop / Material 3)**
- Barra de menus com as seis áreas: **Modelo**, **Câmera**, **Marcador**, **Idioma**,
  **Ajuda** e **Sair** (a reorganização veio do teste em campo).
- Diálogo de **ajustes do modelo arrastável**, com rotação X/Y/Z, tamanho (maior
  dimensão, em metros) e elevação Z **em tempo real**, e o botão Redefinir.
- **Zoom e deslocamento**: roda do mouse, `Ctrl`+roda (que é como chega a pinça de
  touchpad no Windows), teclas `+`/`=`/`-`, botões `−`/`+` na barra e arrasto do modelo
  no plano com o botão esquerdo.
- **Modo tela cheia** (com a barra que aparece ao passar o ponteiro), linha de estado
  com as mensagens temporizadas do app Android e diagnóstico copiável para suporte.
- A interface **nunca** entra na captura de tela.

**Idiomas**
- Os mesmos **8 idiomas** do app Android (pt-BR, pt-PT, en, es, fr, de, it e zh-CN) e a
  opção "Sistema", com troca em tempo de execução e **116 chaves com paridade completa**
  entre os bundles (verificado por teste).

**Empacotamento e diagnóstico**
- **Pacote portátil** (`createDistributable`): roda sem instalar nada, com o runtime do
  Java e as bibliotecas nativas ao lado do executável.
- Diagnóstico de modelo por linha de comando (`--check-model`, ou `gradlew modelCheck`),
  que converte um arquivo de verdade e grava um relatório com versões, pastas, presença
  das DLLs e o motivo de qualquer falha.
- `nativeDistributions` com `vendor`, `copyright` e a **licença** (o `.msi`, pela tarefa
  `packageMsi`, ainda não foi conferido numa máquina limpa).

**Licença**
- **GPL-3.0** (`LICENSE`), `NOTICE` com as licenças de terceiros, e o aviso de direito
  autoral e de licença **visível na interface** (diálogo Sobre) e nos metadados do
  executável — como a própria GPL-3.0 pede para interfaces interativas.

### Corrigido

Correções que vieram do **teste em campo, no notebook do cliente** (todas registradas
com o porquê em `docs/roadmap.md`):

- O sentido vertical do arrasto estava invertido (puxar "para a frente" levava o modelo
  "para trás"); o teste de sinal foi atualizado junto.
- O painel de ajustes virou **diálogo arrastável** — ele cobria a área do vídeo.
- A fileira de botões **quebra linha** em vez de rolar (a rolagem escondia ações).
- **Duas causas de "não carrega o modelo"** no pacote, invisíveis no `gradlew run`:
  faltava o módulo **`jdk.unsupported`** no runtime do jpackage (o LWJGL usa
  `sun.misc.Unsafe`) e faltavam as **DLLs do Visual C++** ao lado do executável (a
  `assimp.dll` importa `msvcp140.dll`). O pacote agora passa `--check-model` com código
  de saída `0`.
- A **pinça de dois dedos na tela sensível ao toque não funciona** neste aplicativo —
  nem tem como funcionar: a entrada do Compose Desktop no Windows não recebe eventos de
  toque (o toque chega convertido em mouse, de um contato só). O gesto continua no
  código e o log passa a registrar quantos pontos tocam o vídeo; o zoom de quem usa a
  tela ganhou os botões `−`/`+`. O histórico está na **decisão 31** do roadmap e na
  seção *Toque, touchpad e mouse* do README.

### Qualidade

- **199 testes unitários** (JUnit 5), incluindo três que desenham quadros **de verdade
  na GPU** e conferem os pixels, testes de detecção com quadros sintetizados de
  homografia conhecida, a paridade dos 8 bundles de idioma e dois que guardam o aviso de
  direito autoral e de licença no diálogo Sobre (GPL-3.0, seção 5).

[1.0.7]: https://github.com/em-rezende/Windows-ArkZ-ARModelViewer/releases/tag/v1.0.7
[1.0.0]: https://github.com/em-rezende/Windows-ArkZ-ARModelViewer/releases/tag/v1.0.0
