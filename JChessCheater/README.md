# JChessCheater

A rede **v16a** do JChessAI jogando contra os **bots do chess.com**, pela tela.

O programa lê o tabuleiro da imagem do navegador, pergunta o lance ao nosso motor e clica com o
mouse. Serve para uma coisa: **medir a nossa rede contra adversários de força conhecida**. Os
bots do chess.com têm rating publicado, jogam a qualquer hora e não cansam — é o banco de
provas mais barato que existe para responder *quanto vale a iteração 80*.

Ele conhece exatamente **uma** dependência: o `jchessai.jar`. Dele vêm as duas coisas de que
precisa — as regras de xadrez (`chessai.core.Board`, usado como árbitro) e o motor, consultado
por UCI num subprocesso. Sem Stockfish, sem Python, sem C++, e sem uma segunda implementação de
xadrez neste repositório.

---

## 1. O que é preciso ter

| | |
|---|---|
| **Java 21** | o `jchessai.jar` é *class file* 65; uma JVM 17 recusa |
| **`lib/jchessai.jar`** | gerado no JChessAI com `./gradlew fatJar` |
| **`weights/iter_0080.pt`** | 65 MB, não versionado |
| **Monitor Full HD** | 1920x1080 — as regiões de captura foram medidas nessa resolução |

```bash
mkdir -p lib weights
cp ../JChessAI/uci/build/libs/jchessai.jar lib/
cp ../JChessAI/weights/iter_0080.pt weights/
```

Nenhum dos dois é versionado. O JAR fica de fora **de propósito**: uma cópia commitada
envelheceria em silêncio, e "o motor está desatualizado" é o tipo de defeito que só aparece no
meio de uma partida.

Se preferir não copiar, aponte os caminhos:

```
-Djchesscheater.jar=/caminho/jchessai.jar
-Djchesscheater.pesos=/caminho/iter_0080.pt
```

Sem isso, o programa procura em `lib/` e `weights/` e depois no repositório vizinho do JChessAI.

---

## 2. Compilar e rodar

```bash
./run.sh              # compila se preciso e abre a janela
```

Ou à mão:

```bash
javac -encoding UTF-8 -d bin -cp lib/jchessai.jar $(find src -name '*.java')
java -cp bin:lib/jchessai.jar chesscheater.JChessCheater
```

No Eclipse, o `.classpath` já inclui `lib/jchessai.jar` — basta ter o arquivo lá.

### Diagnóstico

```bash
java -cp bin:lib/jchessai.jar chesscheater.teste.TesteDeFumaca
```

Roda sem navegador e sem monitor — **41 verificações**. Confere os moldes, a geometria do
tabuleiro, a **cadeia de visão inteira** (pinta uma posição e lê de volta, nas duas
orientações), as regras, o árbitro da partida e **o motor de verdade** — sobe o JAR, pede lance
com e sem histórico e verifica que o protocolo não dessincroniza. É o primeiro comando a rodar
numa máquina nova.

A leitura de peças usa os próprios moldes como tinta: pega transposição de linha/coluna, erro
de amostragem e FEN montada ao contrário, mas **não** diz nada sobre o desenho real do
chess.com — isso só a tela diz.

---

## 3. A janela

| Campo | O que faz |
|---|---|
| **Monitor** | em qual tela está o navegador (avisa se não for 1920x1080) |
| **Ambiente** | perfil de captura: Windows, Ubuntu ou genérico. Já vem no detectado |
| **Intervalo (ms)** | de quanto em quanto tempo a tela é lida |
| **Latência (ms)** | teto do tempo gasto **movendo o mouse** até a casa — não é tempo de pensar |
| **Nível (simulações)** | **a força da rede**: simulações do MCTS por lance |
| **Jogar como** | a cor que jogamos (ajustada sozinha ao detectar a posição inicial) |
| **Auto-jogo** | desligado, o programa só lê e mostra a posição, sem clicar |

**Iniciar** começa o laço · **Parar** interrompe · **Jogar** força uma jogada quando o robô
perde o fio · **Fechar** encerra e mata o subprocesso do motor.

### Sobre o "Nível"

O combo escolhe **simulações por lance**, que é o botão de força do MCTS:

| Simulações | 10 | 200 | 800 | 1400 |
|---|---|---|---|---|
| Tempo por lance | ~0,15 s | ~1,2 s | ~5 s | ~9 s |

Números medidos numa máquina de 6 núcleos. **200** é o padrão e foi o valor usado no torneio de
validação do JChessAI. Subir aumenta a força de verdade — a busca é o multiplicador da rede —,
mas cada lance demora proporcionalmente mais.

---

## 4. O compartimento da imagem

Está tudo em **`src/chesscheater/visao/PerfilDeTela.java`**, e é o arquivo a abrir quando o
chess.com mudar o desenho.

Lá dentro moram, e só lá:

- a **região de captura** de cada ambiente (Windows / Ubuntu / genérico);
- os **limiares de cor** que dizem o que é casa clara, casa escura e realce amarelo;
- o **recorte do rótulo** de coordenada (o dígito da fileira) e seus limiares;
- as constantes de cobertura, de casa vazia e da faixa de tamanho de casa.

Cada perfil carrega os **seus próprios** limiares. Se o site mudar no Windows e não no Ubuntu,
ajusta-se `WINDOWS` sem tocar em `UBUNTU` — era exatamente para isso que os limiares deixaram
de ser constantes globais.

Nenhuma outra classe do projeto contém número medido em tela.

> **Quando o site mudar**: tire capturas novas nos dois sistemas, meça as cores das casas e a
> caixa do tabuleiro, e ajuste o perfil correspondente. O `TesteDeFumaca` **não** pega esse tipo
> de regressão — ele valida a geometria contra um tabuleiro sintético, não contra o desenho
> real. Quem valida isso é a tela.

### Os moldes das peças

`MoldesEmbutidos.java` traz os doze moldes 64x64 em Base64, copiados byte a byte do FenScanner
do JStockfish11 — calibrados contra o tema padrão do chess.com. É dado, não lógica: nada ali
precisa ser entendido para mexer no resto.

---

## 5. Mapa do código

| Pacote | O que faz |
|---|---|
| `visao` | captura → geometria → grade → placement. **`Visao` é a fachada**: quem chama pede uma leitura e recebe geometria + grade, ou `null` se a tela não permitiu ler |
| `partida` | `Partida` — o estado autoritativo, sobre `chessai.core.Board`. Descobre o lance do bot pela diferença entre a tela e o estado interno |
| `motor` | `MotorUci` — o subprocesso do `jchessai.jar` |
| `robo` | `Clicador` — o mouse humanizado |
| `ui` | `FormPrincipal` — a janela e a máquina de estados da partida |

### Duas decisões que valem saber

**A partida inteira vai para o motor, não a FEN.** A rede v16a recebe **4 frames** — a posição
atual e as três anteriores. `position startpos moves e2e4 e7e5 ...` entrega o histórico real;
mandar só a FEN deixa os três frames anteriores zerados, que é outra entrada e dá outro lance
(medido no JChessAI: difere em 9 de 20 posições). Por isso a lista de lances desde o início é
mantida a partida toda. O botão "Jogar" é a exceção: ele reconstrói a posição da tela, o
histórico se perde, e aí a consulta é pela FEN mesmo.

**O lance fica pendente até a tela confirmar.** Ao jogar, o estado interno não avança: a
próxima varredura verifica se a peça realmente se moveu. Se não moveu — o clique caiu numa
prévia, e isso acontece —, o mesmo lance é re-clicado. É o que impede o robô de achar que jogou
quando não jogou.

---

## 6. Quando algo dá errado

| Sintoma | Causa provável |
|---|---|
| `jchessai.jar não encontrado` | falta copiar para `lib/` (§1) |
| `pesos não encontrados` | falta o `iter_0080.pt` em `weights/` |
| `UnsupportedClassVersionError` | JVM 17; o JAR exige **Java 21** |
| "Tabuleiro não encontrado" | região errada no combo Ambiente, navegador não maximizado, ou monitor que não é Full HD |
| "Posição não reconhecida" | o robô perdeu o fio (popup, animação). Ajuste a cor e clique **Jogar** |
| Peças lidas erradas | o desenho do site mudou → `PerfilDeTela` (§4) |
| O robô joga girado 180° | orientação travada errada. Clique **Jogar** com a cor certa no combo |
| O motor demora demais | baixe o Nível (simulações) |

---

## 7. Para que serve, e para que não serve

Serve para **testar a nossa rede contra bots**, que é o que o projeto AlphaZero precisa medir e
o que o chess.com oferece para isso. O nome é uma piada interna.

Não serve — e não deve ser usado — para partidas valendo rating contra pessoas.
