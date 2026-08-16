package chesscheater.visao;

/**
 * Geometria do tabuleiro dentro da imagem capturada: canto superior esquerdo e lado da casa.
 *
 * <p>O lado é {@code double} de propósito. O tabuleiro do chess.com raramente mede um múltiplo
 * exato de 8 pixels, e arredondar aqui acumularia erro até a oitava casa — o suficiente para
 * amostrar a casa vizinha na borda do tabuleiro.
 */
public final class Geometria
{
    public final int x0, y0;
    public final double lado;

    public Geometria(int x0, int y0, double lado)
    {
        this.x0 = x0;
        this.y0 = y0;
        this.lado = lado;
    }

    /** O lado da casa está numa faixa plausível para uma tela Full HD? */
    public boolean plausivel()
    {
        return lado >= PerfilDeTela.LADO_MIN && lado <= PerfilDeTela.LADO_MAX;
    }

    /** Centro da célula (coluna,linha) da grade, em pixels da imagem capturada. */
    public double[] centroDaCelula(double coluna, double linha)
    {
        return new double[] { x0 + (coluna + 0.5) * lado, y0 + (linha + 0.5) * lado };
    }

    /**
     * Converte (coluna,fileira) de xadrez — a=0..h=7, fileira 1=0..8=7 — para (coluna,linha)
     * da grade na tela, respeitando a orientação.
     *
     * <p>Com as brancas embaixo, a8 fica no topo-esquerda; com as pretas embaixo o tabuleiro
     * está girado 180 graus.
     */
    public static int[] paraColunaLinha(int coluna, int fileira, Orientacao orientacao)
    {
        if (orientacao == Orientacao.BRANCAS)
            return new int[] { coluna, 7 - fileira };
        return new int[] { 7 - coluna, fileira };
    }

    @Override
    public String toString()
    {
        return String.format("x0=%d y0=%d lado=%.2f", x0, y0, lado);
    }
}
