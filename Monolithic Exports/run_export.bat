@echo off
setlocal
cd /d "%~dp0"
echo ========================================================
echo   Running Monolithic Project Exporter
echo ========================================================
python export_monolith.py %*
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Python export failed with exit code %ERRORLEVEL%.
    pause
    exit /b %ERRORLEVEL%
)
echo.
echo Monolith generated successfully in "%~dp0"
echo ========================================================
endlocal
