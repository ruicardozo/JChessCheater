@echo off
REM JChessCheater — abre o programa com um Java 21.
REM
REM Por que este .bat existe: e comum ter um Java mais antigo no PATH e um 21 instalado a
REM parte. Este arquivo procura um 21 em alguns lugares conhecidos antes de desistir.
REM
REM Se o seu 21 estiver noutro lugar, defina JAVA21 antes de rodar, ou edite a linha abaixo:
REM    set JAVA21=C:\caminho\do\jdk-21

setlocal
cd /d "%~dp0"

if not "%JAVA21%"=="" goto :achou

REM Lugares conhecidos, em ordem.
for %%D in (
  "C:\Users\%USERNAME%\.jdks\temurin-21"
  "C:\Program Files\Eclipse Adoptium\jdk-21"
  "C:\Program Files\Java\jdk-21"
  "%JAVA_HOME%"
) do (
  if exist "%%~D\bin\java.exe" (
    set JAVA21=%%~D
    goto :achou
  )
)

REM Nao achou um 21 conhecido: tenta o java do PATH. Se for antigo, o proprio programa avisa
REM com uma mensagem clara (a classe Iniciar roda em qualquer Java a partir do 8).
echo Nenhum JDK 21 conhecido encontrado; tentando o java do PATH...
java -jar jchesscheater.jar %*
goto :fim

:achou
echo Usando: %JAVA21%
"%JAVA21%\bin\java.exe" -jar jchesscheater.jar %*

:fim
if errorlevel 1 pause
endlocal
