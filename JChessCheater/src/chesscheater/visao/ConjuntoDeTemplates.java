package chesscheater.visao;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Os moldes das doze peças, reamostrados numa resolução de referência.
 *
 * <p>Cada molde guarda duas coisas: {@code iso}, a luminância isolada da peça (o desenho em si)
 * e {@code frente}, a máscara do que é peça e não é casa. A classificação usa as duas — forma
 * (interseção sobre união das máscaras) e correlação (do desenho) — porque nenhuma das duas
 * sozinha separa bispo de peão em casa escura.
 */
public final class ConjuntoDeTemplates
{
    /** Um molde de peça. O símbolo segue a FEN: maiúsculo = branca. */
    public static final class Template
    {
        public final char simbolo;
        public final double[] iso;
        public final double[] frente;

        Template(char simbolo, double[] iso, double[] frente)
        {
            this.simbolo = simbolo;
            this.iso = iso;
            this.frente = frente;
        }
    }

    public final int largura, altura;
    private final Map<Character, Template> porSimbolo;

    private ConjuntoDeTemplates(int largura, int altura, Map<Character, Template> porSimbolo)
    {
        this.largura = largura;
        this.altura = altura;
        this.porSimbolo = porSimbolo;
    }

    public Collection<Template> todos()
    {
        return porSimbolo.values();
    }

    public int tamanhoDoRecorte()
    {
        return largura * altura;
    }

    /** Lê o formato binário dos moldes (magic "FEN1" + cabeçalho + pares iso/frente). */
    public static ConjuntoDeTemplates carrega(byte[] dados) throws IOException
    {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(dados)))
        {
            int magic = in.readInt();
            if (magic != 0x46454E31)
                throw new IOException("Moldes inválidos (magic incorreto).");

            int w = in.readInt(), h = in.readInt(), quantos = in.readInt();
            int n = w * h;
            Map<Character, Template> mapa = new LinkedHashMap<>();
            byte[] isoB = new byte[n], frenteB = new byte[n];

            for (int k = 0; k < quantos; k++)
            {
                char simbolo = (char) in.readByte();
                leTudo(in, isoB);
                leTudo(in, frenteB);
                double[] iso = new double[n], frente = new double[n];
                for (int i = 0; i < n; i++)
                {
                    iso[i] = isoB[i] & 0xFF;
                    frente[i] = (frenteB[i] & 0xFF) / 255.0;
                }
                mapa.put(simbolo, new Template(simbolo, iso, frente));
            }
            return new ConjuntoDeTemplates(w, h, mapa);
        }
    }

    private static void leTudo(InputStream in, byte[] buf) throws IOException
    {
        int off = 0;
        while (off < buf.length)
        {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0)
                throw new IOException("Fim inesperado nos moldes.");
            off += n;
        }
    }
}
