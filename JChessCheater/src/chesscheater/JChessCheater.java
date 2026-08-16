package chesscheater;

import java.awt.GraphicsEnvironment;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import chesscheater.ui.FormPrincipal;
import chesscheater.visao.Visao;

/**
 * JChessCheater — a rede v16a jogando contra os bots do chess.com.
 *
 * <p>Para que serve: <b>medir a nossa rede treinada contra adversários de força conhecida</b>.
 * Os bots do chess.com têm rating publicado, jogam sempre, não cansam e não reclamam — é o
 * banco de provas mais barato que existe para responder "quanto vale a iteração 80?". O que o
 * programa faz é ler o tabuleiro da tela, perguntar o lance ao nosso motor e clicar.
 *
 * <p>Ele conhece exatamente <b>uma</b> dependência: o {@code jchessai.jar}. Dele vêm as duas
 * coisas de que precisa — as regras de xadrez ({@code chessai.core.Board}, usado como árbitro)
 * e o motor em si, consultado por UCI num subprocesso. Não há Stockfish, não há Python, não há
 * C++, e não há uma segunda implementação de xadrez neste repositório.
 *
 * <h2>Como rodar</h2>
 * <pre>
 * java -cp bin:lib/jchessai.jar chesscheater.JChessCheater
 * </pre>
 * O JAR e os pesos são procurados em {@code lib/} e {@code weights/}, com queda para o
 * repositório vizinho do JChessAI. Para apontar caminhos explícitos:
 * <pre>
 * -Djchesscheater.jar=/caminho/jchessai.jar  -Djchesscheater.pesos=/caminho/iter_0080.pt
 * </pre>
 *
 * <p><b>Exige Java 21</b>, como o próprio {@code jchessai.jar} (class file 65).
 */
public final class JChessCheater
{
    private JChessCheater() { }

    public static void main(String[] args)
    {
        if (GraphicsEnvironment.isHeadless())
        {
            System.err.println("Ambiente headless: este programa precisa de tela "
                + "para capturar e de mouse para clicar.");
            System.exit(2);
        }

        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception aparenciaPadrao)
        {
            // segue com o Look-and-Feel padrão do Swing
        }

        final Visao visao;
        try
        {
            visao = new Visao();
        }
        catch (Exception falha)
        {
            JOptionPane.showMessageDialog(null,
                "Falha ao carregar os moldes das peças:\n" + falha.getMessage(),
                "JChessCheater", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }

        SwingUtilities.invokeLater(() -> new FormPrincipal(visao).mostra());
    }
}
