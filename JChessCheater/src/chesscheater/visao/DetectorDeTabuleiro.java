package chesscheater.visao;

/**
 * Acha o tabuleiro dentro da região capturada.
 *
 * <p>A captura pode conter "verde" que não é tabuleiro — barra lateral, anúncios, tema escuro
 * do sistema. E, conforme o ambiente e o zoom do navegador, o tabuleiro fica em posições e
 * tamanhos diferentes. A estratégia é INDEPENDENTE de ambiente: localizar a caixa delimitadora
 * do maior bloco contíguo de cor-de-casa, com limiar BAIXO de cobertura.
 *
 * <p>São três passadas que se refinam mutuamente:
 * <ol>
 *   <li>colunas com cobertura acima do limiar (sobre toda a altura) → banda em X;</li>
 *   <li>linhas com cobertura acima do limiar, medidas SÓ dentro da banda X → banda em Y;</li>
 *   <li>refina X medindo só dentro da banda Y.</li>
 * </ol>
 * O resultado é a caixa do tabuleiro, e o lado da casa é o lado dividido por 8.
 */
public final class DetectorDeTabuleiro
{
    private DetectorDeTabuleiro() { }

    /** @return a geometria do tabuleiro, ou {@code null} se não achou. */
    public static Geometria detecta(Rgb im, PerfilDeTela perfil)
    {
        final int W = im.largura, H = im.altura;
        final PerfilDeTela.Cores cores = perfil.cores;

        int[] porColuna = new int[W];
        for (int y = 0; y < H; y++)
        {
            int base = y * W;
            for (int x = 0; x < W; x++)
                if (cores.ePixelDeCasa(im.r[base + x], im.g[base + x], im.b[base + x]))
                    porColuna[x]++;
        }

        int[] bandaX = maiorBloco(porColuna, H);
        if (bandaX == null)
            return null;

        int larguraDaBanda = bandaX[1] - bandaX[0] + 1;
        int[] porLinha = new int[H];
        for (int y = 0; y < H; y++)
        {
            int base = y * W, c = 0;
            for (int x = bandaX[0]; x <= bandaX[1]; x++)
                if (cores.ePixelDeCasa(im.r[base + x], im.g[base + x], im.b[base + x]))
                    c++;
            porLinha[y] = c;
        }

        int[] bandaY = maiorBloco(porLinha, larguraDaBanda);
        if (bandaY == null)
            return null;

        int alturaDaBanda = bandaY[1] - bandaY[0] + 1;
        int[] porColunaRefinada = new int[W];
        for (int x = 0; x < W; x++)
        {
            int c = 0;
            for (int y = bandaY[0]; y <= bandaY[1]; y++)
                if (cores.ePixelDeCasa(im.r[y * W + x], im.g[y * W + x], im.b[y * W + x]))
                    c++;
            porColunaRefinada[x] = c;
        }

        int[] bandaX2 = maiorBloco(porColunaRefinada, alturaDaBanda);
        if (bandaX2 == null)
            bandaX2 = bandaX;

        int larg = bandaX2[1] - bandaX2[0] + 1;
        int alt = bandaY[1] - bandaY[0] + 1;
        // O tabuleiro é quadrado: usamos o menor lado e ancoramos no canto da caixa.
        int lado = Math.min(larg, alt);
        return new Geometria(bandaX2[0], bandaY[0], lado / 8.0);
    }

    /**
     * Maior bloco contíguo de índices cuja cobertura excede o limiar, tolerando pequenas
     * falhas (até {@link PerfilDeTela#FALHA_TOLERADA} índices abaixo do limiar sem quebrar o
     * bloco).
     *
     * @return {início, fim} ou {@code null}
     */
    static int[] maiorBloco(int[] contagem, int total)
    {
        final double limiar = total * PerfilDeTela.COBERTURA_DO_TABULEIRO;
        int melhorInicio = -1, melhorFim = -1, melhorTam = 0;
        int inicio = -1, ultimoBom = -1, falhas = 0;

        for (int i = 0; i < contagem.length; i++)
        {
            if (contagem[i] > limiar)
            {
                if (inicio < 0)
                    inicio = i;
                ultimoBom = i;
                falhas = 0;
            }
            else if (inicio >= 0)
            {
                falhas++;
                if (falhas > PerfilDeTela.FALHA_TOLERADA)
                {
                    int tam = ultimoBom - inicio + 1;
                    if (tam > melhorTam)
                    {
                        melhorTam = tam;
                        melhorInicio = inicio;
                        melhorFim = ultimoBom;
                    }
                    inicio = -1;
                    falhas = 0;
                }
            }
        }
        if (inicio >= 0)
        {
            int tam = ultimoBom - inicio + 1;
            if (tam > melhorTam)
            {
                melhorInicio = inicio;
                melhorFim = ultimoBom;
            }
        }
        return melhorInicio < 0 ? null : new int[] { melhorInicio, melhorFim };
    }
}
