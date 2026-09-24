@echo off
setlocal

set "SCRIPT=%~dp0scripts\clean.ps1"

if not exist "%SCRIPT%" (
    echo Cleanup script not found: %SCRIPT%
    exit /b 1
)

where pwsh.exe >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    pwsh.exe -NoProfile -File "%SCRIPT%"
    exit /b %ERRORLEVEL%
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%"
exit /b %ERRORLEVEL%
