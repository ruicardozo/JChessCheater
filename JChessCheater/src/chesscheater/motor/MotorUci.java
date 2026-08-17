package chesscheater.motor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import chesscheater.config.Configuracao;

/**
 * A ponte com o <b>jchessai.jar</b> — a nossa rede v16a + MCTS, falando UCI.
 *
 * <p>É a única coisa que este programa sabe sobre motores de xadrez: um subprocesso que recebe
 * {@code position} + {@code go} e devolve {@code bestmove}. Sem Python, sem C++, sem
 * biblioteca nativa.
 *
 * <h2>Por que mandamos a partida inteira, e não a FEN</h2>
 * A rede v16a recebe <b>4 frames</b> — a posição atual e as três anteriores. Mandar
 * {@code position startpos moves e2e4 e7e5 ...} entrega o histórico REAL; mandar só a FEN
 * deixa os três frames anteriores ZERADOS, que é uma entrada diferente e produz outro lance.
 * Isso não é detalhe: medido no JChessAI, a mesma FEN com e sem histórico dá lance diferente
 * em 9 de 20 posições. Por isso {@link #melhorLanceDaPartida} é o caminho normal e
 * {@link #melhorLanceDaFen} existe só para o resgate, quando o histórico se perdeu.
 *
 * <h2>A armadilha do protocolo</h2>
 * Qualquer comando enviado durante um {@code go} <b>interrompe a busca</b> e devolve um lance
 * ruim com {@code nodes 0}. Por isso esta classe é estritamente pergunta-resposta: nada é
 * escrito enquanto um {@code bestmove} não chegou.
 */
public final class MotorUci implements AutoCloseable
{
    private final Path jar;
    private final Path pesos;

    private Process processo;
    private BufferedWriter entrada;
    private Thread leitor;
    private final Deque<String> linhas = new ArrayDeque<>();
    private final Object trava = new Object();
    private volatile boolean vivo = false;
    private int simsAtual = -1;

    public MotorUci(Path jar, Path pesos)
    {
        this.jar = jar;
        this.pesos = pesos;
    }

    /** Constrói a partir do JSON mestre — quem decide os caminhos é ele. */
    public static MotorUci de(Configuracao config)
    {
        return new MotorUci(config.motor(), config.pesos());
    }

    public Path jar()   { return jar; }
    public Path pesos() { return pesos; }
    public boolean estaVivo() { return vivo && processo != null && processo.isAlive(); }

    /**
     * Sobe o motor e faz o aperto de mão UCI. Idempotente: chamar de novo com o motor vivo
     * não faz nada.
     *
     * @throws IOException se faltar o JAR, faltarem os pesos ou o motor não responder
     */
    public void inicia() throws IOException
    {
        if (estaVivo())
            return;

        if (!Files.isRegularFile(jar))
            throw new IOException("jchessai.jar não encontrado em: " + jar.toAbsolutePath()
                + "\nPonha-o ao lado do jchesscheater.jar, ou ajuste \"motor\" no "
                + Configuracao.NOME_PADRAO + ".");
        if (!Files.isRegularFile(pesos))
            throw new IOException("pesos não encontrados em: " + pesos.toAbsolutePath()
                + "\nAjuste \"pesos\" no " + Configuracao.NOME_PADRAO
                + " para apontar a iteração que você quer usar.");

        // O mesmo java que roda este programa. O jchessai.jar é class file 65 (Java 21), e
        // este programa também usa as classes de xadrez de dentro dele — então se estamos
        // rodando, o java corrente serve.
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();

        ProcessBuilder pb = new ProcessBuilder(java, "-jar", jar.toString(),
                                               "--weights", pesos.toString());
        pb.redirectErrorStream(true);
        processo = pb.start();
        entrada = new BufferedWriter(new OutputStreamWriter(
            processo.getOutputStream(), StandardCharsets.UTF_8));
        vivo = true;

        final BufferedReader saida = new BufferedReader(new InputStreamReader(
            processo.getInputStream(), StandardCharsets.UTF_8));
        leitor = new Thread(() -> {
            try
            {
                String linha;
                while ((linha = saida.readLine()) != null)
                    synchronized (trava)
                    {
                        linhas.addLast(linha.trim());
                        trava.notifyAll();
                    }
            }
            catch (IOException fim)
            {
                // processo encerrado: nada a fazer
            }
            finally
            {
                vivo = false;
                synchronized (trava)
                {
                    trava.notifyAll();
                }
            }
        }, "MotorUci-leitor");
        leitor.setDaemon(true);
        leitor.start();

        envia("uci");
        esperaPor("uciok", 30_000);
        simsAtual = -1;                            // força o setoption no primeiro lance
        envia("isready");
        esperaPor("readyok", 180_000);             // a carga da rede leva ~100ms, mas o
                                                   // primeiro isready também compila a JVM
    }

    /** Ajusta o número de simulações por lance. Barato: só reenvia quando muda. */
    public void defineSims(int sims) throws IOException
    {
        if (sims == simsAtual)
            return;
        garanteVivo();
        envia("setoption name Sims value " + sims);
        simsAtual = sims;
    }

    /** Avisa o motor que começou outra partida (descarta a árvore de busca). */
    public void novaPartida() throws IOException
    {
        garanteVivo();
        envia("ucinewgame");
        envia("isready");
        esperaPor("readyok", 60_000);
    }

    /**
     * O melhor lance a partir da <b>partida inteira</b> — com o histórico de 4 frames real.
     * É o caminho normal.
     *
     * @param lances a sequência UCI desde a posição inicial
     * @return o lance em UCI, ou {@code null} se o motor não respondeu um lance jogável
     */
    public String melhorLanceDaPartida(List<String> lances) throws IOException
    {
        StringBuilder sb = new StringBuilder("position startpos");
        if (!lances.isEmpty())
        {
            sb.append(" moves");
            for (String l : lances)
                sb.append(' ').append(l);
        }
        return pergunta(sb.toString());
    }

    /**
     * O melhor lance a partir de uma FEN solta — <b>sem histórico</b>.
     *
     * <p>Só para o resgate: quando a posição foi reconstruída da tela e a sequência de lances
     * se perdeu. A rede recebe os três frames anteriores zerados e joga um pouco diferente.
     */
    public String melhorLanceDaFen(String fen) throws IOException
    {
        return pergunta("position fen " + fen);
    }

    /** Manda a posição, pede o lance e espera o {@code bestmove}. */
    private String pergunta(String comandoDePosicao) throws IOException
    {
        garanteVivo();
        synchronized (trava)
        {
            linhas.clear();
        }
        envia(comandoDePosicao);
        envia("go");

        String bestmove = esperaPor("bestmove", 300_000);
        String[] partes = bestmove.split("\\s+");
        if (partes.length < 2 || partes[1].equals("0000") || partes[1].length() < 4)
            return null;
        return partes[1];
    }

    /** As linhas de {@code info} da última busca, para o log. */
    public List<String> ultimasInfos()
    {
        List<String> saida = new ArrayList<>();
        synchronized (trava)
        {
            for (String l : linhas)
                if (l.startsWith("info depth"))
                    saida.add(l);
        }
        return saida;
    }

    private void garanteVivo() throws IOException
    {
        if (!estaVivo())
            throw new IOException("o motor não está rodando");
    }

    private void envia(String comando) throws IOException
    {
        entrada.write(comando);
        entrada.write('\n');
        entrada.flush();
    }

    /** Espera a primeira linha que comece com o prefixo, consumindo o que vier antes. */
    private String esperaPor(String prefixo, long prazoMs) throws IOException
    {
        final long limite = System.currentTimeMillis() + prazoMs;
        synchronized (trava)
        {
            while (true)
            {
                // Iterator, e não for-each com remove: remover durante o for-each estoura
                // ConcurrentModificationException na primeira linha que casar.
                for (java.util.Iterator<String> it = linhas.iterator(); it.hasNext(); )
                {
                    String linha = it.next();
                    if (linha.startsWith(prefixo))
                    {
                        it.remove();
                        return linha;
                    }
                }

                long resta = limite - System.currentTimeMillis();
                if (resta <= 0)
                    throw new IOException("o motor não respondeu '" + prefixo + "' a tempo");
                if (!vivo)
                    throw new IOException("o motor morreu esperando '" + prefixo + "'");
                try
                {
                    trava.wait(Math.min(resta, 200));
                }
                catch (InterruptedException interrompido)
                {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrompido esperando '" + prefixo + "'");
                }
            }
        }
    }

    @Override
    public void close()
    {
        vivo = false;
        if (processo == null)
            return;
        try
        {
            envia("quit");
            processo.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
        }
        catch (Exception naoRespondeu)
        {
            // segue para o destroy
        }
        finally
        {
            processo.destroy();
            processo = null;
        }
    }
}
