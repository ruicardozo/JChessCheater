@echo off
REM JChessCheater — compila se preciso e abre a janela.
REM
REM O java tem que ser 21: o jchessai.jar e class file 65. Nesta maquina o java do PATH e o 17,
REM entao apontamos o Temurin 21 embutido na extensao redhat.java do VSCode (o mesmo caminho
REM que o JChessAI usa). Ajuste JAVA21 se mudar de maquina.
setlocal
cd /d "%~dp0"

if "%JAVA21%"=="" set JAVA21=C:\Users\rc59576\.jdks\temurin-21

if not exist "%JAVA21%\bin\java.exe" (
  echo JDK 21 nao encontrado em %JAVA21%
  echo Defina JAVA21 apontando para um JDK 21.
  exit /b 1
)

if not exist "lib\jchessai.jar" (
  echo lib\jchessai.jar nao esta la. Gere no JChessAI ^(gradlew fatJar^) e copie para lib\.
  exit /b 1
)

if not exist "bin" mkdir bin

dir /s /b src\*.java > "%TEMP%\jcc_fontes.txt"
"%JAVA21%\bin\javac.exe" -encoding UTF-8 -d bin -cp lib\jchessai.jar @"%TEMP%\jcc_fontes.txt"
if errorlevel 1 exit /b 1

"%JAVA21%\bin\java.exe" -cp "bin;lib\jchessai.jar" chesscheater.JChessCheater %*
endlocal
