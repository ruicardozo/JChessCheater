package chesscheater.visao;

/**
 * De que lado o tabuleiro está desenhado — isto é, quais peças estão EMBAIXO na tela.
 *
 * <p>O robô joga sempre o lado de baixo, então a orientação decide o mapeamento tela↔casa e,
 * junto com ele, de que cor jogamos. Errar isto é o defeito mais caro da máquina de visão:
 * a partida continua "funcionando", mas com o tabuleiro girado 180 graus.
 */
public enum Orientacao
{
    BRANCAS("brancas"),
    PRETAS("pretas");

    private final String rotulo;

    Orientacao(String rotulo)
    {
        this.rotulo = rotulo;
    }

    public Orientacao oposta()
    {
        return this == BRANCAS ? PRETAS : BRANCAS;
    }

    @Override
    public String toString()
    {
        return rotulo;
    }
}
