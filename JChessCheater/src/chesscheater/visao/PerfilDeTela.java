package chesscheater.visao;

/**
 * <b>O compartimento da imagem.</b> Tudo que depende de <i>como o chess.com desenha</i> mora
 * aqui — e só aqui.
 *
 * <p>Por que este arquivo existe separado: o chess.com pode mudar levemente o desenho (cor das
 * casas, posição do tabuleiro, tamanho do rótulo de coordenada) num sistema operacional e não
 * no outro. Quando isso acontecer, a reprogramação é <b>neste arquivo</b>, com screenshots
 * novos na mão. Nenhuma outra classe do projeto carrega número medido em tela.
 *
 * <p>Cada perfil carrega os seus PRÓPRIOS limiares. Se o Windows mudar e o Ubuntu não, ajusta-se
 * {@link #WINDOWS} sem tocar em {@link #UBUNTU} — era exatamente para isso que os limiares
 * deixaram de ser constantes globais.
 *
 * <p><b>Premissa de tela</b>: monitor Full HD (1920x1080), navegador maximizado. As regiões
 * abaixo foram medidas nessa configuração.
 */
public final class PerfilDeTela
{
    // =========================================================================
    //  Limiares de cor — o que é "casa de tabuleiro" na imagem
    // =========================================================================
    /**
     * Faixas RGB que reconhecem um pixel como sendo de casa (e não de peça).
     *
     * <p>São três famílias porque o chess.com pinta a casa clara, a casa escura e o realce
     * amarelo do último lance. O realce PRECISA contar como casa: sem ele, a casa realçada
     * viraria um borrão de "peça" e a detecção do tabuleiro perderia duas casas por lance.
     */
    public static final class Cores
    {
        public final int claraRMin, claraGMin, claraBMin;
        public final int escuraRMin, escuraRMax, escuraGMin, escuraGMax, escuraBMin, escuraBMax;
        public final int amarelaRMin, amarelaGMin, amarelaBMax, amarelaDeltaRG;

        Cores(int claraRMin, int claraGMin, int claraBMin,
              int escuraRMin, int escuraRMax, int escuraGMin, int escuraGMax,
              int escuraBMin, int escuraBMax,
              int amarelaRMin, int amarelaGMin, int amarelaBMax, int amarelaDeltaRG)
        {
            this.claraRMin = claraRMin;
            this.claraGMin = claraGMin;
            this.claraBMin = claraBMin;
            this.escuraRMin = escuraRMin;
            this.escuraRMax = escuraRMax;
            this.escuraGMin = escuraGMin;
            this.escuraGMax = escuraGMax;
            this.escuraBMin = escuraBMin;
            this.escuraBMax = escuraBMax;
            this.amarelaRMin = amarelaRMin;
            this.amarelaGMin = amarelaGMin;
            this.amarelaBMax = amarelaBMax;
            this.amarelaDeltaRG = amarelaDeltaRG;
        }

        /** O pixel é de casa (clara, escura ou realçada)? */
        public boolean ePixelDeCasa(int r, int g, int b)
        {
            boolean clara = r > claraRMin && g > claraGMin && b > claraBMin;
            boolean escura = r > escuraRMin && r < escuraRMax
                          && g > escuraGMin && g < escuraGMax
                          && b > escuraBMin && b < escuraBMax;
            boolean amarela = r > amarelaRMin && g > amarelaGMin && b < amarelaBMax
                           && Math.abs(r - g) < amarelaDeltaRG;
            return clara || escura || amarela;
        }
    }

    /** Limiares medidos no tema padrão do chess.com. Valem hoje nos dois sistemas. */
    public static final Cores CORES_PADRAO = new Cores(
        200, 200, 170,               // clara: creme
        90, 150, 120, 175, 60, 120,  // escura: verde
        180, 180, 160, 40);          // amarela: realce do último lance

    // =========================================================================
    //  Rótulo de coordenada — como o dígito do rank é desenhado dentro da casa
    // =========================================================================
    /**
     * Recorte e limiares do rótulo de rank, usados para ler a orientação do tabuleiro
     * direto da tela (ver {@link LeitorDeRotulos}).
     *
     * <p>Duas particularidades do desenho, medidas na tela real: o rótulo é pintado na COR DA
     * CASA OPOSTA (por isso {@link Cores#ePixelDeCasa} aceita as duas e não serve para achar o
     * glifo — o que o isola é o módulo do desvio de luminância); e a peça é desenhada POR CIMA
     * do rótulo, então só se lê rótulo de casa vazia.
     */
    public static final class Rotulo
    {
        /** Recorte dentro da casa, em fração do lado. */
        public final double x, y, largura, altura;
        /** Resolução de reamostragem do recorte. */
        public final int n;
        /** Desvio de luminância (vs. fundo da casa) a partir do qual é tinta. */
        public final double contraste;
        /** Fora desta faixa de tinta o recorte não é um dígito legível. */
        public final double tintaMin, tintaMax;

        Rotulo(double x, double y, double largura, double altura, int n,
               double contraste, double tintaMin, double tintaMax)
        {
            this.x = x;
            this.y = y;
            this.largura = largura;
            this.altura = altura;
            this.n = n;
            this.contraste = contraste;
            this.tintaMin = tintaMin;
            this.tintaMax = tintaMax;
        }
    }

    public static final Rotulo ROTULO_PADRAO =
        new Rotulo(0.03, 0.04, 0.20, 0.30, 24, 30.0, 0.03, 0.45);

    // =========================================================================
    //  Os perfis
    // =========================================================================
    public final String nome;
    /** Região de captura, relativa ao canto do monitor escolhido. */
    public final int x, y, largura, altura;
    public final Cores cores;
    public final Rotulo rotulo;

    private PerfilDeTela(String nome, int x, int y, int largura, int altura,
                         Cores cores, Rotulo rotulo)
    {
        this.nome = nome;
        this.x = x;
        this.y = y;
        this.largura = largura;
        this.altura = altura;
        this.cores = cores;
        this.rotulo = rotulo;
    }

    // Referências medidas em 1920x1080, navegador maximizado, chess.com:
    //   Windows: tabuleiro x:253..1028  y:187..962
    //   Ubuntu : tabuleiro x:299..1122  y:183..1006
    // Cada preset cobre o tabuleiro do seu ambiente com ~50px de folga. A folga basta porque
    // o detector ainda refina a posição exata DENTRO da região capturada — a região só
    // precisa CONTER o tabuleiro.

    public static final PerfilDeTela WINDOWS =
        new PerfilDeTela("Windows", 200, 135, 880, 880, CORES_PADRAO, ROTULO_PADRAO);

    public static final PerfilDeTela UBUNTU =
        new PerfilDeTela("Ubuntu", 245, 130, 930, 930, CORES_PADRAO, ROTULO_PADRAO);

    /** União dos dois, para quando o usuário não sabe qual usar. */
    public static final PerfilDeTela GENERICO =
        new PerfilDeTela("Genérico (união)", 200, 130, 980, 930, CORES_PADRAO, ROTULO_PADRAO);

    public static final PerfilDeTela[] TODOS = { GENERICO, WINDOWS, UBUNTU };

    /** O perfil correspondente ao sistema operacional em que estamos rodando. */
    public static PerfilDeTela detectado()
    {
        String so = System.getProperty("os.name", "").toLowerCase();
        if (so.contains("win"))
            return WINDOWS;
        if (so.contains("nux") || so.contains("nix"))
            return UBUNTU;
        return GENERICO;
    }

    @Override
    public String toString()
    {
        return nome;
    }

    // =========================================================================
    //  Constantes da leitura que NÃO dependem do sistema operacional
    // =========================================================================
    /**
     * Fração mínima de pixels de casa numa coluna/linha para ela "tocar" o tabuleiro.
     *
     * <p>É baixa de propósito: na posição inicial as fileiras 1,2,7,8 estão cheias de peças e
     * a fração de pixels de cor-de-casa nelas cai bem abaixo de 50%. 15% ainda detecta a
     * fileira e rejeita o ruído da barra lateral.
     */
    public static final double COBERTURA_DO_TABULEIRO = 0.15;

    /** Tolerância de falhas (em pixels) dentro de um bloco contíguo. */
    public static final int FALHA_TOLERADA = 4;

    /** Abaixo desta fração de "frente" a casa é considerada vazia. */
    public static final double FRACAO_DE_CASA_VAZIA = 0.03;

    /** Lado da casa fora desta faixa (px) = leitura absurda, descartada. */
    public static final double LADO_MIN = 30, LADO_MAX = 200;

    /**
     * Ordem das peças no menu de promoção do chess.com, a partir da casa de destino em
     * direção ao centro. Se um teste ao vivo mostrar ordem diferente, basta reordenar aqui.
     */
    public static final String ORDEM_DO_MENU_DE_PROMOCAO = "qnrb";

    // ── Normalização de brilho (rede de segurança contra dimming/gamma) ──────
    // Os limiares de cor são ABSOLUTOS. Quando a captura chega mais escura que o esperado
    // (visualizador em tela cheia reamostrando, cadeia de screenshot, perfil de cor), a casa
    // clara cai abaixo do limiar e a segmentação casa/peça COLAPSA. Só usada como FALLBACK
    // quando a leitura crua falha, então o caminho que já funciona nunca passa por aqui.
    public static final int NORM_ALVO = 245;
    public static final double NORM_PERCENTIL = 0.995;
    public static final double NORM_GANHO_MAX = 1.6;
}
