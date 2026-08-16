package chesscheater.visao;

import java.util.Arrays;

/**
 * Lê a orientação do tabuleiro nos <b>rótulos de coordenada</b> — a verdade impressa na tela.
 *
 * <p>O chess.com desenha o número da fileira dentro da casa, no canto superior esquerdo da
 * coluna mais à esquerda. A casa do topo e a da base carregam sempre os dígitos extremos: com
 * as brancas embaixo o topo é '8' e a base é '1'; com as pretas embaixo é o inverso. Ler qual
 * dos dois é o '8' dá a orientação direto da tela — sem depender de material, de estado interno
 * nem de heurística.
 *
 * <p>É o único caminho que resolve o <i>bootstrap no meio de um final</i>, onde contar material
 * vira loteria. Abstém-se quando a tela não permite ler: coordenadas desligadas, cantos
 * ocupados (a peça é desenhada POR CIMA do rótulo) ou realce de último lance.
 */
public final class LeitorDeRotulos
{
    private LeitorDeRotulos() { }

    /**
     * A orientação deduzida dos rótulos, ou {@code null} se a tela não permitir ler.
     *
     * <p>Lê as duas pontas da coluna mais à esquerda. Quando as duas são legíveis, exige que
     * discordem (uma é '8' e a outra é '1'); se concordarem, uma das leituras está errada e o
     * método se abstém em vez de chutar.
     */
    public static Orientacao orientacao(Rgb im, Geometria geo, char[][] grade,
                                        PerfilDeTela perfil)
    {
        // Peça por cima do rótulo o torna ilegível: só casas vazias.
        Boolean topo = grade[0][0] == '.' ? eOitoOuNao(im, geo, 0, 0, perfil) : null;
        Boolean base = grade[7][0] == '.' ? eOitoOuNao(im, geo, 7, 0, perfil) : null;

        if (topo != null && base != null)
        {
            if (topo.booleanValue() == base.booleanValue())
                return null;                      // leitura incoerente: abstém
            return topo.booleanValue() ? Orientacao.BRANCAS : Orientacao.PRETAS;
        }
        if (topo != null)
            return topo.booleanValue() ? Orientacao.BRANCAS : Orientacao.PRETAS;
        if (base != null)
            return base.booleanValue() ? Orientacao.PRETAS : Orientacao.BRANCAS;
        return null;
    }

    /**
     * O glifo do rótulo desta casa é o dígito '8'?
     *
     * <p>{@code TRUE} = é '8'; {@code FALSE} = não tem olhal nenhum (nas linhas extremas isso
     * só pode ser '1'); {@code null} = ilegível, e o chamador deve se abster.
     *
     * <p>A discriminação é ABSOLUTA, não uma comparação de densidade: '8' tem dois olhais
     * fechados e '1' não tem nenhum. Contamos linhas do glifo com duas ou mais sequências de
     * tinta separadas — um proxy dos olhais que não exige análise de componentes conexas.
     * Medido na tela real com casa de 99 px: '8' dá 5 linhas, '1' dá 0.
     */
    static Boolean eOitoOuNao(Rgb im, Geometria geo, int linha, int coluna, PerfilDeTela perfil)
    {
        final PerfilDeTela.Rotulo rot = perfil.rotulo;
        final int n = rot.n;
        double[] luminancia = new double[n * n];

        for (int ty = 0; ty < n; ty++)
            for (int tx = 0; tx < n; tx++)
            {
                double sx = geo.x0 + (coluna + rot.x) * geo.lado
                          + (tx + 0.5) * rot.largura * geo.lado / n;
                double sy = geo.y0 + (linha + rot.y) * geo.lado
                          + (ty + 0.5) * rot.altura * geo.lado / n;
                double[] px = im.bilinear(sx, sy);
                luminancia[ty * n + tx] = (px[0] + px[1] + px[2]) / 3.0;
            }

        // Fundo = mediana do recorte (o glifo é minoria dos pixels).
        double[] ordenado = luminancia.clone();
        Arrays.sort(ordenado);
        final double fundo = ordenado[ordenado.length / 2];

        boolean[] tinta = new boolean[n * n];
        int quantaTinta = 0;
        for (int i = 0; i < tinta.length; i++)
        {
            tinta[i] = Math.abs(luminancia[i] - fundo) > rot.contraste;
            if (tinta[i])
                quantaTinta++;
        }

        double fracao = (double) quantaTinta / tinta.length;
        // Pouca tinta = coordenadas desligadas ou fora do tabuleiro; tinta demais = não é um
        // dígito (peça, realce de último lance, seta de análise).
        if (fracao < rot.tintaMin || fracao > rot.tintaMax)
            return null;

        int linhasComDoisTracos = 0;
        for (int ty = 0; ty < n; ty++)
        {
            int tracos = 0;
            boolean anterior = false;
            for (int tx = 0; tx < n; tx++)
            {
                boolean atual = tinta[ty * n + tx];
                if (atual && !anterior)
                    tracos++;
                anterior = atual;
            }
            if (tracos >= 2)
                linhasComDoisTracos++;
        }

        if (linhasComDoisTracos >= 2)
            return Boolean.TRUE;
        if (linhasComDoisTracos == 0)
            return Boolean.FALSE;
        return null;                              // uma linha só: ambíguo
    }
}
