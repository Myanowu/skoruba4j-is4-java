@echo off
REM Skoruba4j STS launcher. Reads heap settings from jvm.env.cmd (written by skoruba4j-console).
setlocal
pushd "%~dp0"
call "%~dp0env.cmd" || exit /b 1
if exist "%~dp0jvm.env.cmd" call "%~dp0jvm.env.cmd"
set "XMX=%IDSERVER_STS_XMX%"
if "%XMX%"=="" set "XMX=512m"
set "JAR="
for %%f in ("%~dp0..\lib\skoruba4j-sts-*.jar") do set "JAR=%%~f"
if "%JAR%"=="" (
  echo STS jar not found under %~dp0..\lib
  exit /b 1
)
"%JAVA_HOME%\bin\java.exe" -Xmx%XMX% -Dspring.profiles.active=oss,local -DIDSERVER_HOME=%IDSERVER_HOME% -jar "%JAR%"
popd
