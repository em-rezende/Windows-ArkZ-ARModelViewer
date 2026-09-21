# Detecção de marcadores

Como o app reconhece um marcador impresso **sem o ARCore**, e por que este caminho foi
escolhido.

## O problema

No Android, o reconhecimento era do ARCore (*Augmented Images*): o app registrava as
imagens de referência num `AugmentedImageDatabase` e o ARCore devolvia, a cada quadro,
as imagens reconhecidas com a pose. O ARCore é exclusivo do Android — e o SDK do
SceneView só existe para Android (veja o README, decisão 1). No desktop, essa camada
precisa ser reimplementada.

## A escolha: casamento de pontos de interesse

| Opção | Por que não |
|---|---|
| **ArUco** | Preciso e rápido, mas exige um dicionário **específico** (ArUco/AprilTag). Os marcadores deste app são os pseudo-QR que ele mesmo desenha e imagens escolhidas pelo usuário — nenhum dicionário os reconhece, e migrar invalidaria todo marcador já impresso. |
| **ML Kit / Vision** | Não existe ML Kit para JVM desktop; o equivalente seria portar um modelo de detecção de features, com peso e complexidade muito maiores. |
| **Casamento de pontos de interesse** ✅ | É exatamente o que o ARCore faz com *augmented images*: pontos de interesse + homografia. Os **mesmos marcadores impressos continuam valendo**, inclusive os pseudo-QR (alto contraste e muitos cantos, justamente o perfil ideal). |

O caminho por ArUco continua no radar como **modo alternativo**, para quem aceita
imprimir um marcador específico — mas não substitui o padrão.

## O caminho, quadro a quadro

```
quadro da câmera (BufferedImage)
        │  luminância (0,299 R · 0,587 G · 0,114 B)
        ▼
   Mat CV_8UC1 ──► ORB (1500 pontos, 8 níveis, FAST 20)
        │                  │ descritores binários
        │                  ▼
        │          BFMatcher (Hamming) contra os descritores de cada marcador
        │                  │ knnMatch k=2 + teste de razão de Lowe (0,75)
        │                  ▼
        │          findHomography (RANSAC, 3 px) ──► 4 cantos no quadro
        │                  │ validação: área ≥ 1%, convexo, arestas na proporção
        │                  ▼
        │          solvePnP (IPPE, alvo plano) com os intrínsecos
        ▼                  ▼
   estado de rastreio ◄── pose (centerPose) + suavização dos cantos
```

Roda **numa thread própria** (nunca na de interface): o ORB num quadro de 1280×720
custa algumas dezenas de milissegundos. Os arranjos de trabalho são reaproveitados e a
detecção consome o `StateFlow` **conflado** da câmera — se um quadro atrasar, o próximo
substitui o anterior em vez de formar fila (o mesmo "último quadro vale" do ARCore).

## A escala da imagem de referência (o detalhe que decide se funciona na mão do usuário)

O ORB é invariante a escala **dentro do alcance da pirâmide**: com 8 níveis de fator 1,2,
isso é ~3,6×. A imagem de referência deste app tem 1000 px; um marcador de 15 cm visto a
50 cm ocupa cerca de **170 px** num quadro de 720p — uma diferença de **6×**, fora do
alcance. O resultado era uma detecção que só funcionava com a figura colada na webcam
(enquanto os testes sintéticos, com o marcador ocupando ~400 px, passavam).

Por isso a referência é **reduzida** para no máximo 600 px (`INTER_AREA`, uma vez por
marcador, na abertura) antes de extrair os pontos de interesse: a faixa coberta passa a
ser de ~170 px a ~2100 px, folgadamente a distância de uso (30–60 cm). A redução exige um
cuidado: as **quinas da referência** passam a ser as da imagem reduzida — é com elas que a
homografia leva os pontos da referência até o quadro da câmera.

Este é o tipo de defeito que só aparece com hardware real: por isso existe o teste
`reconhece o marcador na distancia de uso tipica (30 a 60 cm)`, que monta um quadro de
1280×720 com o marcador a ~55 cm (≈170 px) e exige o reconhecimento.

## Mapeamento ARCore → desktop

| ARCore (Android) | Desktop | Observação |
|---|---|---|
| `AugmentedImage.name` | `DetectedMarker.name` | identificador do marcador cadastrado. |
| `AugmentedImage.index` | `DetectedMarker.index` | posição na lista cadastrada. |
| `AugmentedImage.centerPose` | `DetectedMarker.centerPose` | mesmo referencial de imagem (+X largura, +Y normal, +Z altura), para reaproveitar a ancoragem do `ModelMetrics`. |
| `AugmentedImage.extentX` / `extentZ` | `DetectedMarker.extentX` / `extentZ` | largura física real; os marcadores são quadrados, então os dois são iguais. |
| `TrackingState.TRACKING/PAUSED/STOPPED` | idem (`ar.TrackingState`) | `PAUSED` mantém a última pose por ~0,5 s (15 quadros) para o conteúdo não piscar. |
| `TrackingMethod.FULL_TRACKING/LAST_KNOWN_POSE` | idem | informa se a pose é medida ou herdada. |
| `frame.getUpdatedTrackables` | `detect(frame)` | aqui a detecção é por quadro, não por callback do SDK. |
| `distinctByPose` (na tela do Android) | `dedupeByPose` (no detector) | duas referências casando com a mesma figura física viram uma detecção só. |

## Intrínsecos da câmera

Sem ARCore não há calibração automática. O padrão é estimar pela **resolução e pelo
campo de visão horizontal (60°)**, com pixels quadrados e centro no meio da imagem.
Isso acerta a pose a alguns por cento — suficiente para o modelo parecer "no lugar", e
o painel de ajustes cobre a diferença. A calibração real com tabuleiro de xadrez
(`tools/calibrate-camera.ps1`, etapa 9) substitui a estimativa quando existir.

## Limitações conhecidas (e o que fazer)

| Limitação | Consequência | Mitigação |
|---|---|---|
| A câmera é a origem do mundo | não há rastreio de movimento (o desktop não se move) | é o comportamento correto para um computador: a pose é sempre relativa à webcam. |
| Imagem de referência sem textura | um marcador "liso" nunca é reconhecido | o `MarkerGenerator` já produz alto contraste; ao carregar a imagem do usuário, o app avisa (o marcador entra na lista, mas não casa). |
| Marcador pequeno no quadro (< 1% da área) | detecção instável | o texto de Ajuda orienta a figura a ocupar boa parte do quadro; o limiar é `MIN_AREA_RATIO`. |
| Mudança brusca de iluminação | 1–2 quadros de instabilidade | suavização dos cantos (média exponencial) e janela de pausa de 15 quadros. |
| Sem SLAM (sem planos/oclusão) | nada é desenhado atrás do marcador | limitação inerente ao cenário; o modelo fica ancorado no plano do marcador. |
| FOV estimado, não calibrado | erro de escala de poucos por cento na distância | `DEFAULT_HORIZONTAL_FOV_DEGREES` ou calibração real. |
| Marcador muito perto (< ~20 cm) | fica fora do alcance da pirâmide do ORB (a referência é reduzida para 600 px) | a Ajuda orienta a distância de 30–60 cm; `REFERENCE_MAX_SIDE` ajusta a faixa. |

## Ajustes (onde mexer)

| Constante | Arquivo | Efeito |
|---|---|---|
| `FEATURE_COUNT`, `FAST_THRESHOLD` | `ar/MarkerDetector.kt` | mais pontos/limiar menor = reconhece mais longe, ao custo de tempo por quadro. |
| `ratioTest` (0,75), `minMatchCount` (12) | `ar/MarkerDetector.kt` | rigor do casamento: valores maiores = menos falsos positivos e menos detecções. |
| `MIN_AREA_RATIO` (0,01), `MIN_SIDE_RATIO` (0,2) | `ar/MarkerDetector.kt` | tamanho/formato mínimo aceito para um quadrilátero. |
| `PAUSE_FRAMES` (15), `MARKER_SMOOTHING` (0,5) | `ar/MarkerDetector.kt` | permanência em `PAUSED` e suavidade do contorno. |
| `DEFAULT_HORIZONTAL_FOV_DEGREES` (60) | `ar/CameraIntrinsics.kt` | escala da pose. |

## Como isso é testado sem câmera

`src/test/kotlin/.../ar/MarkerDetectorTest.kt` **sintetiza o quadro**: a imagem do
marcador é deformada por uma homografia conhecida para dentro de um quadro de 640×480, e
o teste cobra os quatro cantos (tolerância de 8 px, contra os 3 px do RANSAC), a pose à
frente da câmera na distância esperada pela geometria (f · largura / pixels), a rotação
ortonormal, a ausência de falso positivo num quadro em branco e a sequência
`TRACKING → PAUSED → fora da cena`. Há também:

* o quadro **na distância de uso** (1280×720 com o marcador a ~55 cm), que foi o que pegou
  o problema de escala da referência;
* uma referência **sem textura**, para garantir que o app não quebra com um marcador ruim;
* um quadro **minúsculo** e o uso do detector **depois de fechado**, que provam que uma
  falha nativa do OpenCV vira "não detectado" em vez de derrubar o aplicativo.
