package chesscheater.partida;

import java.util.ArrayList;
import java.util.List;

import chessai.core.Board;
import chessai.core.MoveGen;
import chesscheater.visao.Grade;

/**
 * O estado autoritativo da partida — e o árbitro que valida tudo que vem da tela.
 *
 * <p>A tela é uma fonte ruidosa: animação, popup, realce e frame ruim produzem leituras
 * impossíveis. Esta classe é o contrapeso — mantém a posição de verdade, gera lances legais e
 * só aceita da tela o que for alcançável por um lance legal a partir do que ela já sabe.
 *
 * <p>Usa o {@code chessai.core.Board} de dentro do <b>jchessai.jar</b>: as mesmas regras que a
 * rede usa para pensar. Não há segunda implementação de xadrez neste projeto, e é de propósito
 * — duas implementações divergem, e a divergência apareceria como "lance ilegal" no meio de uma
 * partida contra o bot.
 *
 * <h2>A lista de lances é o ativo mais valioso daqui</h2>
 * Manter a sequência desde a posição inicial é o que permite mandar
 * {@code position startpos moves ...} ao motor e entregar à rede o histórico de 4 frames REAL.
 * Quando o histórico se perde (posição reconstruída da tela pelo botão "Jogar"),
 * {@link #temHistorico()} passa a ser {@code false} e o motor é consultado pela FEN — o que
 * funciona, mas alimenta a rede com três frames zerados.
 */
public final class Partida
{
    private Board tabuleiro;
    private final List<String> lances = new ArrayList<>();
    private boolean comHistorico;
    private boolean iniciada;
    private boolean nossaVez;

    public Partida()
    {
        reinicia();
    }

    /** Zera tudo: nenhuma posição, nenhum lance. */
    public void reinicia()
    {
        tabuleiro = null;
        lances.clear();
        comHistorico = false;
        iniciada = false;
        nossaVez = false;
    }

    public boolean iniciada()      { return iniciada; }
    public boolean temHistorico()  { return comHistorico; }
    public boolean nossaVez()      { return nossaVez; }
    public void defineNossaVez(boolean v) { nossaVez = v; }
    public List<String> lances()   { return List.copyOf(lances); }
    public String fen()            { return tabuleiro == null ? null : tabuleiro.fen(); }

    /** O placement (1º campo da FEN) da posição interna. */
    public String placement()
    {
        return tabuleiro == null ? null : Grade.placementDe(tabuleiro.fen());
    }

    /**
     * Começa a partida da posição inicial padrão, <b>sempre</b> — mesmo que a tela já mostre
     * outra coisa.
     *
     * <p>É deliberado: se a captura começar depois de o adversário já ter jogado (típico
     * quando jogamos de pretas), o chamador reconhece a diferença e casa o lance já aplicado
     * via {@link #lanceQueProduz}. Gravar direto o que está na tela produziria um estado
     * incoerente — peão em e4 mas "brancas a jogar".
     *
     * @param somosBrancas se jogamos as brancas (então a primeira vez é nossa)
     */
    public void comecaDoInicio(boolean somosBrancas)
    {
        tabuleiro = Board.deFEN(Board.FEN_INICIAL);
        lances.clear();
        comHistorico = true;
        iniciada = true;
        nossaVez = somosBrancas;
    }

    /**
     * Reconstrói a posição a partir de um placement lido da tela, com o lado a mover dado.
     * <b>O histórico se perde</b> — é o preço do resgate.
     *
     * <p>Direitos de roque são deduzidos do próprio placement (rei e torre nas casas iniciais)
     * e en passant vira "-". Aproximação aceitável: o motor ainda devolve um lance legal sob
     * essa suposição.
     *
     * @return {@code false} se a FEN montada não for aceita pelas regras
     */
    public boolean reconstroiDe(String placement, boolean somosBrancas)
    {
        String fen = placement + " " + (somosBrancas ? "w" : "b") + " "
                   + Grade.direitosDeRoque(placement) + " - 0 1";
        Board novo;
        try
        {
            novo = Board.deFEN(fen);
        }
        catch (RuntimeException fenInvalida)
        {
            return false;
        }
        tabuleiro = novo;
        lances.clear();
        comHistorico = false;
        iniciada = true;
        nossaVez = true;
        return true;
    }

    /** Aplica um lance na linha principal, avançando o estado autoritativo. */
    public void aplica(String uci)
    {
        int lance = tabuleiro.lanceDeUci(uci);
        if (lance == 0)
            throw new IllegalArgumentException("lance ilegal na posição interna: " + uci);
        tabuleiro.aplicaLance(lance);
        lances.add(uci);
    }

    /** O lance é legal na posição atual? */
    public boolean eLegal(String uci)
    {
        return tabuleiro != null && tabuleiro.lanceDeUci(uci) != 0;
    }

    /**
     * O lance legal que produz exatamente {@code placementAlvo}, ou {@code null} se nenhum
     * produz. É assim que o lance do bot é descoberto: pela diferença entre a tela e o estado
     * interno.
     */
    public String lanceQueProduz(String placementAlvo)
    {
        if (tabuleiro == null)
            return null;
        MoveGen.Lista legais = tabuleiro.lancesLegais();
        for (int i = 0; i < legais.tamanho; i++)
        {
            int lance = legais.get(i);
            Board copia = tabuleiro.copia();
            copia.aplicaLance(lance);
            if (Grade.placementDe(copia.fen()).equals(placementAlvo))
                return tabuleiro.uciDe(lance);
        }
        return null;
    }

    /** O placement resultante de aplicar {@code uci}, sem alterar o estado. */
    public String placementDepoisDe(String uci)
    {
        int lance = tabuleiro.lanceDeUci(uci);
        if (lance == 0)
            return null;
        Board copia = tabuleiro.copia();
        copia.aplicaLance(lance);
        return Grade.placementDe(copia.fen());
    }

    /**
     * O placement lido é alcançável aplicando o NOSSO lance e, em seguida, um lance legal do
     * adversário?
     *
     * <p>Serve ao caso em que o nosso lance e a resposta do bot aparecem na mesma varredura.
     * Sem esta checagem, o estado interno avançaria por engano toda vez que a leitura pulasse
     * um ply.
     */
    public boolean alcancavelDepoisDoNosso(String nossoUci, String placementAlvo)
    {
        int nosso = tabuleiro.lanceDeUci(nossoUci);
        if (nosso == 0)
            return false;

        Board depoisDoNosso = tabuleiro.copia();
        depoisDoNosso.aplicaLance(nosso);

        MoveGen.Lista legais = depoisDoNosso.lancesLegais();
        for (int i = 0; i < legais.tamanho; i++)
        {
            Board copia = depoisDoNosso.copia();
            copia.aplicaLance(legais.get(i));
            if (Grade.placementDe(copia.fen()).equals(placementAlvo))
                return true;
        }
        return false;
    }

    /**
     * O placement lido é coerente com a partida em andamento? Aceita as mesmas transições que
     * o laço de jogo considera válidas: a própria posição interna, o efeito de um lance nosso
     * pendente, um lance do adversário, ou o nosso lance somado à resposta dele.
     *
     * <p>É a base do degrau mais forte de decisão de orientação: se exatamente UMA das duas
     * orientações é coerente com a partida, ela decide — sem depender de contagem de material.
     */
    public boolean coerenteCom(String placement, String uciPendente)
    {
        if (!iniciada || tabuleiro == null)
            return false;
        if (placement.equals(placement()))
            return true;
        if (uciPendente != null)
        {
            String esperado = placementDepoisDe(uciPendente);
            if (placement.equals(esperado))
                return true;
            if (alcancavelDepoisDoNosso(uciPendente, placement))
                return true;
        }
        return lanceQueProduz(placement) != null;
    }

    /** Fim de jogo pelas regras (mate, afogamento, material insuficiente, 50 lances). */
    public boolean fimDeJogo()
    {
        return tabuleiro != null && tabuleiro.fimDeJogo();
    }

    public String resultadoTexto()
    {
        return tabuleiro == null ? "" : tabuleiro.resultadoTexto();
    }

    /**
     * Fim de jogo de baixo material: só reis e peões, ou menos de 6 peças no total. O robô
     * joga mais rápido nesses casos — ficar "pensando" 3 segundos por lance num final de rei e
     * peão é o comportamento que menos parece humano.
     */
    public boolean finalDeBaixoMaterial()
    {
        String placement = placement();
        if (placement == null)
            return false;
        int total = 0;
        boolean soReisEPeoes = true;
        for (int i = 0; i < placement.length(); i++)
        {
            char ch = placement.charAt(i);
            if (ch == '/' || Character.isDigit(ch))
                continue;
            total++;
            char minusculo = Character.toLowerCase(ch);
            if (minusculo != 'k' && minusculo != 'p')
                soReisEPeoes = false;
        }
        return soReisEPeoes || total < 6;
    }
}
