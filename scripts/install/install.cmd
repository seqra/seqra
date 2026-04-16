@echo off
REM OpenTaint installer for Windows (CMD wrapper)
REM This script invokes the PowerShell installer.
REM Usage: install.cmd            installs latest
REM        install.cmd 1.2.3       installs version 1.2.3

where powershell >nul 2>nul
if %ERRORLEVEL% equ 0 (
    if "%~1"=="" (
        powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1"
    ) else (
        powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" -Version "%~1"
    )
    exit /b %ERRORLEVEL%
)
where pwsh >nul 2>nul
if %ERRORLEVEL% equ 0 (
    if "%~1"=="" (
        pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1"
    ) else (
        pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" -Version "%~1"
    )
    exit /b %ERRORLEVEL%
)
echo Error: PowerShell is required to install opentaint.
echo Please install PowerShell or use the manual installation method.
echo See https://github.com/seqra/opentaint/blob/main/docs/installation.md for alternatives.
exit /b 1
