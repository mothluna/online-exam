@echo off
rem Stop whatever is listening on the given port (default 8080).
rem
rem Why a .cmd file instead of inline "powershell -Command ...":
rem   the VS Code task terminal may itself be PowerShell, which expands
rem   $_.OwningProcess inside double quotes BEFORE the nested powershell
rem   runs, so the inner command receives an empty Id and kills nothing.
rem   A batch file contains no '$', so it behaves the same in cmd and PS.
rem
rem Keep this file ASCII-only: cmd.exe parses .cmd with the OEM codepage,
rem   non-ASCII comments can break the for-loop parsing.
rem
rem Usage: stop-port.cmd [port]

setlocal enabledelayedexpansion
set "PORT=%~1"
if "%PORT%"=="" set "PORT=8080"

set "FOUND="
set "LASTPID="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%PORT% " ^| findstr /i "LISTENING"') do (
    if not "%%a"=="!LASTPID!" (
        set "FOUND=1"
        set "LASTPID=%%a"
        echo Stopping PID %%a on port %PORT%
        taskkill /F /PID %%a >nul 2>&1
    )
)

if not defined FOUND echo No listener on port %PORT%, nothing to do.
exit /b 0
