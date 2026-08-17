package chesscheater;

import java.lang.reflect.Method;

/**
 * O ponto de entrada do JAR — e a única classe do projeto compilada para <b>Java 8</b>.
 *
 * <p>Existe por um motivo só: transformar um erro ilegível num recado. O resto do programa é
 * <i>class file</i> 65 (Java 21), e uma JVM mais antiga recusa a classe <b>antes</b> de
 * executar qualquer linha nossa — o usuário vê um {@code UnsupportedClassVersionError} com um
 * número mágico e nenhuma pista do que fazer. Como esta classe carrega em qualquer JVM desde a
 * 8, ela consegue checar a versão e dizer, em português, o que está faltando.
 *
 * <p>Por isso ela é modesta de propósito: sem recursos de linguagem modernos, sem depender de
 * nenhuma outra classe nossa em tempo de carga. A chamada ao programa de verdade é por
 * <b>reflexão</b>, para que a classe de 21 só seja carregada depois de a versão ter sido
 * aprovada.
 */
public final class Iniciar
{
    /** Versão mínima da JVM. É a do {@code jchessai.jar}, que este programa também carrega. */
    private static final int MINIMA = 21;

    private Iniciar() { }

    public static void main(String[] args) throws Exception
    {
        int versao = versaoDaJvm();
        if (versao > 0 && versao < MINIMA)
        {
            reclama(versao);
            System.exit(3);
        }

        // Reflexão: só agora a classe de 21 é carregada, com a versão já aprovada.
        Class<?> programa = Class.forName("chesscheater.JChessCheater");
        Method principal = programa.getMethod("main", String[].class);
        principal.invoke(null, (Object) args);
    }

    /** Versão mínima exigida, exposta para o diagnóstico. */
    public static int minima()
    {
        return MINIMA;
    }

    /** A versão da JVM, ou 0 se não deu para descobrir (aí seguimos e deixamos tentar). */
    static int versaoDaJvm()
    {
        return versaoDe(System.getProperty("java.specification.version", ""));
    }

    /**
     * Interpreta a string de versão da especificação Java.
     *
     * <p>Separada de {@link #versaoDaJvm()} para poder ser testada: a JVM não deixa
     * sobrescrever {@code java.specification.version} pela linha de comando, então esta é a
     * única forma de exercitar o caso que importa — o do Java antigo — sem instalar um.
     *
     * <p>Formatos: {@code "1.8"} (Java 8 e anteriores), {@code "17"}, {@code "21"},
     * e por segurança também {@code "21.0.8"}. Qualquer outra coisa devolve 0, e nesse caso
     * o programa segue em frente: recusar por não entender a versão seria pior que tentar.
     */
    public static int versaoDe(String texto)
    {
        if (texto == null)
            return 0;
        if (texto.startsWith("1."))                    // "1.8" → 8
            texto = texto.substring(2);
        int fim = 0;
        while (fim < texto.length() && Character.isDigit(texto.charAt(fim)))
            fim++;
        if (fim == 0)
            return 0;
        try
        {
            return Integer.parseInt(texto.substring(0, fim));
        }
        catch (NumberFormatException naoENumero)
        {
            return 0;
        }
    }

    private static void reclama(int versao)
    {
        String mensagem =
            "Este programa precisa do Java " + MINIMA + " ou mais novo.\n\n"
            + "A JVM que o executou é a versão " + versao + ", em:\n"
            + System.getProperty("java.home") + "\n\n"
            + "No Windows, é comum ter um Java mais antigo no PATH e um 21 instalado à parte.\n"
            + "Rode pelo JChessCheater.bat, que procura um 21, ou chame o java do 21 direto:\n\n"
            + "    C:\\caminho\\do\\jdk-21\\bin\\java.exe -jar jchesscheater.jar";

        System.err.println(mensagem);
        try
        {
            if (!java.awt.GraphicsEnvironment.isHeadless())
                javax.swing.JOptionPane.showMessageDialog(null, mensagem,
                    "JChessCheater — Java antigo demais",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
        catch (Throwable semInterfaceGrafica)
        {
            // a mensagem no console já saiu; não há mais o que fazer
        }
    }
}
