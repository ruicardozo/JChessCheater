package chesscheater.config;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * O <b>JSON mestre</b> do pacote: diz qual iteração da rede usar, e com que ajustes começar.
 *
 * <p>É o que torna a pasta um pacote fechado. Tudo mora junto — os dois JARs, este arquivo e
 * (opcionalmente) os pesos — e trocar de iteração é <b>editar uma linha</b> e reabrir:
 *
 * <pre>
 * jchesscheater.jar      ← o programa
 * jchessai.jar           ← o motor e as regras
 * jchesscheater.json     ← este arquivo
 * iter_0080.pt           ← os pesos (podem estar em outro lugar; ver "pesos")
 * </pre>
 *
 * <h2>O arquivo</h2>
 * <pre>
 * {
 *   "pesos": "iter_0080.pt",
 *   "motor": "jchessai.jar",
 *   "sims": 200,
 *   "ambiente": "auto",
 *   "intervaloMs": 1000,
 *   "latenciaMs": 2000,
 *   "autoJogo": true
 * }
 * </pre>
 *
 * <p>Só {@code pesos} importa de verdade; o resto tem padrão e só serve para a janela já abrir
 * do jeito que se quer. <b>Caminhos relativos resolvem contra a pasta do JAR</b>, não contra o
 * diretório de onde se chamou o programa — é isso que faz o pacote funcionar clicado, chamado
 * de outra pasta ou por atalho.
 *
 * <p>Sem o arquivo, o programa roda assim mesmo, com os padrões e procurando
 * {@code iter_0080.pt} ao lado do JAR. O JSON existe para trocar de iteração sem recompilar
 * nada — que é exatamente o ponto de ter um pacote.
 */
public final class Configuracao
{
    /** Nome padrão do arquivo, procurado na pasta do JAR. */
    public static final String NOME_PADRAO = "jchesscheater.json";

    private final Path pastaBase;
    private final Path arquivo;         // null se não existe
    private final Map<String, Object> valores;

    private Configuracao(Path pastaBase, Path arquivo, Map<String, Object> valores)
    {
        this.pastaBase = pastaBase;
        this.arquivo = arquivo;
        this.valores = valores;
    }

    /**
     * Carrega o JSON mestre.
     *
     * <p>Procura, nesta ordem: o caminho de {@code -Djchesscheater.config}, o
     * {@value #NOME_PADRAO} na pasta do JAR e o {@value #NOME_PADRAO} no diretório atual. Se
     * nenhum existir, devolve a configuração de padrões — não é erro.
     *
     * @throws IOException se o arquivo existe mas não pôde ser lido ou não é um JSON válido
     */
    public static Configuracao carrega() throws IOException
    {
        Path base = pastaDoJar();

        Path candidato = null;
        String daPropriedade = System.getProperty("jchesscheater.config");
        if (daPropriedade != null && !daPropriedade.isBlank())
            candidato = Paths.get(daPropriedade);
        else
        {
            Path naPastaDoJar = base.resolve(NOME_PADRAO);
            Path noDiretorioAtual = Paths.get(NOME_PADRAO);
            if (Files.isRegularFile(naPastaDoJar))
                candidato = naPastaDoJar;
            else if (Files.isRegularFile(noDiretorioAtual))
                candidato = noDiretorioAtual;
        }

        if (candidato == null || !Files.isRegularFile(candidato))
            return new Configuracao(base, null, Map.of());

        String texto = Files.readString(candidato, StandardCharsets.UTF_8);
        Object raiz;
        try
        {
            raiz = Json.interpreta(texto);
        }
        catch (RuntimeException invalido)
        {
            throw new IOException(candidato.toAbsolutePath() + "\n" + invalido.getMessage());
        }
        if (!(raiz instanceof Map))
            throw new IOException(candidato.toAbsolutePath()
                + "\nO conteúdo tem que ser um objeto JSON — algo entre { e }.");

        @SuppressWarnings("unchecked")
        Map<String, Object> mapa = (Map<String, Object>) raiz;
        // O JSON fica ao lado do que ele descreve: caminhos relativos resolvem contra a pasta
        // DELE, não contra a do JAR, para o -Djchesscheater.config apontar um pacote inteiro.
        Path pasta = candidato.toAbsolutePath().getParent();
        return new Configuracao(pasta == null ? base : pasta, candidato, mapa);
    }

    /**
     * A pasta onde o JAR está — a âncora do pacote.
     *
     * <p>Quando se roda de dentro do Eclipse ou com {@code -cp bin}, não há JAR; aí cai no
     * diretório atual, que é o comportamento útil em desenvolvimento.
     */
    public static Path pastaDoJar()
    {
        try
        {
            URI uri = Configuracao.class.getProtectionDomain()
                                        .getCodeSource().getLocation().toURI();
            Path local = Paths.get(uri);
            Path pasta = Files.isDirectory(local) ? local : local.getParent();
            if (pasta != null)
                return pasta.toAbsolutePath().normalize();
        }
        catch (Exception semCodeSource)
        {
            // segue para o diretório atual
        }
        return Paths.get("").toAbsolutePath();
    }

    /** O arquivo efetivamente lido, ou {@code null} se estamos só com os padrões. */
    public Path arquivo() { return arquivo; }

    /** A pasta contra a qual os caminhos relativos são resolvidos. */
    public Path pastaBase() { return pastaBase; }

    // ── Os campos ────────────────────────────────────────────────────────────

    /**
     * Caminho do arquivo de pesos — <b>a iteração da rede</b>, que é o motivo de este arquivo
     * existir.
     */
    public Path pesos()
    {
        return descobre("jchesscheater.pesos", texto("pesos", "iter_0080.pt"),
                        "../JChessAI/weights/iter_0080.pt",
                        "../../JChessAI/weights/iter_0080.pt");
    }

    /** Caminho do {@code jchessai.jar}. */
    public Path motor()
    {
        return descobre("jchesscheater.jar", texto("motor", "jchessai.jar"),
                        "lib/jchessai.jar",
                        "../JChessAI/uci/build/libs/jchessai.jar",
                        "../../JChessAI/uci/build/libs/jchessai.jar");
    }

    /**
     * Resolve um caminho com três fontes, nesta ordem: a propriedade de sistema (o override de
     * emergência), o que o JSON pede, e por fim os lugares alternativos — que servem ao
     * desenvolvimento, quando se roda de {@code bin/} com o repositório do JChessAI ao lado.
     *
     * <p>Não encontrando nada, devolve o que o JSON pede: é o caminho que vai aparecer na
     * mensagem de erro, e é o certo para o usuário conferir.
     */
    private Path descobre(String propriedade, String doJson, String... alternativos)
    {
        String daPropriedade = System.getProperty(propriedade);
        if (daPropriedade != null && !daPropriedade.isBlank())
            return Paths.get(daPropriedade);

        Path pedido = resolve(doJson);
        if (Files.isRegularFile(pedido))
            return pedido;

        for (String alternativo : alternativos)
        {
            Path p = resolve(alternativo);
            if (Files.isRegularFile(p))
                return p;
        }
        return pedido;
    }

    /** Simulações por lance com que a janela abre. */
    public int sims() { return inteiro("sims", 200); }

    public int intervaloMs() { return inteiro("intervaloMs", 1000); }

    public int latenciaMs() { return inteiro("latenciaMs", 2000); }

    public boolean autoJogo() { return booleano("autoJogo", true); }

    /** Perfil de tela pedido: "auto", "windows", "ubuntu" ou "generico". */
    public String ambiente() { return texto("ambiente", "auto"); }

    /** Descrição de uma linha para o log e para a barra de estado. */
    public String resumo()
    {
        return (arquivo == null ? "sem JSON (padrões)" : arquivo.getFileName().toString())
            + " · pesos: " + pesos().getFileName()
            + " · " + sims() + " simulações";
    }

    // ── Apoio ────────────────────────────────────────────────────────────────
    private Path resolve(String caminho)
    {
        Path p = Paths.get(caminho);
        return p.isAbsolute() ? p.normalize() : pastaBase.resolve(p).normalize();
    }

    private String texto(String chave, String padrao)
    {
        Object v = valores.get(chave);
        return v instanceof String && !((String) v).isBlank() ? (String) v : padrao;
    }

    private int inteiro(String chave, int padrao)
    {
        Object v = valores.get(chave);
        if (v instanceof Number)
            return ((Number) v).intValue();
        if (v instanceof String)
            try
            {
                return Integer.parseInt(((String) v).trim());
            }
            catch (NumberFormatException naoENumero)
            {
                return padrao;
            }
        return padrao;
    }

    private boolean booleano(String chave, boolean padrao)
    {
        Object v = valores.get(chave);
        if (v instanceof Boolean)
            return (Boolean) v;
        if (v instanceof String)
            return Boolean.parseBoolean(((String) v).trim());
        return padrao;
    }
}
