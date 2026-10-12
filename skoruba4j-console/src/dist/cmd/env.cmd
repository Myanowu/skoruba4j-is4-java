@echo off
setlocal
if not defined IDSERVER_HOME (
  set "IDSERVER_HOME=%~dp0.."
)
for %%I in ("%IDSERVER_HOME%") do set "IDSERVER_HOME=%%~fI"
set "JAVA_HOME=%IDSERVER_HOME%\jdk"
set "IDSERVER_CONFIG=%IDSERVER_HOME%\config\idserver-local.yml"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Bundled JDK not found: %JAVA_HOME%
  echo Rebuild dist so jdk\ is copied from the build JDK.
  exit /b 1
)
if not exist "%IDSERVER_HOME%\logs" mkdir "%IDSERVER_HOME%\logs"
if not exist "%IDSERVER_HOME%\data" mkdir "%IDSERVER_HOME%\data"
if exist "%IDSERVER_HOME%\config\jvm.env.cmd" call "%IDSERVER_HOME%\config\jvm.env.cmd"
if not defined IDSERVER_STS_XMX set "IDSERVER_STS_XMX=512m"
if not defined IDSERVER_ADMIN_XMX set "IDSERVER_ADMIN_XMX=512m"
if not defined IDSERVER_API_XMX set "IDSERVER_API_XMX=256m"
endlocal & set "IDSERVER_HOME=%IDSERVER_HOME%" & set "JAVA_HOME=%JAVA_HOME%" & set "IDSERVER_CONFIG=%IDSERVER_CONFIG%" & set "IDSERVER_STS_XMX=%IDSERVER_STS_XMX%" & set "IDSERVER_ADMIN_XMX=%IDSERVER_ADMIN_XMX%" & set "IDSERVER_API_XMX=%IDSERVER_API_XMX%"
