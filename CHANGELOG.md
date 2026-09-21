# Changelog

Todas as mudanças relevantes do **ArkZ ARModelViewer Desktop** são registradas neste
arquivo. O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/)
e o projeto usa [Versionamento Semântico](https://semver.org/lang/pt-BR/).

Antes da 1.0.0 não houve versão pública: o aplicativo foi construído por etapas
(descritas em [`docs/roadmap.md`](docs/roadmap.md)) e conferido em uso real antes de
ser publicado.

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

[1.0.0]: https://github.com/em-rezende/Windows-ArkZ-ARModelViewer/releases/tag/v1.0.0
