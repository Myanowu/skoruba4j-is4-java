@echo off
call "%~dp0env.cmd" || exit /b 1
for %%P in (5051 6061 44302) do (
  for /f "tokens=5" %%A in ('netstat -ano ^| findstr /I "LISTENING" ^| findstr ":%%P "') do (
    if not "%%A"=="0" taskkill /F /PID %%A >nul 2>&1
  )
)
echo Stopped listeners on 5051, 6061, and 44302.
