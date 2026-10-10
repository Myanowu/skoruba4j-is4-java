@echo off
REM Skoruba4j Console (Swing ops UI) launcher.
setlocal
pushd "%~dp0"
set "JAR="
for %%f in ("%~dp0..\lib\skoruba4j-console-*.jar") do set "JAR=%%~f"
if "%JAR%"=="" (
  echo Console jar not found under %~dp0..\lib
  exit /b 1
)
start "" "%~dp0..\jdk\bin\javaw.exe" -jar "%JAR%"
popd
