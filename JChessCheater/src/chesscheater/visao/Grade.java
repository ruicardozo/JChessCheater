package chesscheater.visao;

/**
 * Operações sobre a grade 8x8 lida da tela: girar, virar FEN e checar plausibilidade.
 *
 * <p>A grade sai da tela em coordenadas de IMAGEM (linha 0 = topo). O resto do programa fala
 * em <i>placement</i> — o primeiro campo da FEN, sempre na perspectiva das brancas. A conversão
 * entre os dois é o que {@link #orienta} faz, e é o único lugar onde a orientação vira geometria.
 */
public final class Grade
{
    private Grade() { }

    /** Placement da posição inicial padrão. */
    public static final String PLACEMENT_INICIAL = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR";

    /** Gira a grade para a perspectiva das brancas, conforme a orientação da tela. */
    public static char[][] orienta(char[][] grade, Orientacao orientacao)
    {
        if (orientacao == Orientacao.BRANCAS)
            return grade;
        char[][] saida = new char[8][8];
        for (int l = 0; l < 8; l++)
            for (int c = 0; c < 8; c++)
                saida[l][c] = grade[7 - l][7 - c];
        return saida;
    }

    /** Grade (já orientada) → placement, o primeiro campo da FEN. */
    public static String paraPlacement(char[][] grade)
    {
        StringBuilder sb = new StringBuilder();
        for (int l = 0; l < 8; l++)
        {
            int vazias = 0;
            for (int c = 0; c < 8; c++)
            {
                char ch = grade[l][c];
                if (ch == '.')
                    vazias++;
                else
                {
                    if (vazias > 0)
                    {
                        sb.append(vazias);
                        vazias = 0;
                    }
                    sb.append(ch);
                }
            }
            if (vazias > 0)
                sb.append(vazias);
            if (l < 7)
                sb.append('/');
        }
        return sb.toString();
    }

    /**
     * Por que a grade NÃO é um tabuleiro plausível, ou {@code null} se estiver ok.
     *
     * <p>É a rede de segurança contra o frame ruim: durante a animação de um lance, ou com uma
     * popup por cima, a leitura devolve peças fantasma. Rejeitar aqui é muito mais barato que
     * descobrir depois — um placement impossível envenenaria o estado da partida.
     */
    public static String motivoDeImplausibilidade(char[][] grade)
    {
        int reisBrancos = 0, reisPretos = 0, pecas = 0, peoesBrancos = 0, peoesPretos = 0;
        for (char[] linha : grade)
            for (char ch : linha)
            {
                if (ch == 'K') reisBrancos++;
                if (ch == 'k') reisPretos++;
                if (ch == 'P') peoesBrancos++;
                if (ch == 'p') peoesPretos++;
                if (ch != '.') pecas++;
            }

        if (reisBrancos != 1 || reisPretos != 1)
            return "reis: brancos=" + reisBrancos + " pretos=" + reisPretos + " (esperado 1 de cada)";
        if (pecas < 2 || pecas > 32)
            return "total de peças=" + pecas + " (fora de [2,32])";
        if (peoesBrancos > 8 || peoesPretos > 8)
            return "peões: brancos=" + peoesBrancos + " pretos=" + peoesPretos + " (máx. 8)";
        for (char ch : grade[0])
            if (ch == 'P' || ch == 'p')
                return "peão na fileira de borda (topo)";
        for (char ch : grade[grade.length - 1])
            if (ch == 'P' || ch == 'p')
                return "peão na fileira de borda (base)";
        return null;
    }

    /**
     * Palpite de orientação por contagem de material — <b>último recurso</b>.
     *
     * <p>Só serve no bootstrap: é confiável na posição inicial, onde a margem é ±34, e vira
     * loteria conforme as peças somem. Com 7 peças a margem cai para 1 ponto, e o bônus do rei
     * chega a se INVERTER, porque quem está ganhando avança o rei para o campo adversário. Era
     * o uso disto no meio de jogo que fazia o robô "trocar de lado" sozinho num final.
     */
    public static Orientacao palpitePorMaterial(char[][] grade)
    {
        int nota = 0;
        for (int l = 0; l < 8; l++)
            for (int c = 0; c < 8; c++)
            {
                char ch = grade[l][c];
                if (ch == '.')
                    continue;
                int branca = Character.isUpperCase(ch) ? 1 : -1;
                int embaixo = l >= 4 ? 1 : -1;
                nota += branca * embaixo;
            }

        int linhaReiBranco = -1, linhaReiPreto = -1;
        for (int l = 0; l < 8; l++)
            for (int c = 0; c < 8; c++)
            {
                if (grade[l][c] == 'K') linhaReiBranco = l;
                if (grade[l][c] == 'k') linhaReiPreto = l;
            }
        if (linhaReiBranco >= 0 && linhaReiPreto >= 0)
            nota += 2 * (linhaReiBranco > linhaReiPreto ? 1 : -1);

        return nota >= 0 ? Orientacao.BRANCAS : Orientacao.PRETAS;
    }

    /** Só o campo de peças (1º campo) de uma FEN. */
    public static String placementDe(String fen)
    {
        int sp = fen.indexOf(' ');
        return sp < 0 ? fen : fen.substring(0, sp);
    }

    /** Expande uma fileira da FEN ("3n4") em 8 chars ("...n...."). */
    public static char[] expandeFileira(String fileira)
    {
        char[] saida = new char[8];
        int c = 0;
        for (int i = 0; i < fileira.length() && c < 8; i++)
        {
            char ch = fileira.charAt(i);
            if (Character.isDigit(ch))
            {
                int n = ch - '0';
                while (n-- > 0 && c < 8)
                    saida[c++] = '.';
            }
            else
                saida[c++] = ch;
        }
        while (c < 8)
            saida[c++] = '.';
        return saida;
    }

    /**
     * Direitos de roque deduzidos do placement: K/Q se o rei branco está em e1 e há torre em
     * h1/a1; k/q idem para as pretas. Sem isso, uma FEN com "KQkq" mas o rei fora de e1 é
     * ILEGAL e quebra o parser de qualquer motor.
     */
    public static String direitosDeRoque(String placement)
    {
        String[] fileiras = placement.split("/");
        if (fileiras.length != 8)
            return "-";
        char[] primeira = expandeFileira(fileiras[7]);   // fileira 1 (brancas)
        char[] oitava = expandeFileira(fileiras[0]);     // fileira 8 (pretas)

        StringBuilder sb = new StringBuilder();
        if (primeira[4] == 'K')
        {
            if (primeira[7] == 'R') sb.append('K');
            if (primeira[0] == 'R') sb.append('Q');
        }
        if (oitava[4] == 'k')
        {
            if (oitava[7] == 'r') sb.append('k');
            if (oitava[0] == 'r') sb.append('q');
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }
}
