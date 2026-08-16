#!/usr/bin/env bash
# JChessCheater — compila se preciso e abre a janela.
#
# O java tem que ser 21: o jchessai.jar é class file 65. Se o do PATH for mais antigo,
# aponte um 21 em JAVA21_HOME (ex.: export JAVA21_HOME=~/.jdks/temurin-21).
set -euo pipefail
cd "$(dirname "$0")"

JAVA_BIN="${JAVA21_HOME:+$JAVA21_HOME/bin/}java"
JAVAC_BIN="${JAVA21_HOME:+$JAVA21_HOME/bin/}javac"

if [ ! -f lib/jchessai.jar ]; then
  echo "lib/jchessai.jar não está lá. Gere no JChessAI e copie:" >&2
  echo "  (cd ../JChessAI && ./gradlew fatJar) && cp ../JChessAI/uci/build/libs/jchessai.jar lib/" >&2
  exit 1
fi

# Recompila se algum fonte estiver mais novo que o bin.
if [ ! -d bin ] || [ -n "$(find src -name '*.java' -newer bin 2>/dev/null | head -1)" ]; then
  echo "compilando..."
  mkdir -p bin
  "$JAVAC_BIN" -encoding UTF-8 -d bin -cp lib/jchessai.jar $(find src -name '*.java')
  touch bin
fi

exec "$JAVA_BIN" -cp "bin:lib/jchessai.jar" chesscheater.JChessCheater "$@"
