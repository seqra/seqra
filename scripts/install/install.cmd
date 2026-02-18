@echo off
REM Seqra installer for Windows (CMD wrapper)
REM This script invokes the PowerShell installer.

where powershell >nul 2>nul
if %ERRORLEVEL% equ 0 (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1"
) else (
    where pwsh >nul 2>nul
    if %ERRORLEVEL% equ 0 (
        pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1"
    ) else (
        echo Error: PowerShell is required to install seqra.
        echo Please install PowerShell or use the manual installation method.
        exit /b 1
    )
)
