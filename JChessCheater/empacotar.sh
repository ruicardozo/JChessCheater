#!/usr/bin/env bash
# Monta o pacote distribuível em dist/ — uma pasta que se copia inteira para qualquer máquina:
#
#   dist/
#     jchesscheater.jar    o programa (Main-Class e Class-Path no manifesto)
#     jchessai.jar         o motor e as regras
#     jchesscheater.json   o JSON mestre: qual iteração usar
#     LEIAME.txt           o essencial, para quem abrir a pasta daqui a um ano
#     iter_0080.pt         os pesos, se existirem por perto (opcional)
#
# Rodar lá: java -jar jchesscheater.jar
set -euo pipefail
cd "$(dirname "$0")"

JAVAC_BIN="${JAVA21_HOME:+$JAVA21_HOME/bin/}javac"
JAR_BIN="${JAVA21_HOME:+$JAVA21_HOME/bin/}jar"

MOTOR="${1:-lib/jchessai.jar}"
if [ ! -f "$MOTOR" ]; then
  echo "motor não encontrado: $MOTOR" >&2
  echo "gere no JChessAI (./gradlew fatJar) e copie para lib/, ou passe o caminho:" >&2
  echo "  ./empacotar.sh /caminho/jchessai.jar" >&2
  exit 1
fi

echo "compilando..."
rm -rf build/classes dist
mkdir -p build/classes dist
# O lançador PRIMEIRO, e para Java 8 de propósito: é ele que consegue carregar numa JVM
# antiga e dizer "precisa do Java 21" em vez de deixar estourar UnsupportedClassVersionError.
# Primeiro porque o diagnóstico testa a leitura de versão dele.
"$JAVAC_BIN" -encoding UTF-8 --release 8 -nowarn -d build/classes \
             $(find src-launcher -name '*.java')

"$JAVAC_BIN" -encoding UTF-8 -d build/classes -cp "$MOTOR:build/classes" \
             $(find src -name '*.java')

echo "empacotando..."
cat > build/manifesto.txt <<MANIFESTO
Main-Class: chesscheater.Iniciar
Class-Path: jchessai.jar
Implementation-Title: JChessCheater
Implementation-Version: 1.0.0
MANIFESTO

"$JAR_BIN" --create --file dist/jchesscheater.jar --manifest build/manifesto.txt \
           -C build/classes .
cp "$MOTOR" dist/jchessai.jar
cp pacote/jchesscheater.json dist/
cp pacote/LEIAME.txt dist/
cp pacote/JChessCheater.bat dist/

# Os pesos são opcionais no pacote: 65 MB nem sempre se quer carregar junto. Se houver um por
# perto, entra — é o que faz a pasta funcionar sem mais nada.
for candidato in weights/iter_0080.pt \
                 ../JChessAI/weights/iter_0080.pt \
                 ../../JChessAI/weights/iter_0080.pt; do
  if [ -f "$candidato" ]; then
    cp "$candidato" dist/
    echo "  pesos incluídos: $(basename "$candidato")"
    break
  fi
done

echo
echo "pacote em dist/:"
ls -la dist/
echo
echo "para testar:  cd dist && java -cp jchesscheater.jar chesscheater.teste.TesteDeFumaca"
echo "para rodar :  cd dist && java -jar jchesscheater.jar"
