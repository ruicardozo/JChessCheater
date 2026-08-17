@echo off
REM Monta o pacote distribuivel em dist\ — uma pasta que se copia inteira para qualquer maquina:
REM
REM   dist\jchesscheater.jar    o programa (Main-Class e Class-Path no manifesto)
REM   dist\jchessai.jar         o motor e as regras
REM   dist\jchesscheater.json   o JSON mestre: qual iteracao usar
REM   dist\LEIAME.txt           o essencial
REM   dist\iter_0080.pt         os pesos, se existirem por perto (opcional)
REM
REM Rodar la: java -jar jchesscheater.jar
setlocal enabledelayedexpansion
cd /d "%~dp0"

if "%JAVA21%"=="" set JAVA21=C:\Users\rc59576\.jdks\temurin-21
if not exist "%JAVA21%\bin\javac.exe" (
  echo JDK 21 nao encontrado em %JAVA21%. Defina JAVA21 apontando para um JDK 21.
  exit /b 1
)

set MOTOR=%1
if "%MOTOR%"=="" set MOTOR=lib\jchessai.jar
if not exist "%MOTOR%" (
  echo Motor nao encontrado: %MOTOR%
  echo Gere no JChessAI ^(gradlew fatJar^) e copie para lib\, ou passe o caminho:
  echo   empacotar.bat C:\caminho\jchessai.jar
  exit /b 1
)

echo compilando...
if exist build\classes rmdir /s /q build\classes
if exist dist rmdir /s /q dist
mkdir build\classes
mkdir dist
dir /s /b src\*.java > "%TEMP%\jcc_fontes.txt"
"%JAVA21%\bin\javac.exe" -encoding UTF-8 -d build\classes -cp "%MOTOR%" @"%TEMP%\jcc_fontes.txt"
if errorlevel 1 exit /b 1

echo empacotando...
> build\manifesto.txt echo Main-Class: chesscheater.JChessCheater
>> build\manifesto.txt echo Class-Path: jchessai.jar
>> build\manifesto.txt echo Implementation-Title: JChessCheater
>> build\manifesto.txt echo Implementation-Version: 1.0.0

"%JAVA21%\bin\jar.exe" --create --file dist\jchesscheater.jar --manifest build\manifesto.txt -C build\classes .
if errorlevel 1 exit /b 1

copy /y "%MOTOR%" dist\jchessai.jar >nul
copy /y pacote\jchesscheater.json dist\ >nul
copy /y pacote\LEIAME.txt dist\ >nul

REM Os pesos sao opcionais: 65 MB nem sempre se quer carregar junto.
for %%C in ("weights\iter_0080.pt" "..\JChessAI\weights\iter_0080.pt" "D:\Downloads\temp\iter_0080.pt") do (
  if exist %%C (
    copy /y %%C dist\ >nul
    echo   pesos incluidos: %%~nxC
    goto :pesos_ok
  )
)
echo   sem pesos no pacote: ajuste "pesos" no dist\jchesscheater.json
:pesos_ok

echo.
echo pacote em dist\:
dir /b dist
echo.
echo para testar:  cd dist ^&^& java -cp jchesscheater.jar chesscheater.teste.TesteDeFumaca
echo para rodar :  cd dist ^&^& java -jar jchesscheater.jar
endlocal
