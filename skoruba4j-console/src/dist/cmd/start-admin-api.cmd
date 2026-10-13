@echo off
REM Skoruba4j Admin API launcher. Reads heap settings from jvm.env.cmd (written by skoruba4j-console).
setlocal
pushd "%~dp0"
call "%~dp0env.cmd" || exit /b 1
if exist "%~dp0jvm.env.cmd" call "%~dp0jvm.env.cmd"
set "XMX=%IDSERVER_API_XMX%"
if "%XMX%"=="" set "XMX=256m"
set "JAR="
for %%f in ("%~dp0..\lib\skoruba4j-admin-api-*.jar") do set "JAR=%%~f"
if "%JAR%"=="" (
  echo Admin API jar not found under %~dp0..\lib
  exit /b 1
)
"%JAVA_HOME%\bin\java.exe" -Xmx%XMX% -Dspring.profiles.active=oss,local -DIDSERVER_HOME=%IDSERVER_HOME% -jar "%JAR%"
popd
