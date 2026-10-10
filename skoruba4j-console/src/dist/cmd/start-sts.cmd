@echo off
REM Skoruba4j STS launcher. Reads heap settings from jvm.env.cmd (written by skoruba4j-console).
setlocal
pushd "%~dp0"
if exist "%~dp0jvm.env.cmd" call "%~dp0jvm.env.cmd"
set "XMX=%IDSERVER_STS_XMX%"
if "%XMX%"=="" set "XMX=512m"
set "JAR="
for %%f in ("%~dp0..\lib\skoruba4j-sts-*.jar") do set "JAR=%%~f"
if "%JAR%"=="" (
  echo STS jar not found under %~dp0..\lib
  exit /b 1
)
"%~dp0..\jdk\bin\java.exe" -Xmx%XMX% -Dspring.profiles.active=oss,local -jar "%JAR%"
popd
