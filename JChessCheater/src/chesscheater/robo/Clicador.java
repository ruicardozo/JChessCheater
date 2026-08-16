package chesscheater.robo;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.concurrent.ThreadLocalRandom;

import chesscheater.visao.Geometria;
import chesscheater.visao.Orientacao;
import chesscheater.visao.PerfilDeTela;

/**
 * Executa o lance na tela com movimento humanizado.
 *
 * <p>Nada aqui altera QUAL lance é jogado — apenas COMO o cursor chega até as casas. Em vez de
 * teleportar o ponteiro e clicar no centro exato, o trajeto tem:
 *
 * <ul>
 *   <li><b>curva com ruído senoidal</b>, o tremor da mão, com envelope que zera nas pontas
 *       (começa preciso e termina preciso, curvando no meio);</li>
 *   <li><b>perfil de velocidade ease-in-out</b> — acelera e desacelera — com micro-pausas
 *       ocasionais, como quem hesita;</li>
 *   <li><b>ponto de clique disperso</b> dentro da casa, com raio gaussiano truncado: provável
 *       no centro, raro na borda, nunca fora.</li>
 * </ul>
 *
 * <p>O orçamento de tempo é respeitado: os pesos por passo são normalizados ANTES do trajeto,
 * então as hesitações não estouram o total.
 */
public final class Clicador
{
    private final Robot robot;

    public Clicador(Robot robot)
    {
        this.robot = robot;
    }

    /**
     * Joga o lance UCI ("e2e4", "e1g1", "e7e8q") na tela.
     *
     * @param orcamentoMs tempo total a gastar arrastando o ponteiro até as casas; ~65% para
     *                    chegar à origem e ~35% para ir ao destino
     */
    public void jogaLance(String uci, Geometria geo, Rectangle regiao,
                          Orientacao orientacao, int orcamentoMs)
    {
        int colunaOrigem = uci.charAt(0) - 'a', fileiraOrigem = uci.charAt(1) - '1';
        int colunaDestino = uci.charAt(2) - 'a', fileiraDestino = uci.charAt(3) - '1';

        int msAteOrigem = Math.max(120, (int) Math.round(orcamentoMs * 0.65));
        int msAteDestino = Math.max(90, (int) Math.round(orcamentoMs * 0.35));

        clicaCasa(colunaOrigem, fileiraOrigem, geo, regiao, orientacao, msAteOrigem);
        dorme(60 + ThreadLocalRandom.current().nextInt(120));
        clicaCasa(colunaDestino, fileiraDestino, geo, regiao, orientacao, msAteDestino);

        if (uci.length() == 5)
        {
            char promocao = Character.toLowerCase(uci.charAt(4));
            dorme(200 + ThreadLocalRandom.current().nextInt(150));
            clicaPromocao(colunaDestino, fileiraDestino, promocao, geo, regiao, orientacao);
        }

        // A mão não congela sobre a casa: por mais 0,5-1s o ponteiro vagueia pelas
        // proximidades, como quem tira a mão do mouse.
        vagueia(500 + ThreadLocalRandom.current().nextInt(501));
    }

    /**
     * Clica a peça de promoção no menu do chess.com.
     *
     * <p>O menu é uma coluna vertical ancorada na casa de destino e cresce SEMPRE da borda em
     * direção ao centro do tabuleiro: destino no topo da tela (linha 0) → menu desce; destino
     * embaixo (linha 7) → menu sobe. Vale para as duas cores e as duas orientações. O primeiro
     * item (Dama) fica sobre a própria casa de destino.
     */
    void clicaPromocao(int colunaDestino, int fileiraDestino, char promocao,
                       Geometria geo, Rectangle regiao, Orientacao orientacao)
    {
        int[] cl = Geometria.paraColunaLinha(colunaDestino, fileiraDestino, orientacao);
        int coluna = cl[0], linha = cl[1];

        int indice = PerfilDeTela.ORDEM_DO_MENU_DE_PROMOCAO.indexOf(promocao);
        if (indice < 0)
            indice = 0;                                   // desconhecido → Dama (defensivo)

        int direcao = (linha == 0) ? +1 : -1;
        double linhaDoMenu = linha + direcao * indice;

        double[] centro = geo.centroDaCelula(coluna, linhaDoMenu);
        double[] desvio = desvioGaussiano(geo.lado);
        int alvoX = (int) Math.round(regiao.x + centro[0] + desvio[0]);
        int alvoY = (int) Math.round(regiao.y + centro[1] + desvio[1]);

        moveComoHumano(alvoX, alvoY, 220 + ThreadLocalRandom.current().nextInt(180));
        dorme(20 + ThreadLocalRandom.current().nextInt(40));
        pressionaESolta();
    }

    /** Move até um ponto disperso dentro da casa gastando ~duracaoMs, e clica. */
    void clicaCasa(int coluna, int fileira, Geometria geo, Rectangle regiao,
                   Orientacao orientacao, int duracaoMs)
    {
        int[] cl = Geometria.paraColunaLinha(coluna, fileira, orientacao);
        double[] centro = geo.centroDaCelula(cl[0], cl[1]);
        double[] desvio = desvioGaussiano(geo.lado);

        int alvoX = (int) Math.round(regiao.x + centro[0] + desvio[0]);
        int alvoY = (int) Math.round(regiao.y + centro[1] + desvio[1]);

        moveComoHumano(alvoX, alvoY, duracaoMs);
        dorme(20 + ThreadLocalRandom.current().nextInt(50));   // assentamento da mão
        pressionaESolta();
    }

    private void pressionaESolta()
    {
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        dorme(30 + ThreadLocalRandom.current().nextInt(40));
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    /**
     * Desloca o ponteiro sem clicar por ~duracaoMs, em um ou dois saltos curtos nas
     * proximidades da posição atual.
     */
    public void vagueia(int duracaoMs)
    {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Point atual = ponteiro();
        if (atual == null)
        {
            dorme(duracaoMs);
            return;
        }

        int saltos = 1 + r.nextInt(2);
        int x = atual.x, y = atual.y;
        for (int i = 0; i < saltos; i++)
        {
            int fatia = (i == saltos - 1)
                ? duracaoMs - (duracaoMs / saltos) * i
                : duracaoMs / saltos;
            double angulo = r.nextDouble() * 2 * Math.PI;
            double distancia = 20 + r.nextDouble() * 100;
            int nx = x + (int) Math.round(Math.cos(angulo) * distancia);
            int ny = y + (int) Math.round(Math.sin(angulo) * distancia);
            moveComoHumano(nx, ny, Math.max(120, fatia));
            x = nx;
            y = ny;
        }
    }

    /**
     * Desvio (dx,dy) a partir do centro da casa, com raio gaussiano truncado.
     *
     * <p>A margem de segurança importa: um clique na borda pode cair na casa vizinha e jogar
     * outro lance. O truncamento em 72% do meio-lado garante que isso não acontece.
     */
    static double[] desvioGaussiano(double lado)
    {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double raioMaximo = (lado / 2.0) * 0.72;
        double sigma = raioMaximo * 0.40;

        double raio = Math.abs(r.nextGaussian()) * sigma;
        if (raio > raioMaximo)
            raio = raioMaximo;

        double angulo = r.nextDouble() * 2.0 * Math.PI;
        return new double[] { raio * Math.cos(angulo), raio * Math.sin(angulo) };
    }

    /** Arrasta o ponteiro até (alvoX,alvoY) ao longo de ~duracaoMs. */
    public void moveComoHumano(int alvoX, int alvoY, int duracaoMs)
    {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Point inicio = ponteiro();
        if (inicio == null)
        {
            robot.mouseMove(alvoX, alvoY);
            return;
        }

        double x0 = inicio.x, y0 = inicio.y;
        double dx = alvoX - x0, dy = alvoY - y0;
        double distancia = Math.hypot(dx, dy);
        if (distancia < 3.0)
        {
            robot.mouseMove(alvoX, alvoY);
            return;
        }

        // Vetor perpendicular à trajetória, para o ruído lateral.
        double ux = dx / distancia, uy = dy / distancia;
        double px = -uy, py = ux;

        double amplitude = Math.min(distancia * 0.06, 14.0) * (0.6 + r.nextDouble() * 0.8);
        double f1 = 1.0 + r.nextDouble() * 1.5;
        double f2 = 2.0 + r.nextDouble() * 2.5;
        double fase1 = r.nextDouble() * Math.PI * 2;
        double fase2 = r.nextDouble() * Math.PI * 2;
        double peso2 = 0.25 + r.nextDouble() * 0.25;

        int passos = Math.max(12, Math.min(90, duracaoMs / 8));

        // Pesos de tempo por passo: maiores nas pontas (a mão acelera e desacelera), menores
        // no meio, com hesitações ocasionais. Normalizados ANTES para caber no orçamento.
        double[] peso = new double[passos + 1];
        double somaDosPesos = 0;
        for (int i = 1; i <= passos; i++)
        {
            double t = (double) i / passos;
            peso[i] = 2.0 - 1.4 * Math.sin(Math.PI * t);
            if (r.nextDouble() < 0.08)
                peso[i] += 2.0 + r.nextDouble() * 3.0;
            somaDosPesos += peso[i];
        }

        long inicioNs = System.nanoTime();
        for (int i = 1; i <= passos; i++)
        {
            double t = (double) i / passos;
            double suave = suavizacao(t);

            double bx = x0 + dx * suave;
            double by = y0 + dy * suave;

            double envelope = Math.sin(Math.PI * t);
            double lateral = amplitude * envelope
                * (Math.sin(f1 * Math.PI * 2 * t + fase1)
                 + peso2 * Math.sin(f2 * Math.PI * 2 * t + fase2));

            double jitterX = (r.nextDouble() - 0.5) * 1.6;
            double jitterY = (r.nextDouble() - 0.5) * 1.6;

            robot.mouseMove((int) Math.round(bx + px * lateral + jitterX),
                            (int) Math.round(by + py * lateral + jitterY));
            dorme((long) Math.max(2, duracaoMs * (peso[i] / somaDosPesos)));
        }

        robot.mouseMove(alvoX, alvoY);            // garante o ponto exato escolhido

        long decorrido = (System.nanoTime() - inicioNs) / 1_000_000L;
        long restante = duracaoMs - decorrido;
        if (restante > 0)
            dorme(Math.min(restante, 200));
    }

    private static Point ponteiro()
    {
        try
        {
            return MouseInfo.getPointerInfo().getLocation();
        }
        catch (Exception ambienteRestrito)
        {
            return null;                          // alguns ambientes não expõem; há fallback
        }
    }

    /** smoothstep clássico: 3t² - 2t³. */
    static double suavizacao(double t)
    {
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        return t * t * (3 - 2 * t);
    }

    public static void dorme(long ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException interrompido)
        {
            Thread.currentThread().interrupt();
        }
    }
}
