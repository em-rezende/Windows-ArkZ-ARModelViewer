# Política de segurança

## Versões com suporte

| Versão | Suporte |
|---|---|
| 1.0.x (atual) | ✅ correções de segurança |
| anterior à 1.0.0 | ❌ não houve versão pública |

## Como relatar uma vulnerabilidade

**Não abra uma issue pública** para falhas de segurança. Envie um e-mail para
**emrezende@gmail.com** com:

* descrição do problema e do impacto (o que um atacante consegue fazer);
* versão do aplicativo (menu **Ajuda ▸ Sobre**) e versão do Windows;
* passos para reproduzir (ou um PoC/vídeo);
* se possível, uma sugestão de correção.

Confirmação de recebimento em até **7 dias**. A correção sai como uma nova versão nas
[releases](https://github.com/em-rezende/Windows-ArkZ-ARModelViewer/releases) e é
registrada no [CHANGELOG](CHANGELOG.md) — com crédito ao relator, se ele quiser.

## Escopo

O aplicativo **não coleta nem envia dados**: não há contas, servidor, anúncios nem
telemetria. Os modelos carregados, as capturas de tela, os marcadores personalizados e o
log ficam no próprio computador (`%LOCALAPPDATA%\ArkZ ARModelViewer` e
`%USERPROFILE%\Pictures\ArkZ ARModelViewer`), e a única entrada externa é a **webcam** —
usada apenas no quadro atual, para reconhecer o marcador.

Relatos que fazem sentido aqui:

* *crash* ou consumo de memória descontrolado com arquivos de modelo malformados
  (`.glb`, `.obj`, `.ply`, `.stl`, `.3mf`): o carregamento passa por bibliotecas
  **nativas** (Filament, gltfio e Assimp), que são a superfície mais sensível do projeto;
* caminhos que escapem das pastas do aplicativo ao salvar capturas/marcadores ou ao abrir
  arquivos escolhidos no diálogo nativo;
* problemas nos recursos externos (abrir site, abrir pasta, enviar e-mail) — por exemplo,
  abrir um endereço diferente do que está escrito na tela;
* qualquer forma de execução de código a partir de um arquivo de modelo ou de uma imagem
  de marcador (o aplicativo **não** interpreta scripts embutidos nesses arquivos).
