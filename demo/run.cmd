@echo off
REM Start one public-demo process against a local SQLite file.
REM Usage: demo\run.cmd [sts|admin|admin-api]
REM JDK 21 is required. The DEMO user is created on first STS or Admin start.
setlocal
set "ROLE=%~1"
if "%ROLE%"=="" set "ROLE=sts"
if /I "%ROLE%"=="sts" (
  set "MODULE=skoruba4j-sts"
  set "PORT=5050"
) else if /I "%ROLE%"=="admin" (
  set "MODULE=skoruba4j-admin"
  set "PORT=6060"
) else if /I "%ROLE%"=="admin-api" (
  set "MODULE=skoruba4j-admin-api"
  set "PORT=44302"
) else (
  echo Usage: %~nx0 [sts^|admin^|admin-api]
  exit /b 1
)

cd /d "%~dp0.."
if not defined IDSERVER_HOME set "IDSERVER_HOME=%CD%\demo\runtime"
if not exist "%IDSERVER_HOME%\data" mkdir "%IDSERVER_HOME%\data"
if not exist "%IDSERVER_HOME%\secrets" mkdir "%IDSERVER_HOME%\secrets"
if not exist "%IDSERVER_HOME%\logs" mkdir "%IDSERVER_HOME%\logs"

set "JSQLITE_REV=90f7ede35801a60cd3ea9e206a03d5cf68b35c14"
set "JSQLITE_JAR=%USERPROFILE%\.m2\repository\com\myano\jsqlite\0.1.0-SNAPSHOT\jsqlite-0.1.0-SNAPSHOT.jar"
if exist "%JSQLITE_JAR%" goto :package
set "JSQLITE_SRC=%CD%\..\JSQLite"
if exist "%JSQLITE_SRC%\pom.xml" goto :install_jsqlite
set "JSQLITE_SRC=%CD%\..\jsqlite"
if exist "%JSQLITE_SRC%\pom.xml" goto :install_jsqlite
set "JSQLITE_SRC=%IDSERVER_HOME%\jsqlite-src"
if exist "%JSQLITE_SRC%\pom.xml" goto :install_jsqlite
echo Cloning public JSQLite %JSQLITE_REV% (SQLite driver, no secrets).
git init "%JSQLITE_SRC%" || exit /b 1
git -C "%JSQLITE_SRC%" remote add origin https://github.com/Myanowu/jsqlite.git || exit /b 1
git -C "%JSQLITE_SRC%" fetch --depth 1 origin %JSQLITE_REV% || exit /b 1
git -C "%JSQLITE_SRC%" checkout --detach FETCH_HEAD || exit /b 1
:install_jsqlite
echo Installing com.myano:jsqlite from %JSQLITE_SRC%
call mvnw.cmd -f "%JSQLITE_SRC%\pom.xml" -B -DskipTests install
if errorlevel 1 exit /b 1
:package
call mvnw.cmd -pl %MODULE% -am -DskipTests package
if errorlevel 1 exit /b 1

set "JAR="
for %%f in ("%MODULE%\target\%MODULE%-*.jar") do (
  echo %%~xf | findstr /I /C:".original" >nul || set "JAR=%%~f"
)
if "%JAR%"=="" (
  echo Packaged jar not found under %MODULE%\target
  exit /b 1
)

echo IDSERVER_HOME=%IDSERVER_HOME%
echo Starting %ROLE% from %JAR%
echo When the process is up: http://127.0.0.1:%PORT%/health
java -Xmx512m -Dspring.profiles.active=oss -Dserver.ssl.enabled=false -DIDSERVER_HOME=%IDSERVER_HOME% -Dspring.config.additional-location=file:%CD%\demo\idserver-demo.yml -jar "%JAR%"
exit /b %ERRORLEVEL%
