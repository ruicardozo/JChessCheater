package chesscheater.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor de JSON mínimo — o suficiente para um arquivo de configuração, e nada além.
 *
 * <p>Existe para não trazer biblioteca nenhuma: o JChessCheater tem exatamente uma
 * dependência, o {@code jchessai.jar}, e vale a pena manter assim. Um arquivo de configuração
 * de dez linhas não justifica um Gson no pacote.
 *
 * <p>Cobre o JSON de verdade — objetos, vetores, strings com escapes (inclusive {@code \\uXXXX}),
 * números, {@code true}/{@code false}/{@code null} — e recusa o resto com a <b>posição do erro</b>,
 * que é o que importa quando alguém edita o arquivo à mão e esquece uma vírgula.
 *
 * <p>Tipos devolvidos: {@code Map<String,Object>}, {@code List<Object>}, {@code String},
 * {@code Double}, {@code Boolean}, {@code null}.
 */
public final class Json
{
    private final String texto;
    private int i;

    private Json(String texto)
    {
        this.texto = texto;
    }

    /** Interpreta um documento JSON inteiro. */
    public static Object interpreta(String texto)
    {
        Json j = new Json(texto);
        j.pulaBrancos();
        Object valor = j.valor();
        j.pulaBrancos();
        if (j.i < j.texto.length())
            throw j.erro("lixo depois do fim do documento");
        return valor;
    }

    private Object valor()
    {
        pulaBrancos();
        if (i >= texto.length())
            throw erro("documento vazio");

        char c = texto.charAt(i);
        switch (c)
        {
            case '{': return objeto();
            case '[': return vetor();
            case '"': return string();
            case 't': return literal("true", Boolean.TRUE);
            case 'f': return literal("false", Boolean.FALSE);
            case 'n': return literal("null", null);
            default:  return numero();
        }
    }

    private Map<String, Object> objeto()
    {
        Map<String, Object> mapa = new LinkedHashMap<>();
        consome('{');
        pulaBrancos();
        if (espia() == '}')
        {
            i++;
            return mapa;
        }
        while (true)
        {
            pulaBrancos();
            String chave = string();
            pulaBrancos();
            consome(':');
            mapa.put(chave, valor());
            pulaBrancos();
            char c = espia();
            if (c == ',')
            {
                i++;
                continue;
            }
            if (c == '}')
            {
                i++;
                return mapa;
            }
            throw erro("esperava ',' ou '}'");
        }
    }

    private List<Object> vetor()
    {
        List<Object> lista = new ArrayList<>();
        consome('[');
        pulaBrancos();
        if (espia() == ']')
        {
            i++;
            return lista;
        }
        while (true)
        {
            lista.add(valor());
            pulaBrancos();
            char c = espia();
            if (c == ',')
            {
                i++;
                continue;
            }
            if (c == ']')
            {
                i++;
                return lista;
            }
            throw erro("esperava ',' ou ']'");
        }
    }

    private String string()
    {
        consome('"');
        StringBuilder sb = new StringBuilder();
        while (true)
        {
            if (i >= texto.length())
                throw erro("string sem fecha-aspas");
            char c = texto.charAt(i++);
            if (c == '"')
                return sb.toString();
            if (c != '\\')
            {
                sb.append(c);
                continue;
            }
            if (i >= texto.length())
                throw erro("escape truncado");
            char e = texto.charAt(i++);
            switch (e)
            {
                case '"':  sb.append('"');  break;
                case '\\': sb.append('\\'); break;
                case '/':  sb.append('/');  break;
                case 'b':  sb.append('\b'); break;
                case 'f':  sb.append('\f'); break;
                case 'n':  sb.append('\n'); break;
                case 'r':  sb.append('\r'); break;
                case 't':  sb.append('\t'); break;
                case 'u':
                    if (i + 4 > texto.length())
                        throw erro("escape \\u truncado");
                    sb.append((char) Integer.parseInt(texto.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default:
                    throw erro("escape desconhecido: \\" + e);
            }
        }
    }

    private Double numero()
    {
        int inicio = i;
        if (espia() == '-' || espia() == '+')
            i++;
        while (i < texto.length())
        {
            char c = texto.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E'
                || c == '+' || c == '-')
                i++;
            else
                break;
        }
        if (inicio == i)
            throw erro("valor não reconhecido");
        try
        {
            return Double.valueOf(texto.substring(inicio, i));
        }
        catch (NumberFormatException naoENumero)
        {
            throw erro("número inválido: " + texto.substring(inicio, i));
        }
    }

    private Object literal(String esperado, Object valor)
    {
        if (!texto.startsWith(esperado, i))
            throw erro("esperava " + esperado);
        i += esperado.length();
        return valor;
    }

    private char espia()
    {
        if (i >= texto.length())
            throw erro("fim inesperado");
        return texto.charAt(i);
    }

    private void consome(char c)
    {
        if (espia() != c)
            throw erro("esperava '" + c + "'");
        i++;
    }

    private void pulaBrancos()
    {
        while (i < texto.length() && Character.isWhitespace(texto.charAt(i)))
            i++;
    }

    /** Erro com linha e coluna: quem editou o arquivo à mão precisa saber ONDE errou. */
    private IllegalArgumentException erro(String mensagem)
    {
        int linha = 1, coluna = 1;
        for (int k = 0; k < Math.min(i, texto.length()); k++)
        {
            if (texto.charAt(k) == '\n')
            {
                linha++;
                coluna = 1;
            }
            else
                coluna++;
        }
        return new IllegalArgumentException(
            "JSON inválido na linha " + linha + ", coluna " + coluna + ": " + mensagem);
    }
}
