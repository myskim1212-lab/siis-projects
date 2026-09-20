@echo off
setlocal
cd /d "%~dp0"

python -c "import webview" 2>nul
if errorlevel 1 (
    echo [run.bat] Installing dependencies...
    python -m pip install --no-index --find-links=vendor\wheels -r requirements.txt
    if errorlevel 1 (
        echo [run.bat] Offline install failed, trying online install...
        python -m pip install -r requirements.txt
    )
    if errorlevel 1 (
        echo [run.bat] Dependency install failed.
        pause
        exit /b 1
    )
)

python main.py
if errorlevel 1 (
    echo.
    echo [run.bat] Application exited with an error.
    pause
)
