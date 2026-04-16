@echo off
REM OpenTaint installer for Windows (CMD wrapper)
REM This script invokes the PowerShell installer.
REM Usage: install.cmd            installs latest
REM        install.cmd 1.2.3       installs version 1.2.3

where powershell >nul 2>nul
if %ERRORLEVEL% equ 0 (
    call :run_installer powershell %*
    exit /b %ERRORLEVEL%
)
where pwsh >nul 2>nul
if %ERRORLEVEL% equ 0 (
    call :run_installer pwsh %*
    exit /b %ERRORLEVEL%
)
echo Error: PowerShell is required to install opentaint.
echo Please install PowerShell or use the manual installation method.
echo See https://github.com/seqra/opentaint/blob/main/docs/installation.md for alternatives.
exit /b 1

:run_installer
REM %1 = shell executable (powershell or pwsh), %2 = optional version
if "%~2"=="" (
    %1 -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1"
) else (
    %1 -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" -Version "%~2"
)
exit /b %ERRORLEVEL%
