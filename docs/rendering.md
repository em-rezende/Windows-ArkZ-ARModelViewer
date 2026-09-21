# Renderização — a cena de RA no desktop

Este documento explica como o aplicativo desenha a cena de realidade aumentada no
Windows: o caminho do quadro da webcam até a tela, a matemática da câmera, a carga dos
modelos e **as armadilhas que só aparecem na tela** (e que os testes de GPU pegam).

Para a detecção de marcador (que produz a pose usada aqui), veja
[`marker-detection.md`](marker-detection.md).

## 1. O caminho de um quadro

```
webcam ──▶ Mat (OpenCV) ──▶ BackgroundFrame (RGBA, de cima para baixo)
                                    │
marcador ──▶ DetectedMarker ──┐     │
                              ▼     ▼
                        RenderScene (descrição imutável do quadro)
                                    │
                        FilamentRenderer.render(scene)
                                    │
        ┌───────────────────────────┴────────────────────────────┐
        │ câmera (projeção das intrínsecas, olhando para −Z)      │
        │   ├── plano de fundo: vídeo da webcam a 50 m            │
        │   └── modelo: .glb com a matriz do ModelPlacement       │
        └───────────────────────────┬────────────────────────────┘
                                    ▼
                SwapChain offscreen ──▶ readPixels ──▶ SceneFrame
                                                           │
                                          Compose desenha a imagem na tela
```

Três decisões estruturam esse desenho:

1. **A cena é uma descrição, não um estado espalhado.** O `RenderScene` carrega fundo,
   marcador, modelo e ajustes; o renderizador não guarda estado de aplicação. Isso mantém
   o backend trocável e torna a conta de ancoragem testável sem GPU.
2. **O desenho é feito fora da tela** (`SwapChain` offscreen + `readPixels`). É o que
   encaixa no Compose sem embutir janela nativa, o que faz a captura de tela sem interface
   (etapa 7) ser a mesma função num alvo maior, e o que permite testar o caminho inteiro
   dentro de um teste comum.
3. **A projeção sai das intrínsecas da detecção.** A pose do marcador vem de um `solvePnP`
   com a matriz da câmera; o renderizador usa **os mesmos números**
   ([`CameraProjection`](../src/main/kotlin/com/arkz/armodelviewer/render/CameraProjection.kt)).
   Um campo de visão aproximado (o caminho comum) deslocaria o modelo do marcador — pouco
   no centro da imagem, muito nas bordas.

## 2. As duas metades da matemática

| Peça | Pergunta que responde | Onde |
|---|---|---|
| `CameraProjection` | Onde o ponto 3D cai **na imagem**? (`u = fx·X/Z + cx`) | `render/CameraProjection.kt` |
| `ModelPlacement` | Onde a geometria do arquivo cai **no mundo**? | `render/ModelPlacement.kt` |

**Referenciais** — o referencial do **marcador** é o do ARCore, e é o que o `solvePnP` produz a
partir das quatro quinas do objeto (`MarkerDetector.markerObjectPointsMat`, em que o Y é
**zero**): **X = largura**, **Y = NORMAL** (sai do plano da figura, na direção de quem olha) e
**Z = altura NA imagem**. A correção deste referencial é a **decisão 34** do roadmap: antes
dela o código supunha o plano em XY, e o modelo girava em torno do eixo errado quando a folha
impressa girava.

* **câmera**: +X para a direita, +Y para cima, olhando para **−Z** (um ponto à frente tem z
  negativo);
* **marcador**: +X = largura da imagem, **+Y = a normal** e **+Z = altura NA imagem** (para
  baixo). O **plano da figura é o XZ**, e é por isso que a **elevação** — o controle chamado
  `Elevação Z` na interface, nome herdado do app Android — é o único ajuste que tira o modelo
  do plano: ela desloca o modelo ao longo da normal, que é o eixo **Y**.

> **Cuidado com a pose "identidade" ao escrever testes**: um marcador com rotação de
> identidade fica **de perfil** para a câmera (o plano dele contém a direção de visão). Um
> marcador de frente para a câmera tem a rotação que leva o **+Y** dele (a normal) ao **+Z** do
> mundo. Um teste chegou a medir "zero pixel de modelo" por causa disso — a pose precisa ser
> fisicamente possível.

A conta do modelo é `mundo = pose do marcador ∘ [ancoragem · rotação · escala]`, com a
escala normalizando a maior dimensão do arquivo para o tamanho escolhido (0,2 m por
padrão, o mesmo do Android).

## 3. Os modelos: seis formatos, um caminho de desenho

O Filament lê **glTF/GLB** e nada mais. Os outros quatro formatos passam pelo Assimp
(`AssimpModelLoader`), que grava um `.glb` **ao lado** do arquivo escolhido — a mesma
ideia do SceneView no Android.

| Etapa | Ferramenta | Detalhe que importa |
|---|---|---|
| Ler `.obj/.stl/.ply/.3mf` | Assimp (LWJGL) | `aiProcess_PreTransformVertices` (nós já aplicados) + `aiProcess_GenBoundingBoxes` |
| Normais | Assimp | `aiProcess_GenNormals` — o Filament **descarta primitivas sem normais** |
| Escrever `.glb` | Assimp (`glb2`) | grava ao lado do original, com o mesmo nome |
| Desenhar | Filament `gltfio` | *uber shaders* embutidos na biblioteca nativa |
| Medir | `ModelBounds` | caixa em **unidades do arquivo** → `ModelMetrics` → normalização |

### As duas armadilhas do "carrega e não aparece"

1. **Caixa envolvente zerada** (sem `aiProcess_GenBoundingBoxes`): a normalização de
   tamanho vira 1 e um modelo em milímetros é desenhado com 1000 m de altura.
2. **Primitivas sem normais** (sem `aiProcess_GenNormals`): o arquivo é válido, o modelo
   carrega, e o renderizador simplesmente **não desenha nada**, sem erro nenhum.

Nenhuma das duas aparece em teste de lógica: as duas são pegas pelos testes que conferem os
**pixels** ou o **conteúdo do GLB**.

## 4. Materiais

O único material escrito à mão é o do plano de fundo: superfície não iluminada, com a
textura do vídeo da câmera. Ele é **compilado em tempo de execução** pelo módulo `filamat`
— os jars do binding não trazem nenhum `.filamat` e o `matc` só existe como executável.

Duas regras não óbvias:

* compilar para **todas** as plataformas e APIs (`Platform.ALL` + `TargetApi.ALL`): o motor
  escolhe o backend sozinho (aqui, Vulkan) e um material só-GLSL **aborta o processo** com
  *"not built for any of the Vulkan backend's supported shader languages (SPIR-V)"*;
* o shader precisa de `prepareMaterial(material)` mesmo sendo não iluminado, e
  `require(VertexAttribute.UV0)` para as coordenadas de textura existirem.

## 5. A cena montada no Filament

| Elemento | Como é criado | Detalhe |
|---|---|---|
| Câmera | `setCustomProjection(matriz, 0,05 m, 100 m)` + `lookAt` | matriz = `CameraProjection.fromIntrinsics` |
| Plano de fundo | quad unitário + `TransformManager` | escala = `frameSizeAt(...)` a 50 m; **caixa declarada** (`Box`) |
| Textura do vídeo | `Texture` `SRGB8_A8`, recriada só quando o tamanho muda | codificação sRGB correta (vídeo não "lavado") |
| Modelo | `gltfio` (`AssetLoader` + `UberShaderProvider`), bytes do `.glb` | entra na cena **só** com o marcador rastreado |
| Posição | `TransformManager.setTransform(instância, matriz)` | matriz do `ModelPlacement` |
| Luzes | duas direcionais (90 000 e 25 000 lux) sem sombras | o Filament expõe a cena como fotografia ao sol |

**Ordem de desmontagem** (importa): a entidade do plano de fundo é destruída **antes** da
textura, dos vértices e do material. Destruir um recurso com um renderizável vivo aborta o
processo (*"destroying MaterialInstance ... which is still in use by Renderable"*). No fim,
`engine.destroy()` — sem ele o processo não encerra no Windows.

## 6. Como isso é validado

Três testes desenham **de verdade** na GPU e conferem os pixels
([`FilamentRendererTest`](../src/test/kotlin/com/arkz/armodelviewer/render/FilamentRendererTest.kt)):

| Teste | O que ele pega |
|---|---|
| fundo com metade vermelha em cima e azul embaixo | vídeo de cabeça para baixo, canais trocados, plano do tamanho errado |
| modelo ancorado no marcador, **iluminado**, e ausente sem rastreio | gltfio, matriz de ancoragem, luzes e a regra do rastreio |
| redimensionar o quadro e trocar de modelo | ciclo de vida do `SwapChain` e do asset |

Os quadros são pequenos (64×64 e 96×96) de propósito: o que está em teste é a **cadeia**,
não a qualidade da imagem. As cores são conferidas por **dominância de canal**, e não por
valor exato, porque o caminho sRGB do Filament não promete o byte idêntico de volta — mas
promete a ordem dos canais e a posição das coisas.

Além deles: `FilamentSpikeTest` (o motor inicializa e desenha offscreen),
`MaterialSpikeTest` (o material compila), `CameraProjectionTest` (a projeção concorda com o
modelo pinhole da detecção) e `AssimpModelLoaderTest` (a conversão de cada formato e o
conteúdo do GLB gerado).

## 7. Como a cena chega à tela (e o que ainda falta)

A ligação com a interface é feita por quatro peças pequenas, cada uma com um papel claro:

| Peça | Papel |
|---|---|
| `ui/SceneImage.kt` | converte o quadro da câmera em RGBA (a textura do fundo) e o quadro desenhado em imagem do Compose, com buffers reaproveitados |
| `render/SceneComposer.kt` | o laço de desenho fora da thread de interface, com canal **conflado** ("o último quadro vale") e o renderizador injetado por fábrica — o que o torna testável sem GPU |
| `ui/ArSceneSection.kt` | a tela: seção da câmera + carregar modelo + painel de ajustes (tamanho, rotação e elevação em tempo real) + linha de estado |
| `ui/ModelPicker.kt` | o diálogo **nativo do Windows** e a preparação do arquivo (cópia + conversão para GLB), fora da thread de interface |

O tamanho do quadro e os intrínsecos saem do **quadro real** que a webcam entrega: é o mesmo
par de números que a detecção usou, e é o que mantém o modelo assentado no marcador.

Um detalhe que custa caro se esquecido: **o motor do Filament não aceita chamadas
concorrentes**. Criar o renderizador, ajustar o tamanho, trocar os intrínsecos e desenhar
acontecem todos na **mesma thread** (`arkz-render`, dentro do `SceneComposer`). A primeira
versão usava o conjunto de threads padrão (`Dispatchers.Default`), e o aplicativo morria
com asserção nativa **no primeiro quadro** — sem exceção, sem mensagem na tela e sem rastro
além de um *dump* do Windows: no log, a última linha era `Renderizador iniciado`. Se algum
dia aparecer outro aborto do tipo, o primeiro lugar a olhar é se alguma chamada ao motor
escapou da thread do renderizador.

O custo é a cópia do quadro a cada desenho (≈3,7 MB em 1280×720), com buffer reaproveitado;
a leitura é **síncrona** (`flushAndWait`), o que mantém a captura de tela simples e correta.
Se a medição em uso real mostrar espera, o caminho natural é ler o quadro anterior enquanto
o novo é desenhado — a mudança é interna ao renderizador, porque a interface já recebe um
quadro por chamada.

A barra de menus, os ajustes em tempo real e a linha de estado (etapa 5) e os gestos de zoom
e arrasto (etapa 6) estão fechados e conferidos em uso real; continuam abertas as etapas 7
(captura de tela sem interface, com as três qualidades) e 8 (instalador `.msi`). O **backend
alternativo em LWJGL/OpenGL** deixou de ser necessário: ele era o plano B para o caso de o
binding comunitário do Filament não funcionar, e os testes de GPU provaram que ele funciona.
O `SceneRenderer` continua sendo uma interface justamente para que esse plano B siga barato,
se algum dia for preciso.

## 8. Gestos: quem entrega o quê

Os números do zoom e do deslocamento têm uma única fonte — `render/InteractiveInput.kt`, com
os seus 10 testes. Quem os alimenta é a área do vídeo, com `detectTransformGestures` mais um
observador de ponteiros. O que cada entrada entrega **não é óbvio**, e é diferente por
plataforma:

| Entrada | Como chega ao gesto | Resultado |
|---|---|---|
| Roda do mouse | evento de rolagem, **sem** `Ctrl` | o vídeo não rola a página: o passo vira zoom |
| Touchpad (dois dedos) | o Windows converte a pinça em `Ctrl`+roda | mesmo caminho da roda |
| `Ctrl`+roda | rolagem com `Ctrl` | zoom; a área do vídeo consome o evento (decisão 20) |
| Teclado | `+` / `=` / `-` | zoom por passo |
| Botões `−` e `+` | clique | zoom por passo — é o caminho de quem usa tela sensível ao toque |
| Arrastar | botão esquerdo pressionado e movido | deslocamento do modelo **no plano** do marcador |
| Pinça de dois dedos na tela sensível ao toque | **não chega**: o Windows entrega o toque como mouse, de um contato só | nada (decisão 31) |

O log registra quantos pontos estão em contato com o vídeo, **a cada mudança**
(`Pontos em contato com o video: N (Mouse|Touch).`), na passagem `Initial` do evento: o
suficiente para provar, na máquina do usuário, se a plataforma entrega um ou dois ponteiros,
e cedo o bastante para não interferir nos gestos que rodam logo depois na cadeia.
