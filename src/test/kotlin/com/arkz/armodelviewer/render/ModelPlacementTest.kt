/*
 * ArkZ ARModelViewer Desktop — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 */

package com.arkz.armodelviewer.render

import com.arkz.armodelviewer.ar.Pose
import com.arkz.armodelviewer.model.ModelMetrics
import com.arkz.armodelviewer.model.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes da ancoragem do modelo.
 *
 * A conta é curta — e é a que decide se o modelo aparece **no lugar certo**. Um erro de
 * referencial aqui produz os dois sintomas clássicos: o modelo carrega e não aparece
 * (deslocado para longe) ou aparece de cabeça para baixo / enterrado no marcador. Como
 * tudo é aritmética pura, dá para conferir ponto por ponto, sem GPU.
 *
 * As contas geométricas usam a **pose de identidade** (o referencial do marcador é o do
 * mundo), o que deixa as asserções legíveis no próprio referencial do marcador — o mesmo
 * em que o `ModelMetrics` trabalha.
 *
 * Convenção do referencial do marcador (a do ARCore para imagens, mantida de propósito):
 * **X** = largura da imagem, **Y** = normal (sai do plano), **Z** = altura NA imagem — e o
 * **plano** da figura é o **XZ**.
 *
 * ## O que estes testes cobram da correspondência entre o ARQUIVO e o marcador (decisão 38)
 *
 * O `ModelPlacement` adota uma correspondência **fixa** entre os eixos do arquivo (glTF: **+Y**
 * para cima, **+Z** para a frente) e os do marcador: o plano **XY** do arquivo **é** o plano da
 * figura, e o **+Z** do arquivo (o "frente" — no `House.glb` do repositório, a face da porta
 * vermelha) **é** a normal. É medido aqui de três formas:
 *
 *  1. **de frente para a câmera** (a situação do desktop) o modelo aparece **de pé** — o +Y do
 *     arquivo no +Y do **mundo** — e com a face virada para quem olha (o +Z do arquivo no +Z do
 *     mundo, a direção da câmera);
 *  2. **girando a folha pela normal** a frente não sai do eixo do giro e o "para cima" acompanha o
 *     giro da folha (o modelo gira no **próprio eixo Z**);
 *  3. **a ancoragem** apoia a face de **trás** no plano (a espessura do modelo sai da figura) e
 *     centra o modelo na largura e na altura da imagem.
 */
class ModelPlacementTest {

    /**
     * Modelo de teste em unidades do arquivo: **20 × 20 × 10**, com a base no `y = 0` (a convenção
     * do glTF para o que fica "em pé") e a face da frente no `z = +5` — a forma do `House.glb` (a
     * casa de referência do repositório), em escala.
     */
    private val metrics = ModelMetrics(
        center = Vec3(0f, 10f, 0f),
        halfExtent = Vec3(10f, 10f, 5f),
    )

    /** A casa de referência (`3d_models/House.glb`), como o `AssimpModelLoaderTest` a mede. */
    private val houseMetrics = ModelMetrics(
        center = Vec3(0f, 2.25f, 0f),
        halfExtent = Vec3(2.5f, 2.25f, 2.5f),
    )

    /**
     * Marcador **de frente para a câmera**, a 50 cm — o caso do relato de campo (o "Marcador A"
     * virado para quem olha).
     *
     * Os eixos do marcador no mundo são as **colunas** da rotação (o `Pose` guarda a rotação em
     * ordem de linha e a aplica como `mundo = R · ponto`): a largura para a direita `(1, 0, 0)`, a
     * **normal** para fora da tela — na direção de quem olha — `(0, 0, 1)` e a altura NA imagem
     * para baixo `(0, −1, 0)`, com o plano da figura no `z = −0,5`.
     */
    private val facingCamera = Pose(
        rotation = doubleArrayOf(
            1.0, 0.0, 0.0,
            0.0, 0.0, -1.0,
            0.0, 1.0, 0.0,
        ),
        translation = Vec3(0f, 0f, -0.5f),
    )

    private val identityPose = Pose(
        rotation = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
        translation = Vec3.ZERO,
    )

    @Test
    fun `a escala normaliza a maior dimensao para o tamanho escolhido`() {
        // Maior dimensão do arquivo: 20 unidades (meia-extensão 10 em X e Y).
        val scale = ModelPlacement.scaleFor(metrics, sizeMeters = 0.2f)

        assertEquals(0.01f, scale, 1e-6f)
        assertEquals(0.2f, metrics.largestDimension * scale, 1e-5f)
    }

    @Test
    fun `o modelo carrega em pe, com a frente virada para quem olha e o fundo no plano`() {
        val matrix = ModelPlacement.worldMatrix(
            markerPose = identityPose,
            metrics = metrics,
            sizeMeters = 0.2f,
            rotationDegrees = Vec3.ZERO,
            elevationMeters = 0f,
        )

        // O "para cima" do arquivo (+Y) vai para a **altura NA imagem** — o −Z do marcador —, e
        // não para a normal: é o que faz o modelo carregar **de pé** em vez de deitado de costas.
        assertVector(
            Vec3(0f, 0f, -1f),
            direction(matrix, Vec3(0f, 20f, 0f)),
            message = "o 'para cima' do arquivo tem de ser a altura NA imagem",
        )
        // E o quanto ele sobe na imagem é o tamanho escolhido (0,2 m = a maior dimensão do arquivo).
        assertEquals(
            0.2f,
            Math.abs(segment(matrix, Vec3(0f, 20f, 0f)).z),
            1e-5f,
            "a altura do modelo na imagem tem de ser o tamanho escolhido",
        )

        // A face da frente (o +Z do arquivo — no `House.glb`, a porta) sai pela NORMAL, na direção
        // de quem olha; a face de trás (o −Z do arquivo) apoia no plano da figura.
        assertVector(
            Vec3(0f, 1f, 0f),
            direction(matrix, Vec3(0f, 0f, 5f)),
            message = "a frente do arquivo tem de ser a normal do marcador",
        )
        assertEquals(
            0f,
            ModelPlacement.apply(matrix, Vec3(0f, 0f, -5f)).y,
            1e-5f,
            "o fundo do modelo apoia no plano da figura (o eixo da normal)",
        )

        // Centralizado na largura e na altura da imagem; a espessura fica toda à frente do plano
        // (a caixa é 20 × 20 × 10: a frente a 0,1 m e o fundo em 0,0 m).
        val center = ModelPlacement.apply(matrix, metrics.center)
        assertEquals(0f, center.x, 1e-5f, "centralizado na largura")
        assertEquals(0f, center.z, 1e-5f, "centralizado na altura da imagem")
        assertEquals(0.05f, center.y, 1e-5f, "o centro fica a meia espessura à frente do plano")
    }

    @Test
    fun `de frente para a camera o modelo aparece de pe com a frente virada para quem olha`() {
        // O caso do desktop — o marcador na mão, virado para a webcam —, e é a situação do relato.
        // O mundo é o da câmera: +X para a direita, +Y para cima e +Z para fora da tela, na direção
        // de quem olha.
        val matrix = ModelPlacement.worldMatrix(
            markerPose = facingCamera,
            metrics = houseMetrics,
            sizeMeters = 0.13f,
            rotationDegrees = Vec3.ZERO,
            elevationMeters = 0f,
        )

        // DE PÉ: o "para cima" do arquivo é o +Y do MUNDO. Com a correspondência de identidade (até
        // a 1.0.7) ele caía na normal — que aqui é o +Z do mundo — e a casa carregava deitada de
        // costas, com o telhado apontando para a câmera.
        assertVector(
            Vec3(0f, 1f, 0f),
            direction(matrix, Vec3(0f, houseMetrics.halfExtent.y * 2f, 0f)),
            message = "o modelo tem de carregar de pé",
        )

        // A PORTA (o +Z do arquivo) olha para quem está vendo: no mundo, o +Z da câmera.
        assertVector(
            Vec3(0f, 0f, 1f),
            direction(matrix, Vec3(0f, 0f, houseMetrics.halfExtent.z)),
            message = "a porta tem de ficar virada para a câmera",
        )

        // E a largura do arquivo é a largura da imagem — sem espelhamento nenhum no caminho.
        assertVector(
            Vec3(1f, 0f, 0f),
            direction(matrix, Vec3(houseMetrics.halfExtent.x, 0f, 0f)),
            message = "a largura do arquivo tem de ser a largura da figura",
        )

        // A parede de trás apoia no plano da figura (o z = −0,5 do marcador) e a frente sai dele na
        // direção de quem olha: 0,13 m à frente (o tamanho escolhido, que é a maior dimensão).
        assertEquals(
            -0.5f,
            ModelPlacement.apply(matrix, Vec3(0f, 0f, -2.5f)).z,
            1e-5f,
            "a parede de trás apoia no plano da figura",
        )
        assertEquals(
            -0.37f,
            ModelPlacement.apply(matrix, Vec3(0f, 0f, 2.5f)).z,
            1e-5f,
            "a frente sai da figura na direção de quem olha",
        )
    }

    @Test
    fun `a elevacao desloca o modelo no eixo da normal do marcador`() {
        val point = Vec3(0f, 0f, 0f)
        val base = ModelPlacement.apply(
            ModelPlacement.worldMatrix(
                identityPose, metrics, sizeMeters = 0.2f, rotationDegrees = Vec3.ZERO,
                elevationMeters = 0f,
            ),
            point,
        )
        val lifted = ModelPlacement.apply(
            ModelPlacement.worldMatrix(
                identityPose, metrics, sizeMeters = 0.2f, rotationDegrees = Vec3.ZERO,
                elevationMeters = 0.5f,
            ),
            point,
        )

        // A normal é o **Y** do referencial do marcador (X = largura, Y = normal, Z = altura NA
        // imagem): é o único eixo em que a elevação mexe. E é o **+Z do arquivo** depois da
        // correspondência de eixos do `ModelPlacement` — por isso o rótulo "Elevação Z" da
        // interface (herdado do app Android) finalmente nomeia o eixo do próprio modelo: a
        // elevação afasta o modelo da folha, na direção de quem olha.
        assertEquals(base.x, lifted.x, 1e-5f)
        assertEquals(base.z, lifted.z, 1e-5f)
        assertEquals(base.y + 0.5f, lifted.y, 1e-5f)
    }

    @Test
    fun `o arrasto desloca o modelo nos eixos do marcador, junto da ancoragem`() {
        val centered = ModelPlacement.worldMatrix(
            identityPose, metrics, sizeMeters = 0.2f, rotationDegrees = Vec3.ZERO,
            elevationMeters = 0f,
        )
        val dragged = ModelPlacement.worldMatrix(
            identityPose, metrics, sizeMeters = 0.2f, rotationDegrees = Vec3.ZERO,
            elevationMeters = 0f,
            offsetMeters = Vec3(0.2f, 0.1f, -0.1f),
        )

        val center = ModelPlacement.apply(centered, metrics.center)
        val moved = ModelPlacement.apply(dragged, metrics.center)

        // O deslocamento entra como translação nos eixos do marcador, somada à ancoragem: o X é a
        // largura, o **Y é a normal** (o frente–trás do arrasto — decisão 36, e o eixo em que o
        // **+Z do arquivo** sai da figura) e o Z a altura NA imagem. É a mesma conta da ancoragem, e
        // é por isso que ela acompanha o tamanho do modelo.
        assertEquals(center.x + 0.2f, moved.x, 1e-5f)
        assertEquals(center.y + 0.1f, moved.y, 1e-5f, "o Y é a normal: o frente–trás do arrasto")
        assertEquals(center.z - 0.1f, moved.z, 1e-5f)
    }

    @Test
    fun `o arrasto nao gira com os cursores de rotacao`() {
        // Quem arrasta move o modelo **sobre a figura**: o deslocamento é aplicado fora da rotação
        // dos cursores (`local = T · R · S`), então girar o modelo não muda o eixo em que ele anda.
        // Sem isso, um modelo girado sairia andando de lado ao arrastar.
        val semGiro = ModelPlacement.worldMatrix(
            identityPose, metrics, sizeMeters = 0.2f, rotationDegrees = Vec3.ZERO,
            elevationMeters = 0f, offsetMeters = Vec3(0.2f, 0f, 0f),
        )
        val comGiro = ModelPlacement.worldMatrix(
            identityPose, metrics, sizeMeters = 0.2f, rotationDegrees = Vec3(0f, 90f, 0f),
            elevationMeters = 0f, offsetMeters = Vec3(0.2f, 0f, 0f),
        )

        // A posição do modelo no mundo depois do mesmo arrasto de 0,2 m na largura do marcador.
        val andou = ModelPlacement.apply(semGiro, Vec3.ZERO)
        val andouGirado = ModelPlacement.apply(comGiro, Vec3.ZERO)

        assertEquals(andou.x, andouGirado.x, 1e-5f)
        assertEquals(andou.y, andouGirado.y, 1e-5f)
        assertEquals(andou.z, andouGirado.z, 1e-5f)
    }

    @Test
    fun `a rotacao dos sliders acontece nos eixos do arquivo, os que o marcador adota`() {
        fun comCursores(graus: Vec3) = ModelPlacement.worldMatrix(
            identityPose, metrics, sizeMeters = 0.2f, rotationDegrees = graus, elevationMeters = 0f,
        )

        // O slider X gira no **X do arquivo** (a largura da figura). Com 90° o "para cima" do modelo
        // sai da imagem e cai na NORMAL: o modelo deita para dentro do plano, apoiado na face da
        // frente. É também a leitura que explica a correspondência antiga: 90° no cursor X
        // **desfazem** o −90° da orientação do app (é o "deitado de costas" da 1.0.7).
        assertVector(
            Vec3(0f, 0f, -1f),
            direction(comCursores(Vec3.ZERO), Vec3(0f, 20f, 0f)),
            message = "sem giro: em pé, com o 'para cima' na altura da imagem",
        )
        assertVector(
            Vec3(0f, 1f, 0f),
            direction(comCursores(Vec3(90f, 0f, 0f)), Vec3(0f, 20f, 0f)),
            message = "90° em X: o 'para cima' caiu na normal",
        )

        // O slider Z gira em torno do **Z do arquivo** — o eixo que sai da folha (a normal). O
        // "para cima" do modelo, que estava na imagem, roda para a largura da figura.
        assertVector(
            Vec3(-1f, 0f, 0f),
            direction(comCursores(Vec3(0f, 0f, 90f)), Vec3(0f, 20f, 0f)),
            message = "90° em Z: girou em torno do eixo que sai da folha",
        )
    }

    @Test
    fun `um arquivo exportado com o Z para cima fica de pe com menos 90 graus no cursor X`() {
        // A correspondência do app supõe o arquivo glTF (+Y para cima, +Z para a frente). Um arquivo
        // exportado com o **Z para cima** (vários CAD e o SketchUp) é o mesmo modelo girado 90° — e o
        // painel resolve: com −90° no cursor X o "para cima" dele (o +Z) vai para a altura NA imagem,
        // como o +Y do arquivo padrão.
        val matrix = ModelPlacement.worldMatrix(
            identityPose, metrics, sizeMeters = 0.2f, rotationDegrees = Vec3(-90f, 0f, 0f),
            elevationMeters = 0f,
        )

        assertVector(
            Vec3(0f, 0f, -1f),
            direction(matrix, Vec3(0f, 0f, 5f)),
            message = "o +Z deste arquivo tem de ir para a altura NA imagem",
        )
    }

    @Test
    fun `a caixa orientada leva a altura do arquivo para a altura da imagem`() {
        val placed = ModelPlacement.orientedBounds(metrics, ModelPlacement.MODEL_ORIENTATION)

        // O centro gira (o meio da altura do arquivo, 10 unidades acima da base, sobe na imagem: o
        // −Z do marcador) e a meia-extensão troca de eixo: no marcador, o "Y" da caixa passa a ser a
        // **espessura** do arquivo (5) e o "Z" a **altura** dele (10).
        assertEquals(0f, placed.center.x, 1e-5f)
        assertEquals(0f, placed.center.y, 1e-5f, "o centro do arquivo cai no plano da figura")
        assertEquals(-10f, placed.center.z, 1e-5f, "e sobe na imagem (−Z do marcador)")
        assertEquals(10f, placed.halfExtent.x, 1e-5f)
        assertEquals(5f, placed.halfExtent.y, 1e-5f, "a espessura do arquivo é o que sai do plano")
        assertEquals(10f, placed.halfExtent.z, 1e-5f, "e a altura dele é a altura NA imagem")
    }

    @Test
    fun `o modelo acompanha a pose do marcador`() {
        // Marcador à frente da câmera (Z negativo, como no ARCore) e deslocado.
        val pose = Pose(
            rotation = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
            translation = Vec3(0.3f, -0.2f, -0.6f),
        )

        val matrix = ModelPlacement.worldMatrix(
            pose, metrics, sizeMeters = 0.2f, rotationDegrees = Vec3.ZERO, elevationMeters = 0f,
        )

        // A translação da matriz é a posição do marcador somada à **ancoragem**: com este modelo
        // (base no y = 0 e espessura centrada no z), ela vale (0; +0,05; +0,10) — o **fundo** dele
        // apoia no plano da figura (0,05 m = a meia espessura pela normal) e ele fica centrado na
        // altura da imagem (0,10 m = a metade da altura do arquivo × a escala de 0,01 m/unidade).
        assertEquals(0.3f, matrix[12], 1e-5f)
        assertEquals(-0.2f + 0.05f, matrix[13], 1e-5f)
        assertEquals(-0.6f + 0.10f, matrix[14], 1e-5f)
    }

    @Test
    fun `a multiplicacao de matrizes respeita a ordem`() {
        val translate = ModelPlacement.translation(Vec3(1f, 0f, 0f))
        val scale = ModelPlacement.uniformScale(2f)

        // Escalar e depois transladar ≠ transladar e depois escalar: é exatamente por
        // isso que a ordem (T · R · S) está documentada e testada.
        val scaleThenTranslate = ModelPlacement.apply(
            ModelPlacement.multiply(translate, scale),
            Vec3(1f, 0f, 0f),
        )
        val translateThenScale = ModelPlacement.apply(
            ModelPlacement.multiply(scale, translate),
            Vec3(1f, 0f, 0f),
        )

        assertEquals(3f, scaleThenTranslate.x, 1e-6f)
        assertEquals(4f, translateThenScale.x, 1e-6f)
    }

    @Test
    fun `a rotacao de euler preserva o tamanho e nao torce o modelo`() {
        val rotation = ModelPlacement.eulerRotation(Vec3(30f, -45f, 60f))
        val point = ModelPlacement.apply(rotation, Vec3(0.3f, -0.4f, 0.5f))
        val length = kotlin.math.sqrt(point.x * point.x + point.y * point.y + point.z * point.z)

        assertEquals(0.7071f, length, 1e-4f)
        assertTrue(
            rotation[3] == 0f && rotation[7] == 0f && rotation[11] == 0f,
            "a última linha de uma rotação em coluna-maior deve ser (0,0,0,1)",
        )
    }

    @Test
    fun `girar o marcador pela normal gira o modelo no proprio eixo Z, sem deita-lo`() {
        // A situação de campo: a folha girada na própria face (na mão, ou sobre a mesa). No
        // referencial do marcador isso é girar em torno do eixo **Y** — a normal —, e no do arquivo
        // é girar em torno do **Z** (o "nariz" do modelo: no `House.glb`, a face da porta), porque é
        // ele que a correspondência de eixos do `ModelPlacement` põe na normal.
        //
        // O que se cobra, em cada giro: a frente do modelo **não sai** da normal (ela é o eixo do
        // giro) e o "para cima" acompanha a folha — ele é a **altura NA imagem** (−Z do marcador),
        // que é justamente o que o giro muda. É o modelo girando junto, eixo por eixo.
        for (passo in 0..11) {
            val graus = passo * 30.0
            val pose = spinAroundNormal(graus)
            val matrix = ModelPlacement.worldMatrix(
                markerPose = pose,
                metrics = metrics,
                sizeMeters = 0.2f,
                rotationDegrees = Vec3.ZERO,
                elevationMeters = 0f,
            )

            // Os eixos do marcador NO MUNDO são as **colunas** da rotação da pose (o `Pose` guarda a
            // rotação em ordem de LINHA e a aplica como `mundo = R · ponto`): a 2ª coluna é a normal
            // e a 3ª é a altura NA imagem.
            val normal = Vec3(pose[0, 1].toFloat(), pose[1, 1].toFloat(), pose[2, 1].toFloat())
            val alturaNaImagem = Vec3(
                -pose[0, 2].toFloat(),
                -pose[1, 2].toFloat(),
                -pose[2, 2].toFloat(),
            )

            assertVector(
                normal,
                direction(matrix, Vec3(0f, 0f, 5f)),
                message = "com a folha a $graus° a frente saiu da normal",
            )
            assertVector(
                alturaNaImagem,
                direction(matrix, Vec3(0f, 20f, 0f)),
                message = "com a folha a $graus° o 'para cima' não acompanhou a folha",
            )
        }

        // A normal do marcador é a 2ª coluna da rotação da pose — o eixo pelo qual a folha gira.
        val pose = spinAroundNormal(0.0)
        assertEquals(1.0, pose[1, 1], 1e-6, "a coluna da normal da pose deveria ser +Y")
    }


    @Test
    fun `com a folha apoiada na mesa o modelo fica deitado com a face para cima`() {
        // A consequência da correspondência fixa, medida para não surpreender: com a folha
        // **apoiada na mesa** a normal é (quase) a vertical do mundo, e é ela que a face do modelo
        // adota — o modelo aparece **deitado com a face para cima** (no `House.glb`, a porta
        // apontando para o teto), girando no plano da folha quando ela gira.
        //
        // É o preço de o modelo ser **colado à figura** — e não orientado pela vertical do mundo —, e
        // é o inverso do caso de campo: a folha **de frente para a webcam**, que é o que a decisão
        // 38 corrigiu (a folha na mesa era o único caso em que a correspondência de identidade, da
        // 1.0.7, dava "em pé").
        val pose = sheetOnTablePose(graus = 25f, giroDaFolha = 0.0)
        val matrix = ModelPlacement.worldMatrix(
            markerPose = pose,
            metrics = metrics,
            sizeMeters = 0.13f,
            rotationDegrees = RenderScene.DEFAULT_ROTATION_DEGREES,
            elevationMeters = 0f,
        )

        // Os eixos do marcador NO MUNDO (as colunas da rotação da pose): a normal da folha e a
        // altura NA imagem (com o sinal trocado, porque o "para cima" da imagem é o −Z).
        val normalDaFolha = Vec3(pose[0, 1].toFloat(), pose[1, 1].toFloat(), pose[2, 1].toFloat())
        val alturaNaImagem = Vec3(
            -pose[0, 2].toFloat(),
            -pose[1, 2].toFloat(),
            -pose[2, 2].toFloat(),
        )

        // A face do modelo (o +Z do arquivo) é a normal da folha — e, na mesa, essa normal aponta
        // para CIMA: é a face do modelo que olha para o teto.
        assertVector(normalDaFolha, direction(matrix, Vec3(0f, 0f, 5f)))
        assertTrue(
            normalDaFolha.y > 0.85f,
            "com a folha apoiada na mesa a normal é (quase) a vertical do mundo: $normalDaFolha",
        )

        // E o "para cima" do modelo é a altura NA imagem da folha — que, com a folha na mesa, está
        // DEITADA no plano da mesa (apontando para quem olha): o modelo está deitado.
        assertVector(alturaNaImagem, direction(matrix, Vec3(0f, 20f, 0f)))
        assertTrue(
            kotlin.math.abs(alturaNaImagem.y) < 0.5f,
            "com a folha apoiada na mesa o 'para cima' do modelo fica no plano da mesa: $alturaNaImagem",
        )
    }

    /**
     * Vetor do arquivo, em metros, depois de empilhada a matriz de um quadro — do ponto `(0, 0, 0)`
     * do arquivo até [tip]. É a medida que serve para conferir direção **e** comprimento.
     */
    private fun segment(matrix: FloatArray, tip: Vec3): Vec3 {
        val origin = ModelPlacement.apply(matrix, Vec3.ZERO)
        val end = ModelPlacement.apply(matrix, tip)
        return Vec3(end.x - origin.x, end.y - origin.y, end.z - origin.z)
    }

    /** Direção (unidade) de um eixo do arquivo no mundo: o que os testes de orientação comparam. */
    private fun direction(matrix: FloatArray, tip: Vec3): Vec3 {
        val vector = segment(matrix, tip)
        val length = kotlin.math.sqrt(
            vector.x * vector.x + vector.y * vector.y + vector.z * vector.z,
        )
        return Vec3(vector.x / length, vector.y / length, vector.z / length)
    }

    /** Confere um vetor componente a componente — as contas são de `Float`. */
    private fun assertVector(
        expected: Vec3,
        actual: Vec3,
        tolerance: Float = 1e-5f,
        message: String = "",
    ) {
        assertEquals(expected.x, actual.x, tolerance, "$message (x): $actual")
        assertEquals(expected.y, actual.y, tolerance, "$message (y): $actual")
        assertEquals(expected.z, actual.z, tolerance, "$message (z): $actual")
    }

    /**
     * Pose de uma folha **deitada na mesa**, a 0,6 m da câmera, vista por uma câmera [graus] abaixo
     * da horizontal: a normal da folha (o +Y do marcador) fica quase na vertical do mundo e o
     * "para baixo na imagem" (o +Z do marcador) aponta para quem olha. [giroDaFolha] é a folha
     * girando **sobre a mesa**, em torno da própria normal.
     */
    private fun sheetOnTablePose(graus: Float, giroDaFolha: Double): Pose {
        val matrix = ModelPlacement.multiply(
            ModelPlacement.eulerRotation(Vec3(graus, 0f, 0f)),
            ModelPlacement.eulerRotation(Vec3(0f, giroDaFolha.toFloat(), 0f)),
        )
        // `eulerRotation` é coluna-maior; a `Pose` guarda a rotação em ordem de LINHA.
        return Pose(
            rotation = doubleArrayOf(
                matrix[0].toDouble(), matrix[4].toDouble(), matrix[8].toDouble(),
                matrix[1].toDouble(), matrix[5].toDouble(), matrix[9].toDouble(),
                matrix[2].toDouble(), matrix[6].toDouble(), matrix[10].toDouble(),
            ),
            translation = Vec3(0f, 0f, -0.6f),
        )
    }

    /** Pose de um marcador que gira em torno da **sua normal** (o eixo Y do referencial dele). */
    private fun spinAroundNormal(degrees: Double): Pose {
        // `eulerRotation` é coluna-maior; a `Pose` guarda a rotação em ordem de LINHA.
        val m = ModelPlacement.eulerRotation(Vec3(0f, degrees.toFloat(), 0f))
        val rotation = doubleArrayOf(
            m[0].toDouble(), m[4].toDouble(), m[8].toDouble(),
            m[1].toDouble(), m[5].toDouble(), m[9].toDouble(),
            m[2].toDouble(), m[6].toDouble(), m[10].toDouble(),
        )
        return Pose(rotation = rotation, translation = Vec3.ZERO)
    }
}
