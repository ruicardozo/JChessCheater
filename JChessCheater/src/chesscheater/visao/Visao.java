package chesscheater.visao;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * A fachada da máquina de visão: captura a tela e devolve um tabuleiro lido, ou nada.
 *
 * <p>É o <b>único</b> ponto por onde o resto do programa enxerga a tela. Quem chama não sabe o
 * que é template, limiar de cor ou banda de cobertura — pede uma {@link Leitura} e recebe
 * geometria + grade + orientação, ou {@code null} se a tela não permitiu ler agora.
 *
 * <p>Devolver {@code null} é normal e frequente: durante a animação de um lance, com uma popup
 * por cima ou com o tabuleiro fora da região, não há leitura confiável. O laço de jogo
 * simplesmente tenta de novo no próximo tique — nunca age sobre leitura duvidosa.
 */
public final class Visao
{
    private final ConjuntoDeTemplates moldes;

    public Visao() throws IOException
    {
        this.moldes = MoldesEmbutidos.carrega();
    }

    /** Uma leitura bem-sucedida: a imagem de onde saiu, a geometria e a grade. */
    public static final class Leitura
    {
        /**
         * A imagem fica junto porque a leitura de rótulos precisa reamostrar EXATAMENTE a
         * mesma imagem de onde a grade saiu — que pode ser a captura crua ou a cópia com
         * brilho normalizado.
         */
        public final Rgb imagem;
        public final Geometria geometria;
        public final char[][] grade;

        Leitura(Rgb imagem, Geometria geometria, char[][] grade)
        {
            this.imagem = imagem;
            this.geometria = geometria;
            this.grade = grade;
        }
    }

    /**
     * Captura a região e tenta ler o tabuleiro.
     *
     * <p>Duas tentativas: a leitura crua e, se ela falhar, uma com brilho normalizado. A
     * segunda é rede de segurança contra captura escura (gamma, perfil de cor, visualizador
     * reamostrando); o caminho que já funciona nunca chega nela.
     *
     * @return a leitura, ou {@code null} se não deu para ler com confiança
     */
    public Leitura le(Robot robot, Rectangle regiao, PerfilDeTela perfil)
    {
        BufferedImage captura = robot.createScreenCapture(regiao);
        Rgb imagem = Rgb.de(captura);

        Leitura leitura = tenta(imagem, perfil);
        if (leitura == null)
        {
            Rgb normalizada = imagem.normalizada();
            if (normalizada != imagem)
                leitura = tenta(normalizada, perfil);
        }
        return leitura;
    }

    /** Uma tentativa de leitura sobre uma imagem já capturada. Silenciosa. */
    public Leitura tenta(Rgb imagem, PerfilDeTela perfil)
    {
        Geometria geo = DetectorDeTabuleiro.detecta(imagem, perfil);
        if (geo == null || !geo.plausivel())
            return null;
        char[][] grade = LeitorDeGrade.leGrade(imagem, geo, moldes, perfil);
        if (Grade.motivoDeImplausibilidade(grade) != null)
            return null;
        return new Leitura(imagem, geo, grade);
    }

    /** A orientação lida dos rótulos de coordenada, ou {@code null} se ilegível. */
    public Orientacao orientacaoPorRotulo(Leitura leitura, PerfilDeTela perfil)
    {
        return LeitorDeRotulos.orientacao(leitura.imagem, leitura.geometria,
                                          leitura.grade, perfil);
    }
}
