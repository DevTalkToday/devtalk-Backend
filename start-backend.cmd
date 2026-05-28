@echo off
cd /d "%~dp0"
if not exist "%~dp0logs" mkdir "%~dp0logs"
set "RUN_ID=%RANDOM%%RANDOM%"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-local.ps1" > "%~dp0logs\spring-server-%RUN_ID%.log" 2> "%~dp0logs\spring-server-%RUN_ID%.err.log"
