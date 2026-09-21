# Como contribuir

Obrigado pelo interesse. Este é o port para Windows do
[Android-ArkZ-ARModelViewer](https://github.com/em-rezende/Android-ArkZ-ARModelViewer),
feito com **fidelidade comportamental**: as mesmas ações, os mesmos textos e os mesmos
números do aplicativo Android.

## Ambiente

| Item | Versão |
|---|---|
| JDK | 25 (toolchain do Gradle; o Filament no desktop exige Java 22+) |
| Gradle | 9.7.1 (vem pelo wrapper) |
| WiX Toolset | 3.14+ — só para gerar o `.msi` |

```powershell
.\gradlew.bat test        # testes (JUnit 5)
.\gradlew.bat run         # abre a janela
```

Os detalhes de build, empacotamento, scripts de apoio e o diagnóstico de modelo estão em
[`docs/development.md`](docs/development.md).

## Como o código é escrito

* **Kotlin idiomático**, com KDoc **em português** nas funções públicas.
* Os comentários explicam **por que** a decisão foi tomada (e o que aconteceu quando ela
  não foi) — não o que a linha faz. Comentário que só repete o código é ruído.
* **Nenhuma dependência nova sem decisão registrada** em
  [`docs/roadmap.md`](docs/roadmap.md): a lista de bibliotecas foi escolhida item por
  item, com as alternativas descartadas e o motivo de cada descarte.
* Cada arquivo-fonte começa com o aviso de direito autoral e de licença (GPL-3.0).

## Antes de enviar

1. `.\gradlew.bat test` verde.
2. Se mexeu na interface, abra a janela (`run`) e clique no que mudou: há testes que
   passam com a tela quebrada.
3. Se acrescentou uma chave de idioma, acrescente-a **nos 8 bundles** e atualize o número
   verificado em `I18nBundlesTest` — o próprio teste diz onde.
4. Se a mudança revelou uma armadilha ou tomou uma decisão, registre no `docs/` (e no
   `CHANGELOG.md`). Neste projeto, a documentação vem **antes** do código.

## Armadilha dos testes (JUnit 5)

Um teste de JUnit 5 **não pode devolver valor**: se o último comando do corpo for uma
função que devolve algo (`assertNotNull`, `assertFailsWith`, `map`…), o JUnit **ignora o
método** — ele não roda, não falha e não avisa. Antes de confiar num teste novo, confira
que ele aparece no relatório com a contagem de `@Test` do arquivo (o exemplo está em
`docs/development.md`).

## Licença

Ao contribuir, você concorda em ceder a sua contribuição sob a **GPL-3.0**, a mesma
licença deste repositório.
