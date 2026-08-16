package chesscheater.visao;

import java.awt.image.BufferedImage;

/**
 * Imagem em memória, separada por canal. Nada vai para disco em nenhum momento.
 *
 * <p>Três arrays de int em vez de um de ARGB porque o laço de detecção percorre a imagem
 * inteira várias vezes comparando canal a canal; separar evita um shift e uma máscara por
 * pixel por passada.
 */
public final class Rgb
{
    public final int largura, altura;
    public final int[] r, g, b;

    public Rgb(int largura, int altura, int[] r, int[] g, int[] b)
    {
        this.largura = largura;
        this.altura = altura;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public static Rgb de(BufferedImage img)
    {
        int w = img.getWidth(), h = img.getHeight();
        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        int[] r = new int[w * h], g = new int[w * h], b = new int[w * h];
        for (int i = 0; i < argb.length; i++)
        {
            int p = argb[i];
            r[i] = (p >> 16) & 0xFF;
            g[i] = (p >> 8) & 0xFF;
            b[i] = p & 0xFF;
        }
        return new Rgb(w, h, r, g, b);
    }

    /**
     * Cópia com brilho normalizado, ou {@code this} se a imagem já está clara o bastante.
     *
     * <p>Estima o nível de quase-branco (percentil alto da luminância) e aplica um ganho
     * UNIFORME para trazê-lo ao canônico. Rede de segurança: só é chamada quando a leitura
     * crua falha (ver {@link Visao}), então o caminho que já funciona nunca passa por aqui.
     */
    public Rgb normalizada()
    {
        int n = r.length;
        if (n == 0)
            return this;

        int[] hist = new int[256];
        for (int i = 0; i < n; i++)
            hist[(r[i] + g[i] + b[i]) / 3]++;

        long limite = (long) Math.ceil(PerfilDeTela.NORM_PERCENTIL * n);
        long acc = 0;
        int percentil = 255;
        for (int v = 0; v < 256; v++)
        {
            acc += hist[v];
            if (acc >= limite)
            {
                percentil = v;
                break;
            }
        }

        double ganho = percentil > 0 ? (double) PerfilDeTela.NORM_ALVO / percentil : 1.0;
        if (ganho <= 1.0)
            return this;                                  // já está clara
        if (ganho > PerfilDeTela.NORM_GANHO_MAX)
            ganho = PerfilDeTela.NORM_GANHO_MAX;

        int[] nr = new int[n], ng = new int[n], nb = new int[n];
        for (int i = 0; i < n; i++)
        {
            nr[i] = limita((int) Math.round(r[i] * ganho));
            ng[i] = limita((int) Math.round(g[i] * ganho));
            nb[i] = limita((int) Math.round(b[i] * ganho));
        }
        return new Rgb(largura, altura, nr, ng, nb);
    }

    /** Amostra bilinear dos três canais em coordenada fracionária. */
    public double[] bilinear(double x, double y)
    {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        int x1 = x0 + 1, y1 = y0 + 1;
        double fx = x - x0, fy = y - y0;
        x0 = corta(x0, 0, largura - 1);
        x1 = corta(x1, 0, largura - 1);
        y0 = corta(y0, 0, altura - 1);
        y1 = corta(y1, 0, altura - 1);

        int i00 = y0 * largura + x0, i10 = y0 * largura + x1;
        int i01 = y1 * largura + x0, i11 = y1 * largura + x1;
        return new double[] {
            interpola(r[i00], r[i10], r[i01], r[i11], fx, fy),
            interpola(g[i00], g[i10], g[i01], g[i11], fx, fy),
            interpola(b[i00], b[i10], b[i01], b[i11], fx, fy) };
    }

    private static double interpola(double v00, double v10, double v01, double v11,
                                    double fx, double fy)
    {
        double topo = v00 + (v10 - v00) * fx;
        double base = v01 + (v11 - v01) * fx;
        return topo + (base - topo) * fy;
    }

    public static int corta(int v, int min, int max)
    {
        return v < min ? min : (v > max ? max : v);
    }

    private static int limita(int v)
    {
        return corta(v, 0, 255);
    }
}
