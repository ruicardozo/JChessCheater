package chesscheater.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Robot;
import java.util.concurrent.ThreadLocalRandom;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import chesscheater.config.Configuracao;
import chesscheater.motor.MotorUci;
import chesscheater.partida.Partida;
import chesscheater.robo.Clicador;
import chesscheater.visao.Geometria;
import chesscheater.visao.Grade;
import chesscheater.visao.Orientacao;
import chesscheater.visao.PerfilDeTela;
import chesscheater.visao.Visao;

/**
 * A janela e o laço de jogo.
 *
 * <p>Amarra as quatro peças, sem saber como nenhuma funciona por dentro: {@link Visao} lê a
 * tela, {@link Partida} valida contra as regras, {@link MotorUci} escolhe o lance e
 * {@link Clicador} o executa.
 *
 * <h2>O laço</h2>
 * Um {@code Timer} captura a tela a cada intervalo. Quando o tabuleiro muda, o trabalho vai
 * para uma thread própria — a busca da rede e os cliques têm pausas e travariam a interface.
 * Enquanto um lance está em andamento, as varreduras são ignoradas: o tabuleiro está mudando
 * por nossa causa.
 *
 * <h2>Lance pendente</h2>
 * Ao jogar, o estado interno <b>não</b> avança de imediato. O lance fica pendente e a próxima
 * varredura confirma se a peça realmente se moveu na tela. Se não moveu — o clique caiu numa
 * prévia, coisa que acontece —, o mesmo lance é re-clicado. É o que impede o robô de achar que
 * jogou quando não jogou.
 */
public final class FormPrincipal
{
    /** Piso absoluto do movimento do mouse: abaixo disso o clique não parece humano. */
    private static final int LATENCIA_MINIMA_MS = 200;

    // ── Interface ────────────────────────────────────────────────────────────
    private final JFrame janela;
    private final JButton botaoIniciar, botaoParar, botaoJogar, botaoFechar;
    private final JLabel rotuloDeEstado;
    private final JComboBox<String> comboMonitor;
    private final JComboBox<PerfilDeTela> comboAmbiente;
    private final JComboBox<Integer> comboSims;
    private final JComboBox<String> comboCor;
    private final JSpinner spinnerIntervalo, spinnerLatencia;
    private final JCheckBox checkAutoJogo;
    private final GraphicsDevice[] monitores;

    // ── Máquina ──────────────────────────────────────────────────────────────
    private final Visao visao;
    private final Configuracao config;
    private final MotorUci motor;
    private final Partida partida = new Partida();
    private Clicador clicador;
    private Robot robot;
    private Timer temporizador;
    private Rectangle regiaoAtual;

    // ── Estado do laço ───────────────────────────────────────────────────────
    private volatile boolean ocupado = false;
    private String ultimoPlacement = null;
    private Orientacao ultimaOrientacao = null;
    private Orientacao candidataAVirada = null;
    private String origemDaOrientacao = "?";
    private boolean tratouPosicaoInicial = false;
    private int nossosLancesJogados = 0;
    private int lancesRapidosDeAbertura = 0;

    // Lance pendente de confirmação na tela.
    private String pendenteUci = null;
    private String pendenteEsperado = null;
    private String pendenteAntes = null;
    private long pendenteDesde = 0L;

    public FormPrincipal(Visao visao, Configuracao config)
    {
        this.visao = visao;
        this.config = config;
        this.motor = MotorUci.de(config);

        janela = new JFrame("JChessCheater — rede v16a contra os bots do chess.com");
        janela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        botaoIniciar = new JButton("Iniciar");
        botaoParar = new JButton("Parar");
        botaoJogar = new JButton("Jogar");
        botaoFechar = new JButton("Fechar");
        botaoParar.setEnabled(false);
        botaoJogar.setEnabled(false);
        rotuloDeEstado = new JLabel("Parado.");

        monitores = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        String[] nomes = new String[monitores.length];
        for (int i = 0; i < monitores.length; i++)
        {
            Rectangle b = monitores[i].getDefaultConfiguration().getBounds();
            nomes[i] = "Monitor " + (i + 1) + " (" + b.width + "x" + b.height
                     + " @" + b.x + "," + b.y + ")";
        }
        comboMonitor = new JComboBox<>(nomes);

        comboAmbiente = new JComboBox<>(PerfilDeTela.TODOS);
        comboAmbiente.setSelectedItem(perfilPedido(config.ambiente()));
        comboAmbiente.setToolTipText("Como o chess.com desenha o tabuleiro neste sistema. "
            + "O detectado já vem selecionado.");

        spinnerIntervalo = new JSpinner(
            new SpinnerNumberModel(config.intervaloMs(), 200, 10000, 100));
        spinnerLatencia = new JSpinner(
            new SpinnerNumberModel(config.latenciaMs(), 0, 60000, 100));
        spinnerLatencia.setToolTipText("Teto do tempo gasto movendo o mouse até a casa "
            + "(simula tempo de reação). Não é tempo de pensar.");

        comboSims = new JComboBox<>(niveis());
        comboSims.setSelectedItem(Integer.valueOf(config.sims()));
        comboSims.setToolTipText("Simulações do MCTS por lance: é a força da rede. "
            + "200 ≈ 1,2 s por lance; 1400 é bem mais forte e bem mais lento.");

        comboCor = new JComboBox<>(new String[] { "Brancas", "Pretas" });
        checkAutoJogo = new JCheckBox("Auto-jogar (robô)", config.autoJogo());

        JPanel topo = new JPanel(new GridLayout(0, 2, 6, 6));
        topo.add(new JLabel("Monitor:"));
        topo.add(comboMonitor);
        topo.add(new JLabel("Ambiente:"));
        topo.add(comboAmbiente);
        topo.add(new JLabel("Intervalo (ms):"));
        topo.add(spinnerIntervalo);
        topo.add(new JLabel("Latência (ms):"));
        topo.add(spinnerLatencia);
        topo.add(new JLabel("Nível (simulações):"));
        topo.add(comboSims);
        topo.add(new JLabel("Jogar como:"));
        topo.add(comboCor);
        topo.add(new JLabel("Auto-jogo:"));
        topo.add(checkAutoJogo);

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        botoes.add(botaoIniciar);
        botoes.add(botaoParar);
        botoes.add(botaoJogar);
        botoes.add(botaoFechar);

        JPanel raiz = new JPanel(new BorderLayout(8, 8));
        raiz.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        raiz.add(topo, BorderLayout.NORTH);
        raiz.add(botoes, BorderLayout.CENTER);
        raiz.add(rotuloDeEstado, BorderLayout.SOUTH);

        janela.setContentPane(raiz);
        janela.pack();
        janela.setMinimumSize(new Dimension(420, janela.getHeight()));
        janela.setLocationRelativeTo(null);

        botaoIniciar.addActionListener(e -> inicia());
        botaoParar.addActionListener(e -> para());
        botaoJogar.addActionListener(e -> forcaJogada());
        botaoFechar.addActionListener(e -> {
            para();
            motor.close();
            janela.dispose();
            System.exit(0);
        });

        // Se a janela for fechada pelo "X", o EXIT_ON_CLOSE encerra a JVM sem passar pelo
        // botão Fechar — sem este gancho o subprocesso do motor ficaria órfão.
        Runtime.getRuntime().addShutdownHook(new Thread(motor::close, "motor-shutdown"));
    }

    public void mostra()
    {
        janela.setVisible(true);
        informa("Pronto — " + config.resumo());
        System.out.println("[JChessCheater] motor    : " + motor.jar());
        System.out.println("[JChessCheater] pesos    : " + motor.pesos());
        System.out.println("[JChessCheater] aplicado : nível " + comboSims.getSelectedItem()
            + " simulações · intervalo " + spinnerIntervalo.getValue() + " ms"
            + " · latência " + spinnerLatencia.getValue() + " ms"
            + " · ambiente " + comboAmbiente.getSelectedItem()
            + " · auto-jogo " + (checkAutoJogo.isSelected() ? "ligado" : "desligado"));
    }

    /** O perfil pedido no JSON ("auto", "windows", "ubuntu", "generico"). */
    private static PerfilDeTela perfilPedido(String nome)
    {
        String n = nome == null ? "auto" : nome.trim().toLowerCase();
        if (n.startsWith("win"))
            return PerfilDeTela.WINDOWS;
        if (n.startsWith("ubu") || n.startsWith("lin"))
            return PerfilDeTela.UBUNTU;
        if (n.startsWith("gen"))
            return PerfilDeTela.GENERICO;
        return PerfilDeTela.detectado();
    }

    /** Os níveis do combo, na ordem em que a Configuracao os declara. */
    private static Integer[] niveis()
    {
        Integer[] saida = new Integer[Configuracao.NIVEIS.length];
        for (int i = 0; i < saida.length; i++)
            saida[i] = Configuracao.NIVEIS[i];
        return saida;
    }

    // =========================================================================
    //  Ciclo de vida
    // =========================================================================
    private void inicia()
    {
        int indiceMonitor = Math.max(0, comboMonitor.getSelectedIndex());
        GraphicsDevice monitor = monitores[indiceMonitor];
        try
        {
            robot = new Robot(monitor);
        }
        catch (Exception falha)
        {
            informa("Erro ao criar o Robot: " + falha.getMessage());
            return;
        }
        clicador = new Clicador(robot);

        PerfilDeTela perfil = (PerfilDeTela) comboAmbiente.getSelectedItem();
        Rectangle limites = monitor.getDefaultConfiguration().getBounds();

        // As regiões de PerfilDeTela foram medidas em 1920x1080. Noutra resolução o preset
        // ainda pode conter o tabuleiro (o detector refina dentro dele), mas pode também não
        // conter — e o sintoma seria "tabuleiro não encontrado" sem dizer por quê.
        if (limites.width != 1920 || limites.height != 1080)
            informa("Atenção: monitor " + limites.width + "x" + limites.height
                + " — as regiões foram calibradas para 1920x1080.");
        // Recorta ao que cabe na tela, para o preset nunca extrapolar os limites.
        regiaoAtual = new Rectangle(
            limites.x + perfil.x, limites.y + perfil.y,
            Math.max(1, Math.min(perfil.largura, limites.width - perfil.x)),
            Math.max(1, Math.min(perfil.altura, limites.height - perfil.y)));

        // Cada "Iniciar" recomeça a partida do zero. A posição interna só nasce quando o
        // primeiro tabuleiro válido for lido.
        partida.reinicia();
        ultimoPlacement = null;
        ultimaOrientacao = null;
        candidataAVirada = null;
        tratouPosicaoInicial = false;
        limpaPendencia();
        sorteiaAberturaRapida();
        ocupado = false;

        habilitaControles(false);
        informa("Subindo o motor...");

        new Thread(() -> {
            try
            {
                motor.inicia();
                motor.defineSims(sims());
                System.out.println("[JChessCheater] " + motor.descricaoDaRede());
                informa("Escaneando...");
                SwingUtilities.invokeLater(() -> {
                    temporizador = new Timer((Integer) spinnerIntervalo.getValue(),
                                             e -> varreUmaVez());
                    temporizador.setInitialDelay(0);
                    temporizador.start();
                });
            }
            catch (Exception falha)
            {
                informa("Motor não subiu: " + falha.getMessage());
                SwingUtilities.invokeLater(() -> habilitaControles(true));
            }
        }, "JChessCheater-inicio").start();
    }

    private void para()
    {
        if (temporizador != null)
        {
            temporizador.stop();
            temporizador = null;
        }
        robot = null;
        clicador = null;
        habilitaControles(true);
        rotuloDeEstado.setText("Parado.");
    }

    private void habilitaControles(boolean parado)
    {
        botaoIniciar.setEnabled(parado);
        botaoParar.setEnabled(!parado);
        botaoJogar.setEnabled(!parado);
        comboMonitor.setEnabled(parado);
        comboAmbiente.setEnabled(parado);
        spinnerIntervalo.setEnabled(parado);
        // Latência, SIMS e cor podem mudar com o robô rodando — são ajustes de jogo.
    }

    // =========================================================================
    //  Varredura
    // =========================================================================
    private void varreUmaVez()
    {
        // Enquanto um lance está sendo processado, o tabuleiro muda por nossa causa.
        if (ocupado || robot == null)
            return;

        final PerfilDeTela perfil = (PerfilDeTela) comboAmbiente.getSelectedItem();
        final Rectangle regiao = regiaoAtual;

        Visao.Leitura leitura;
        try
        {
            leitura = visao.le(robot, regiao, perfil);
        }
        catch (Exception falha)
        {
            rotuloDeEstado.setText("Erro na captura: " + falha.getMessage());
            return;
        }
        if (leitura == null)
        {
            rotuloDeEstado.setText("Tabuleiro não encontrado / posição implausível.");
            return;
        }

        final Orientacao orientacao = resolveOrientacao(leitura, perfil);
        final String placement = Grade.paraPlacement(
            Grade.orienta(leitura.grade, orientacao));

        // O placement é normalizado (sempre na perspectiva das brancas), então ele NÃO muda
        // quando o tabuleiro é virado — só a orientação muda. Por isso a trava considera as
        // duas coisas, além de sempre reavaliar quando há pendência ou virada a confirmar.
        boolean orientacaoMudou = ultimaOrientacao != null && orientacao != ultimaOrientacao;
        if (placement.equals(ultimoPlacement) && !orientacaoMudou
            && pendenteUci == null && candidataAVirada == null)
            return;

        ultimoPlacement = placement;
        System.out.println(placement + "  [" + orientacao + " via " + origemDaOrientacao + "]");

        if (!checkAutoJogo.isSelected())
        {
            rotuloDeEstado.setText("Posição lida (" + orientacao + ").");
            return;
        }

        ocupado = true;
        Thread trabalhador = new Thread(() -> {
            try
            {
                trata(placement, leitura.geometria, regiao, orientacao);
            }
            catch (Exception falha)
            {
                falha.printStackTrace();
                informa("Erro no auto-jogo: " + falha.getMessage());
            }
            finally
            {
                ocupado = false;
            }
        }, "JChessCheater-autojogo");
        trabalhador.setDaemon(true);
        trabalhador.start();
    }

    /**
     * Decide a orientação da tela, combinando quatro fontes da mais forte para a mais fraca.
     *
     * <ol>
     *   <li><b>Posição inicial</b>: decide sozinha. Girar a posição inicial 180 graus troca
     *       rei e dama de lugar, então só UMA das duas orientações pode ser a inicial. Tem
     *       prioridade máxima porque este é também o único momento em que o tabuleiro vira de
     *       verdade — nova partida ou troca de cor.</li>
     *   <li><b>Rótulos de coordenada</b>: a verdade lida da própria tela. Independe de
     *       material, de estado interno e de histórico; é o único degrau que resolve o
     *       bootstrap no meio de um final.</li>
     *   <li><b>Coerência com o estado interno</b>: se exatamente UMA das duas orientações é
     *       coerente com a partida em andamento, ela decide.</li>
     *   <li><b>Trava</b>: o chess.com não gira o tabuleiro no meio da partida. Sem sinal
     *       autoritativo, mantemos a última orientação em vez de re-adivinhar a cada
     *       varredura.</li>
     *   <li><b>Material</b>: só no bootstrap, e é loteria em finais — daí ser o último
     *       recurso.</li>
     * </ol>
     */
    private Orientacao resolveOrientacao(Visao.Leitura leitura, PerfilDeTela perfil)
    {
        final String comoBrancas = Grade.paraPlacement(
            Grade.orienta(leitura.grade, Orientacao.BRANCAS));
        final String comoPretas = Grade.paraPlacement(
            Grade.orienta(leitura.grade, Orientacao.PRETAS));

        // Posição simétrica por rotação de 180 graus: as duas orientações dão o MESMO
        // placement, que portanto não carrega informação nenhuma de orientação.
        final boolean placementInforma = !comoBrancas.equals(comoPretas);

        if (placementInforma)
        {
            boolean inicialComoBrancas = comoBrancas.equals(Grade.PLACEMENT_INICIAL);
            boolean inicialComoPretas = comoPretas.equals(Grade.PLACEMENT_INICIAL);
            if (inicialComoBrancas != inicialComoPretas)
            {
                origemDaOrientacao = "inicial";
                return inicialComoBrancas ? Orientacao.BRANCAS : Orientacao.PRETAS;
            }
        }

        Orientacao porRotulo = visao.orientacaoPorRotulo(leitura, perfil);
        if (porRotulo != null)
        {
            origemDaOrientacao = "rótulo";
            return porRotulo;
        }

        if (placementInforma && partida.iniciada())
        {
            // Caminho rápido: a orientação travada já reproduz a posição interna. É o caso da
            // imensa maioria das varreduras, e evita gerar lances a cada tique.
            String interno = partida.placement();
            if (ultimaOrientacao != null && interno != null
                && interno.equals(ultimaOrientacao == Orientacao.BRANCAS
                                  ? comoBrancas : comoPretas))
            {
                origemDaOrientacao = "interno";
                return ultimaOrientacao;
            }

            boolean okBrancas = partida.coerenteCom(comoBrancas, pendenteUci);
            boolean okPretas = partida.coerenteCom(comoPretas, pendenteUci);
            if (okBrancas != okPretas)
            {
                origemDaOrientacao = "interno";
                return okBrancas ? Orientacao.BRANCAS : Orientacao.PRETAS;
            }
        }

        if (ultimaOrientacao != null)
        {
            origemDaOrientacao = "trava";
            return ultimaOrientacao;
        }

        origemDaOrientacao = "material";
        return Grade.palpitePorMaterial(leitura.grade);
    }

    // =========================================================================
    //  A máquina de estados da partida
    // =========================================================================
    private void trata(String placement, Geometria geo, Rectangle regiao, Orientacao orientacao)
        throws Exception
    {
        // ── Virada de tabuleiro ─────────────────────────────────────────────
        if (ultimaOrientacao != null && orientacao != ultimaOrientacao)
        {
            // Só aceitamos a virada se ela se repetir: evita reagir a oscilação de animação.
            if (orientacao != candidataAVirada)
            {
                candidataAVirada = orientacao;
                informa("Possível virada de tabuleiro; confirmando...");
                return;
            }
            candidataAVirada = null;

            if (placement.equals(Grade.PLACEMENT_INICIAL))
            {
                // Virada DE VERDADE: nova partida ou troca de lado. É o único momento em que
                // o tabuleiro gira, e a posição inicial identifica a orientação sem ambiguidade.
                informa("Tabuleiro virou: agora jogando de " + orientacao + ".");
                limpaPendencia();
                reiniciaDaPosicaoInicial(orientacao);
            }
            else
            {
                // CORREÇÃO de orientação, não virada: a partida interna está certa, o que
                // estava errado era só o mapeamento tela↔casa. Adota sem resetar a partida.
                informa("Orientação corrigida para " + orientacao + " (partida mantida).");
            }
            tratouPosicaoInicial = placement.equals(Grade.PLACEMENT_INICIAL);
            ultimaOrientacao = orientacao;
        }
        else
        {
            candidataAVirada = null;
        }
        ultimaOrientacao = orientacao;

        // ── Lance pendente de confirmação ───────────────────────────────────
        if (pendenteUci != null)
        {
            if (placement.equals(pendenteEsperado))
            {
                // Efetivou na tela: confirma no estado interno.
                nossosLancesJogados++;
                partida.aplica(pendenteUci);
                partida.defineNossaVez(false);
                limpaPendencia();
                return;                            // vez do adversário
            }
            if (placement.equals(pendenteAntes))
            {
                // Nada mudou: o clique caiu numa prévia. Re-tenta após 2s.
                if (System.currentTimeMillis() - pendenteDesde >= 2000)
                {
                    informa("Reenviando " + pendenteUci + " (clique sem efeito)...");
                    clicador.jogaLance(pendenteUci, geo, regiao, orientacao, orcamento());
                    pendenteDesde = System.currentTimeMillis();
                    ultimoPlacement = null;
                }
                else
                    informa("Aguardando efeito da jogada...");
                return;
            }
            // Mudou para outra coisa: pode ser que o nosso lance efetivou E o bot já
            // respondeu entre duas varreduras. Só confirmamos se o placement lido for de
            // fato alcançável por um lance dele a partir da posição após a nossa.
            if (partida.nossaVez() && partida.alcancavelDepoisDoNosso(pendenteUci, placement))
            {
                nossosLancesJogados++;
                partida.aplica(pendenteUci);
                partida.defineNossaVez(false);
            }
            limpaPendencia();
        }

        // ── Auto-detecção de início de partida ──────────────────────────────
        if (placement.equals(Grade.PLACEMENT_INICIAL))
        {
            if (!tratouPosicaoInicial)
            {
                reiniciaDaPosicaoInicial(orientacao);
                tratouPosicaoInicial = true;
            }
        }
        else
        {
            tratouPosicaoInicial = false;
        }

        // ── Sincronia com o estado interno ──────────────────────────────────
        boolean somosBrancas = comboCor.getSelectedIndex() == 0;

        if (!partida.iniciada())
        {
            partida.comecaDoInicio(somosBrancas);
            motor.novaPartida();
            // A tela pode já diferir do início (o bot já jogou). Casa o lance agora.
            if (!placement.equals(Grade.PLACEMENT_INICIAL))
            {
                String doBot = partida.lanceQueProduz(placement);
                if (doBot != null)
                {
                    partida.aplica(doBot);
                    partida.defineNossaVez(true);
                }
            }
        }
        else if (placement.equals(partida.placement()))
        {
            // Tela == estado interno. Se não é a nossa vez, é só o nosso próprio lance
            // reaparecendo numa varredura.
            if (!partida.nossaVez())
                return;
        }
        else
        {
            String doBot = partida.lanceQueProduz(placement);
            if (doBot != null)
            {
                partida.aplica(doBot);
                partida.defineNossaVez(true);
            }
            else
            {
                informa("Posição não reconhecida; aguardando a próxima jogada.");
                return;
            }
        }

        if (partida.nossaVez())
            pensaEJoga(geo, regiao, orientacao);
    }

    /** Pede o lance à rede e o executa na tela. */
    private void pensaEJoga(Geometria geo, Rectangle regiao, Orientacao orientacao)
        throws Exception
    {
        if (partida.fimDeJogo())
        {
            informa("Fim de jogo: " + partida.resultadoTexto());
            return;
        }

        motor.defineSims(sims());
        informa("Pensando (" + sims() + " simulações)...");

        // Com histórico: a partida inteira, e a rede recebe os 4 frames reais. Sem histórico
        // (posição reconstruída pelo botão Jogar): só a FEN, com os frames anteriores zerados.
        String uci = partida.temHistorico()
            ? motor.melhorLanceDaPartida(partida.lances())
            : motor.melhorLanceDaFen(partida.fen());

        if (uci == null)
        {
            informa("O motor não devolveu lance (fim de jogo?).");
            return;
        }
        if (!partida.eLegal(uci))
        {
            informa("Lance do motor ilegal na posição interna: " + uci);
            System.err.println("[JChessCheater] UCI sem casamento legal: " + uci
                + " | FEN: " + partida.fen());
            return;
        }

        int orcamento = orcamento();
        String antes = partida.placement();
        String esperado = partida.placementDepoisDe(uci);

        informa("Jogando " + uci + " em ~" + orcamento + " ms");
        clicador.jogaLance(uci, geo, regiao, orientacao, orcamento);

        // Registra como pendente: o estado interno só avança quando a próxima varredura
        // confirmar que a peça se moveu na tela.
        pendenteUci = uci;
        pendenteEsperado = esperado;
        pendenteAntes = antes;
        pendenteDesde = System.currentTimeMillis();
        ultimoPlacement = null;                   // garante que a próxima varredura processe
    }

    /**
     * Botão "Jogar": o escape manual para quando o robô perde o fio — depois de uma popup que
     * corrompeu a leitura, tipicamente.
     *
     * <p>Reconstrói a posição direto do que está na tela, assumindo que é a nossa vez e que a
     * cor é a do combo. <b>O combo manda</b>, e é de propósito: este é o único caso que a
     * resolução automática de orientação não resolve sozinha (scanner iniciado no meio de um
     * final, com a orientação travada errada). Ajusta-se o combo e clica-se aqui.
     */
    private void forcaJogada()
    {
        if (robot == null || regiaoAtual == null)
        {
            informa("Inicie o scanner antes de usar 'Jogar'.");
            return;
        }
        if (ocupado)
        {
            informa("Ocupado; aguarde a jogada atual terminar.");
            return;
        }

        final PerfilDeTela perfil = (PerfilDeTela) comboAmbiente.getSelectedItem();
        final Rectangle regiao = regiaoAtual;
        ocupado = true;

        new Thread(() -> {
            try
            {
                // A leitura pode derrapar num frame isolado: tenta algumas capturas e usa a
                // primeira plausível.
                Visao.Leitura leitura = null;
                for (int tentativa = 0; tentativa < 8 && leitura == null; tentativa++)
                {
                    leitura = visao.le(robot, regiao, perfil);
                    if (leitura == null)
                        Clicador.dorme(150);
                }
                if (leitura == null)
                {
                    informa("Jogar: leitura instável; tente novamente.");
                    return;
                }

                boolean somosBrancas = comboCor.getSelectedIndex() == 0;
                Orientacao orientacao = somosBrancas ? Orientacao.BRANCAS : Orientacao.PRETAS;
                String placement = Grade.paraPlacement(Grade.orienta(leitura.grade, orientacao));

                if (!partida.reconstroiDe(placement, somosBrancas))
                {
                    informa("Jogar: a posição lida não forma uma FEN válida.");
                    return;
                }
                ultimoPlacement = placement;
                ultimaOrientacao = orientacao;
                candidataAVirada = null;

                informa("Jogada forçada de " + orientacao + " (sem histórico).");
                pensaEJoga(leitura.geometria, regiao, orientacao);
            }
            catch (Exception falha)
            {
                informa("Jogar: falha (" + falha.getClass().getSimpleName() + ": "
                    + falha.getMessage() + ")");
            }
            finally
            {
                ocupado = false;
            }
        }, "JChessCheater-forcar").start();
    }

    // =========================================================================
    //  Apoio
    // =========================================================================
    /**
     * Reinício da partida ao ver a posição inicial: ajusta a cor pelas peças que estão
     * EMBAIXO na tela e zera o estado. O fluxo normal recria a posição em seguida.
     */
    private void reiniciaDaPosicaoInicial(Orientacao orientacao)
    {
        final int indice = orientacao == Orientacao.BRANCAS ? 0 : 1;
        Runnable ajusta = () -> comboCor.setSelectedIndex(indice);
        if (SwingUtilities.isEventDispatchThread())
            ajusta.run();
        else
            try
            {
                SwingUtilities.invokeAndWait(ajusta);
            }
            catch (Exception ignorado)
            {
                // a cor ainda é derivada da orientação adiante; só o combo pode não refletir
            }

        partida.reinicia();
        nossosLancesJogados = 0;
        sorteiaAberturaRapida();
        limpaPendencia();
        informa("Início detectado: jogando de " + orientacao + ".");
    }

    /**
     * Orçamento de tempo do movimento do mouse, em ms.
     *
     * <ul>
     *   <li>final de baixo material: 500 ms fixos — ficar "pensando" num final de rei e peão
     *       é o que menos parece humano;</li>
     *   <li>abertura (primeiras jogadas, quantidade sorteada por partida): um quarto da
     *       latência, piso de 500 ms;</li>
     *   <li>caso geral: aleatório em [0, latência).</li>
     * </ul>
     */
    private int orcamento()
    {
        if (partida.finalDeBaixoMaterial())
            return Math.max(LATENCIA_MINIMA_MS, 500);

        int latencia = (Integer) spinnerLatencia.getValue();
        if (nossosLancesJogados < lancesRapidosDeAbertura)
            return Math.max(LATENCIA_MINIMA_MS, Math.max(latencia / 4, 500));

        int sorteio = latencia > 0 ? ThreadLocalRandom.current().nextInt(latencia) : 0;
        return Math.max(LATENCIA_MINIMA_MS, sorteio);
    }

    private void sorteiaAberturaRapida()
    {
        lancesRapidosDeAbertura = 3 + ThreadLocalRandom.current().nextInt(5);   // 3..7
    }

    private int sims()
    {
        Object v = comboSims.getSelectedItem();
        return v instanceof Integer ? (Integer) v : Configuracao.SIMS_PADRAO;
    }

    private void limpaPendencia()
    {
        pendenteUci = null;
        pendenteEsperado = null;
        pendenteAntes = null;
        pendenteDesde = 0L;
    }

    private void informa(String texto)
    {
        SwingUtilities.invokeLater(() -> rotuloDeEstado.setText(texto));
        System.out.println("[JChessCheater] " + texto);
    }
}
