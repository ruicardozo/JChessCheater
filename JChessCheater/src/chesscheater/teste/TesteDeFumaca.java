package chesscheater.teste;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import chesscheater.config.Configuracao;
import chesscheater.motor.MotorUci;
import chesscheater.partida.Partida;
import chesscheater.visao.ConjuntoDeTemplates;
import chesscheater.visao.DetectorDeTabuleiro;
import chesscheater.visao.Geometria;
import chesscheater.visao.Grade;
import chesscheater.visao.MoldesEmbutidos;
import chesscheater.visao.Orientacao;
import chesscheater.visao.PerfilDeTela;
import chesscheater.visao.Rgb;
import chesscheater.visao.Visao;

/**
 * Diagnóstico de instalação: verifica tudo o que <b>não</b> depende de haver um navegador na
 * tela. Roda em qualquer máquina, inclusive sem monitor.
 *
 * <pre>
 * java -cp bin:lib/jchessai.jar chesscheater.teste.TesteDeFumaca
 * </pre>
 *
 * <p>O que ele cobre, e o que deliberadamente não cobre:
 *
 * <ul>
 *   <li><b>cobre</b>: os moldes carregam; a geometria acha um tabuleiro sintético na posição e
 *       no tamanho certos; a cadeia de visão inteira lê de volta uma posição pintada, nas duas
 *       orientações; as regras e o árbitro se comportam; o {@code jchessai.jar} sobe, responde
 *       UCI e devolve lance legal — com e sem histórico;</li>
 *   <li><b>não cobre</b>: o desenho REAL do chess.com. A leitura de peças aqui usa os próprios
 *       moldes como tinta, então é teste de <i>consistência</i> — pega transposição, erro de
 *       amostragem, FEN montada ao contrário. Os limiares de cor de {@link PerfilDeTela} só
 *       podem ser validados contra uma captura de tela de verdade; se um dia o site mudar o
 *       desenho, é lá que se mexe, olhando a tela.</li>
 * </ul>
 */
public final class TesteDeFumaca
{
    private static int passou = 0, falhou = 0;

    public static void main(String[] args) throws Exception
    {
        System.out.println("JChessCheater — diagnóstico\n");

        moldes();
        geometria();
        leituraDePecas();
        regras();
        arbitro();
        configuracao();
        motor();

        System.out.printf("%n%d passaram, %d falharam%n", passou, falhou);
        System.exit(falhou == 0 ? 0 : 1);
    }

    // =========================================================================
    private static void moldes() throws Exception
    {
        System.out.println("── moldes das peças ──");
        ConjuntoDeTemplates moldes = MoldesEmbutidos.carrega();
        confere("12 moldes carregados", moldes.todos().size() == 12,
                "vieram " + moldes.todos().size());
        confere("resolução de referência 64x64",
                moldes.largura == 64 && moldes.altura == 64,
                moldes.largura + "x" + moldes.altura);

        StringBuilder simbolos = new StringBuilder();
        for (ConjuntoDeTemplates.Template t : moldes.todos())
            simbolos.append(t.simbolo);
        confere("as doze peças da FEN estão lá",
                contemTodos(simbolos.toString(), "KQRBNPkqrbnp"),
                "símbolos: " + simbolos);
    }

    /**
     * A geometria é a parte da visão que dá para testar sem o chess.com: o detector só olha
     * cor de casa, então um tabuleiro pintado com as cores do tema é entrada legítima.
     */
    private static void geometria()
    {
        System.out.println("\n── geometria do tabuleiro ──");
        final int origemX = 57, origemY = 33, lado = 91;    // números feios de propósito
        Rgb imagem = tabuleiroSintetico(900, 880, origemX, origemY, lado);

        Geometria geo = DetectorDeTabuleiro.detecta(imagem, PerfilDeTela.GENERICO);
        confere("achou o tabuleiro", geo != null, "detecta() devolveu null");
        if (geo == null)
            return;

        confere("origem x correta (±2px)", Math.abs(geo.x0 - origemX) <= 2,
                "esperado " + origemX + ", veio " + geo.x0);
        confere("origem y correta (±2px)", Math.abs(geo.y0 - origemY) <= 2,
                "esperado " + origemY + ", veio " + geo.y0);
        confere("lado da casa correto (±1px)", Math.abs(geo.lado - lado) <= 1.0,
                "esperado " + lado + ", veio " + String.format("%.2f", geo.lado));
        confere("geometria considerada plausível", geo.plausivel(),
                "lado " + geo.lado + " fora de [" + PerfilDeTela.LADO_MIN + ","
                + PerfilDeTela.LADO_MAX + "]");

        // Fundo neutro não pode ser confundido com casa — é o que separa o tabuleiro da
        // barra lateral do site.
        confere("fundo neutro não conta como casa",
                !PerfilDeTela.CORES_PADRAO.ePixelDeCasa(48, 46, 43), "");
        confere("casa clara conta como casa",
                PerfilDeTela.CORES_PADRAO.ePixelDeCasa(235, 236, 208), "");
        confere("casa escura conta como casa",
                PerfilDeTela.CORES_PADRAO.ePixelDeCasa(119, 149, 86), "");
        confere("realce amarelo conta como casa",
                PerfilDeTela.CORES_PADRAO.ePixelDeCasa(246, 246, 105), "");
    }

    /**
     * A cadeia de visão inteira, de ponta a ponta: pinta um tabuleiro com peças e lê de volta.
     *
     * <p>As peças são pintadas com os PRÓPRIOS moldes, o que torna este um teste de
     * consistência, não de reconhecimento: ele prova que amostragem, classificação,
     * orientação e montagem da FEN estão certas — um erro de transposição linha/coluna, por
     * exemplo, apareceria na hora. Ele <b>não</b> prova nada sobre o desenho real do
     * chess.com; isso só a tela prova.
     *
     * <p>A pintura é fiel de propósito: onde o molde tem frente, pinta-se cinza limitado a 200
     * — cinza acima disso seria classificado como casa clara e a máscara de frente sairia
     * diferente da do molde. Abaixo de 200, cinza nunca casa com nenhuma das três famílias de
     * cor de casa, então a máscara reproduz exatamente.
     */
    private static void leituraDePecas() throws Exception
    {
        System.out.println("\n── leitura de peças (cadeia completa) ──");
        ConjuntoDeTemplates moldes = MoldesEmbutidos.carrega();
        Visao visao = new Visao();

        for (Orientacao orientacao : Orientacao.values())
        {
            Rgb imagem = tabuleiroComPecas(Grade.PLACEMENT_INICIAL, orientacao, moldes,
                                           900, 880, 57, 33, 91);
            Visao.Leitura leitura = visao.tenta(imagem, PerfilDeTela.GENERICO);
            if (leitura == null)
            {
                confere("leu o tabuleiro com peças (" + orientacao + ")", false,
                        "tenta() devolveu null");
                continue;
            }

            String lido = Grade.paraPlacement(Grade.orienta(leitura.grade, orientacao));
            confere("posição inicial lida de volta, " + orientacao + " embaixo",
                    lido.equals(Grade.PLACEMENT_INICIAL), lido);

            int certas = 0, total = 0;
            char[][] esperada = Grade.orienta(gradeDe(Grade.PLACEMENT_INICIAL), orientacao);
            for (int l = 0; l < 8; l++)
                for (int c = 0; c < 8; c++)
                {
                    total++;
                    if (leitura.grade[l][c] == esperada[l][c])
                        certas++;
                }
            System.out.printf("   %d/%d casas certas (%s embaixo)%n", certas, total, orientacao);
        }
    }

    private static void regras()
    {
        System.out.println("\n── grade, placement e orientação ──");
        char[][] inicial = gradeDe(Grade.PLACEMENT_INICIAL);

        confere("grade → placement fecha o ciclo",
                Grade.paraPlacement(inicial).equals(Grade.PLACEMENT_INICIAL),
                Grade.paraPlacement(inicial));

        // Girar a posição inicial 180 graus troca rei e dama: só uma orientação a reproduz.
        String girada = Grade.paraPlacement(Grade.orienta(inicial, Orientacao.PRETAS));
        confere("girar 180° muda o placement inicial",
                !girada.equals(Grade.PLACEMENT_INICIAL), girada);
        confere("girar duas vezes volta ao original",
                Grade.paraPlacement(Grade.orienta(
                    Grade.orienta(inicial, Orientacao.PRETAS), Orientacao.PRETAS))
                    .equals(Grade.PLACEMENT_INICIAL), "");

        confere("posição inicial é plausível",
                Grade.motivoDeImplausibilidade(inicial) == null,
                String.valueOf(Grade.motivoDeImplausibilidade(inicial)));
        confere("tabuleiro sem rei é rejeitado",
                Grade.motivoDeImplausibilidade(gradeDe("8/8/8/8/8/8/8/8")) != null, "");
        confere("peão na 8ª fileira é rejeitado",
                Grade.motivoDeImplausibilidade(gradeDe("Pnbqkbnr/8/8/8/8/8/8/RNBQKBNK")) != null,
                "");

        confere("material aponta brancas embaixo na posição inicial",
                Grade.palpitePorMaterial(inicial) == Orientacao.BRANCAS, "");

        confere("roque deduzido do placement inicial é KQkq",
                Grade.direitosDeRoque(Grade.PLACEMENT_INICIAL).equals("KQkq"),
                Grade.direitosDeRoque(Grade.PLACEMENT_INICIAL));
        confere("rei fora de e1 não gera direito de roque branco",
                !Grade.direitosDeRoque("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQ1BNR")
                     .contains("K"), "");
    }

    private static void arbitro()
    {
        System.out.println("\n── árbitro da partida (chessai.core.Board) ──");
        Partida p = new Partida();
        p.comecaDoInicio(true);

        confere("começa na posição inicial",
                Grade.PLACEMENT_INICIAL.equals(p.placement()), String.valueOf(p.placement()));
        confere("começa com histórico", p.temHistorico(), "");
        confere("brancas: é a nossa vez", p.nossaVez(), "");

        p.aplica("e2e4");
        confere("e2e4 entrou na lista de lances",
                p.lances().size() == 1 && p.lances().get(0).equals("e2e4"),
                p.lances().toString());

        // O lance do adversário é descoberto pela DIFERENÇA entre a tela e o estado interno.
        String depoisDeE7E5 = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR";
        confere("descobre o lance do bot pelo placement",
                "e7e5".equals(p.lanceQueProduz(depoisDeE7E5)),
                String.valueOf(p.lanceQueProduz(depoisDeE7E5)));
        confere("placement impossível não casa com lance nenhum",
                p.lanceQueProduz("8/8/8/8/8/8/8/8") == null, "");

        p.aplica("e7e5");
        confere("prevê o placement depois do nosso lance",
                p.placementDepoisDe("g1f3") != null
                && p.placementDepoisDe("g1f3").endsWith("PPPP1PPP/RNBQKB1R"),
                String.valueOf(p.placementDepoisDe("g1f3")));
        confere("nosso lance + resposta do bot é alcançável",
                p.alcancavelDepoisDoNosso("g1f3",
                    "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R"), "");
        confere("posição inalcançável em dois plies é rejeitada",
                !p.alcancavelDepoisDoNosso("g1f3", Grade.PLACEMENT_INICIAL), "");
        confere("lance ilegal é recusado", !p.eLegal("e1e8"), "");

        // Reconstrução do botão "Jogar": funciona, mas perde o histórico.
        Partida resgate = new Partida();
        confere("reconstrói de um placement arbitrário",
                resgate.reconstroiDe("8/8/4k3/8/8/4K3/8/7R", true), "");
        confere("reconstruída fica SEM histórico", !resgate.temHistorico(), "");
        confere("final de baixo material detectado", resgate.finalDeBaixoMaterial(), "");
    }

    /**
     * O JSON mestre: escreve um arquivo, lê de volta e confere campo a campo.
     *
     * <p>É o que prova que editar o arquivo muda mesmo a janela — a ligação que dá vontade de
     * presumir e que ninguém percebe estar quebrada até abrir o programa e ver o padrão de
     * fábrica no lugar do que se pediu.
     */
    private static void configuracao() throws Exception
    {
        System.out.println("\n── JSON mestre ──");
        Path pasta = Files.createTempDirectory("jchesscheater-teste");
        try
        {
            Path json = pasta.resolve(Configuracao.NOME_PADRAO);
            Files.writeString(json, """
                {
                  "pesos": "iter_0120.pt",
                  "sims": 800,
                  "ambiente": "ubuntu",
                  "intervaloMs": 1500,
                  "latenciaMs": 3500,
                  "autoJogo": false
                }
                """, StandardCharsets.UTF_8);

            System.setProperty("jchesscheater.config", json.toString());
            Configuracao c = Configuracao.carrega();

            confere("achou o arquivo", json.equals(c.arquivo()), String.valueOf(c.arquivo()));
            confere("pesos: lê o nome pedido",
                    c.pesos().getFileName().toString().equals("iter_0120.pt"),
                    c.pesos().toString());
            confere("pesos: resolve na pasta do JSON",
                    c.pesos().getParent().equals(pasta), c.pesos().toString());
            confere("nível: 800", c.sims() == 800, String.valueOf(c.sims()));
            confere("intervalo: 1500 ms", c.intervaloMs() == 1500, String.valueOf(c.intervaloMs()));
            confere("latência: 3500 ms", c.latenciaMs() == 3500, String.valueOf(c.latenciaMs()));
            confere("auto-jogo: desligado", !c.autoJogo(), "");
            confere("ambiente: ubuntu", "ubuntu".equals(c.ambiente()), c.ambiente());

            // Caminho absoluto: é assim que se aponta uma iteração sem copiar 65 MB.
            Files.writeString(json,
                "{ \"pesos\": \"/tmp/qualquer/iter_0150.pt\" }", StandardCharsets.UTF_8);
            c = Configuracao.carrega();
            confere("pesos: caminho absoluto passa intacto",
                    c.pesos().toString().equals("/tmp/qualquer/iter_0150.pt")
                    || c.pesos().toString().endsWith("iter_0150.pt"), c.pesos().toString());
            confere("campo ausente cai no padrão",
                    c.sims() == Configuracao.SIMS_PADRAO
                    && c.intervaloMs() == Configuracao.INTERVALO_PADRAO_MS
                    && c.latenciaMs() == Configuracao.LATENCIA_PADRAO_MS
                    && c.autoJogo(),
                    c.sims() + "/" + c.intervaloMs() + "/" + c.latenciaMs());

            // Valores fora da lista e fora da faixa: o programa tem que abrir mesmo assim.
            Files.writeString(json,
                "{ \"sims\": 320, \"intervaloMs\": 50, \"latenciaMs\": 999999 }",
                StandardCharsets.UTF_8);
            c = Configuracao.carrega();
            confere("nível fora da lista vira o mais próximo (320 → 400)",
                    c.sims() == 400, String.valueOf(c.sims()));

            Files.writeString(json, "{ \"sims\": 300 }", StandardCharsets.UTF_8);
            confere("empate desce (300 → 200)",
                    Configuracao.carrega().sims() == 200,
                    String.valueOf(Configuracao.carrega().sims()));

            Files.writeString(json,
                "{ \"intervaloMs\": 50, \"latenciaMs\": 999999 }", StandardCharsets.UTF_8);
            c = Configuracao.carrega();
            confere("intervalo abaixo do mínimo é limitado (50 → 200)",
                    c.intervaloMs() == 200, String.valueOf(c.intervaloMs()));
            confere("latência acima do máximo é limitada (999999 → 60000)",
                    c.latenciaMs() == 60_000, String.valueOf(c.latenciaMs()));

            // Sem arquivo nenhum: os padrões, e não uma explosão.
            System.clearProperty("jchesscheater.config");
            Files.delete(json);
            c = Configuracao.carrega();
            confere("sem JSON, roda nos padrões",
                    c.sims() == Configuracao.SIMS_PADRAO && c.autoJogo(), "");
        }
        finally
        {
            System.clearProperty("jchesscheater.config");
            try (var caminhos = Files.walk(pasta))
            {
                caminhos.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> p.toFile().delete());
            }
        }
    }

    private static void motor() throws Exception
    {
        System.out.println("\n── motor (jchessai.jar por UCI) ──");
        Configuracao config;
        try
        {
            config = Configuracao.carrega();
        }
        catch (Exception jsonRuim)
        {
            // Um JSON com vírgula sobrando é o erro mais provável de todos — quem edita o
            // arquivo à mão merece a linha e a coluna, não um stack trace.
            confere("o " + Configuracao.NOME_PADRAO + " é válido", false,
                    jsonRuim.getMessage());
            return;
        }
        System.out.println("   config: " + (config.arquivo() == null
            ? "nenhum (padrões)" : config.arquivo().toString()));
        MotorUci motor = MotorUci.de(config);
        System.out.println("   jar   : " + motor.jar());
        System.out.println("   pesos : " + motor.pesos());

        try
        {
            long t0 = System.currentTimeMillis();
            motor.inicia();
            confere("o motor subiu e respondeu o aperto de mão UCI", motor.estaVivo(),
                    "processo morto");
            System.out.printf("   subiu em %.1f s%n", (System.currentTimeMillis() - t0) / 1000.0);

            motor.defineSims(10);                 // rápido: aqui só importa que responda

            Partida p = new Partida();
            p.comecaDoInicio(true);

            t0 = System.currentTimeMillis();
            String daPartida = motor.melhorLanceDaPartida(p.lances());
            long ms = System.currentTimeMillis() - t0;
            confere("devolve lance com a partida inteira (histórico real)",
                    daPartida != null && p.eLegal(daPartida), String.valueOf(daPartida));
            System.out.printf("   posição inicial → %s (%d ms, 10 simulações)%n", daPartida, ms);

            String daFen = motor.melhorLanceDaFen(p.fen());
            confere("devolve lance a partir de uma FEN solta",
                    daFen != null && p.eLegal(daFen), String.valueOf(daFen));

            // Duas perguntas seguidas: garante que o protocolo não ficou dessincronizado
            // (é o modo de falha clássico de quem não espera o bestmove).
            p.aplica(daPartida);
            String segundo = motor.melhorLanceDaPartida(p.lances());
            confere("responde de novo, em sequência, sem dessincronizar",
                    segundo != null && p.eLegal(segundo), String.valueOf(segundo));
            System.out.println("   depois de " + daPartida + " → " + segundo);

            motor.novaPartida();
            confere("aceita ucinewgame e continua vivo", motor.estaVivo(), "");
        }
        catch (Exception falha)
        {
            confere("o motor respondeu", false, falha.getMessage());
        }
        finally
        {
            motor.close();
        }
    }

    // =========================================================================
    //  Apoio
    // =========================================================================
    /** Tabuleiro pintado com as cores do tema, sobre fundo neutro. Sem peças. */
    private static Rgb tabuleiroSintetico(int largura, int altura,
                                          int origemX, int origemY, int lado)
    {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        final int fundo = 0x302E2B;               // cinza do site, fora do tabuleiro
        final int clara = 0xEBECD0;
        final int escura = 0x779556;

        for (int y = 0; y < altura; y++)
            for (int x = 0; x < largura; x++)
                img.setRGB(x, y, fundo);

        for (int linha = 0; linha < 8; linha++)
            for (int coluna = 0; coluna < 8; coluna++)
            {
                int cor = ((linha + coluna) % 2 == 0) ? clara : escura;
                for (int y = 0; y < lado; y++)
                    for (int x = 0; x < lado; x++)
                        img.setRGB(origemX + coluna * lado + x,
                                   origemY + linha * lado + y, cor);
            }
        return Rgb.de(img);
    }

    /**
     * Tabuleiro pintado com peças, na orientação pedida. As peças saem dos moldes: onde o
     * molde tem frente, pinta cinza (limitado a 200, ver o javadoc de
     * {@link #leituraDePecas()}); no resto, a cor da casa.
     */
    private static Rgb tabuleiroComPecas(String placement, Orientacao orientacao,
                                         ConjuntoDeTemplates moldes,
                                         int largura, int altura,
                                         int origemX, int origemY, int lado)
    {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        final int fundo = 0x302E2B, clara = 0xEBECD0, escura = 0x779556;

        for (int y = 0; y < altura; y++)
            for (int x = 0; x < largura; x++)
                img.setRGB(x, y, fundo);

        // A grade na TELA é a posição girada conforme a orientação.
        char[][] naTela = Grade.orienta(gradeDe(placement), orientacao);

        for (int linha = 0; linha < 8; linha++)
            for (int coluna = 0; coluna < 8; coluna++)
            {
                int corDaCasa = ((linha + coluna) % 2 == 0) ? clara : escura;
                char simbolo = naTela[linha][coluna];
                ConjuntoDeTemplates.Template molde = null;
                if (simbolo != '.')
                    for (ConjuntoDeTemplates.Template t : moldes.todos())
                        if (t.simbolo == simbolo)
                            molde = t;

                for (int y = 0; y < lado; y++)
                    for (int x = 0; x < lado; x++)
                    {
                        int cor = corDaCasa;
                        if (molde != null)
                        {
                            int tx = x * moldes.largura / lado;
                            int ty = y * moldes.altura / lado;
                            int i = ty * moldes.largura + tx;
                            if (molde.frente[i] > 0.5)
                            {
                                int v = Math.min(200, (int) Math.round(molde.iso[i]));
                                cor = (v << 16) | (v << 8) | v;
                            }
                        }
                        img.setRGB(origemX + coluna * lado + x,
                                   origemY + linha * lado + y, cor);
                    }
            }
        return Rgb.de(img);
    }

    /** Placement → grade 8x8 de chars. */
    private static char[][] gradeDe(String placement)
    {
        String[] fileiras = placement.split("/");
        char[][] grade = new char[8][8];
        for (int l = 0; l < 8; l++)
            grade[l] = Grade.expandeFileira(l < fileiras.length ? fileiras[l] : "8");
        return grade;
    }

    private static boolean contemTodos(String onde, String quais)
    {
        for (char c : quais.toCharArray())
            if (onde.indexOf(c) < 0)
                return false;
        return true;
    }

    private static void confere(String oQue, boolean ok, String detalhe)
    {
        if (ok)
        {
            passou++;
            System.out.println("   ok   " + oQue);
        }
        else
        {
            falhou++;
            System.out.println("   FALHA " + oQue + (detalhe.isEmpty() ? "" : "  → " + detalhe));
        }
    }

    private TesteDeFumaca() { }
}
