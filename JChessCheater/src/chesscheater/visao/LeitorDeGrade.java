package chesscheater.visao;

import chesscheater.visao.ConjuntoDeTemplates.Template;

/**
 * Lê as 64 casas da tela e diz que peça está em cada uma.
 *
 * <p>Para cada casa: reamostra o recorte na resolução dos moldes, separa "frente" (o que não é
 * cor de casa) de fundo, e compara com os doze moldes por uma nota que mistura <b>forma</b>
 * (interseção sobre união das máscaras) e <b>desenho</b> (correlação normalizada). Meio a meio.
 *
 * <p>Casa vazia não é decidida por comparação e sim por ausência: abaixo de
 * {@link PerfilDeTela#FRACAO_DE_CASA_VAZIA} de frente, não há o que classificar. Sem esse
 * atalho, o molde mais "magro" venceria em toda casa vazia.
 */
public final class LeitorDeGrade
{
    private LeitorDeGrade() { }

    /** A grade lida da tela, em coordenadas de IMAGEM (linha 0 = topo da tela). */
    public static char[][] leGrade(Rgb im, Geometria geo, ConjuntoDeTemplates moldes,
                                   PerfilDeTela perfil)
    {
        char[][] grade = new char[8][8];
        double[] cinza = new double[moldes.tamanhoDoRecorte()];
        double[] frente = new double[moldes.tamanhoDoRecorte()];

        for (int linha = 0; linha < 8; linha++)
            for (int coluna = 0; coluna < 8; coluna++)
            {
                amostraCasa(im, geo, linha, coluna, moldes, perfil, cinza, frente);
                grade[linha][coluna] = classifica(cinza, frente, moldes);
            }
        return grade;
    }

    /** Reamostra uma casa para a resolução de referência dos moldes (bilinear). */
    static void amostraCasa(Rgb im, Geometria geo, int linha, int coluna,
                            ConjuntoDeTemplates moldes, PerfilDeTela perfil,
                            double[] cinzaSaida, double[] frenteSaida)
    {
        final double casaX = geo.x0 + coluna * geo.lado;
        final double casaY = geo.y0 + linha * geo.lado;
        final int lw = moldes.largura, lh = moldes.altura;

        for (int ty = 0; ty < lh; ty++)
        {
            double sy = casaY + (ty + 0.5) * geo.lado / lh - 0.5;
            for (int tx = 0; tx < lw; tx++)
            {
                double sx = casaX + (tx + 0.5) * geo.lado / lw - 0.5;
                double[] px = im.bilinear(sx, sy);
                int r = (int) Math.round(px[0]);
                int g = (int) Math.round(px[1]);
                int b = (int) Math.round(px[2]);
                int i = ty * lw + tx;
                cinzaSaida[i] = (px[0] + px[1] + px[2]) / 3.0;
                frenteSaida[i] = perfil.cores.ePixelDeCasa(r, g, b) ? 0.0 : 1.0;
            }
        }
    }

    /** @return o símbolo FEN da peça, ou '.' se a casa está vazia. */
    static char classifica(double[] cinza, double[] frente, ConjuntoDeTemplates moldes)
    {
        double somaFrente = 0;
        for (double v : frente)
            somaFrente += v;
        if (somaFrente / frente.length < PerfilDeTela.FRACAO_DE_CASA_VAZIA)
            return '.';

        double[] iso = new double[cinza.length];
        for (int i = 0; i < cinza.length; i++)
            iso[i] = cinza[i] * frente[i];

        char melhorSimbolo = '.';
        double melhorNota = -2.0;
        for (Template t : moldes.todos())
        {
            double intersecao = 0, somaMolde = 0;
            for (int i = 0; i < frente.length; i++)
            {
                intersecao += frente[i] * t.frente[i];
                somaMolde += t.frente[i];
            }
            double uniao = somaFrente + somaMolde - intersecao;
            double forma = uniao > 0 ? intersecao / uniao : 0.0;
            double nota = 0.5 * forma + 0.5 * correlacao(iso, t.iso);
            if (nota > melhorNota)
            {
                melhorNota = nota;
                melhorSimbolo = t.simbolo;
            }
        }
        return melhorSimbolo;
    }

    /** Correlação cruzada normalizada — insensível a brilho e contraste globais. */
    static double correlacao(double[] a, double[] b)
    {
        final int n = a.length;
        double mediaA = 0, mediaB = 0;
        for (int i = 0; i < n; i++)
        {
            mediaA += a[i];
            mediaB += b[i];
        }
        mediaA /= n;
        mediaB /= n;

        double num = 0, varA = 0, varB = 0;
        for (int i = 0; i < n; i++)
        {
            double xa = a[i] - mediaA, xb = b[i] - mediaB;
            num += xa * xb;
            varA += xa * xa;
            varB += xb * xb;
        }
        double den = Math.sqrt(varA * varB);
        return den > 0 ? num / den : 0.0;
    }
}
