@echo off
REM Branch Predictor Lab - Full Stack Demo Launcher
REM Запускает бэкенд + фронтенд и открывает браузер

echo.
echo ===== Branch Predictor Lab - Demo Launcher =====
echo.

REM Проверяем наличие Maven и Node.js
where mvn >nul 2>&1
if errorlevel 1 (
    echo ERROR: Maven not found. Please install Maven 3.9+ and add it to PATH.
    pause
    exit /b 1
)

where node >nul 2>&1
if errorlevel 1 (
    echo ERROR: Node.js not found. Please install Node.js 20+ and add it to PATH.
    pause
    exit /b 1
)

REM Получаем директорию скрипта
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

echo [1/4] Installing frontend dependencies...
cd frontend
if not exist "node_modules\" (
    echo Running: npm install
    call npm install
    if errorlevel 1 (
        echo ERROR: npm install failed.
        pause
        exit /b 1
    )
) else (
    echo npm packages already installed.
)
cd ..

echo.
echo [2/4] Starting Spring Boot backend on http://localhost:8080...
echo.
start "Branch Predictor Lab - Backend" cmd /k "mvn spring-boot:run"
timeout /t 3 /nobreak

echo.
echo [3/4] Starting React frontend on http://localhost:5173...
echo.
cd frontend
start "Branch Predictor Lab - Frontend" cmd /k "npm run dev"
cd ..

timeout /t 2 /nobreak

echo.
echo [4/4] Opening browser...
echo.
timeout /t 2 /nobreak

REM Открываем браузер
start http://localhost:5173

echo.
echo ===== Demo is ready! =====
echo.
echo Backend API:  http://localhost:8080
echo Frontend UI:  http://localhost:5173
echo.
echo Press Ctrl+C in the terminal windows to stop the servers.
echo.
pause
