@echo off
REM Branch Predictor Lab - Demo Launcher
REM PRODUCTION: фронтенд на Vercel, бэк на Render
REM Для локальной разработки используйте: git branch local-dev && mvn spring-boot:run (отдельный терминал) + cd frontend && npm run dev

echo.
echo ===== Branch Predictor Lab - Demo Launcher (PRODUCTION) =====
echo.

set FRONTEND_URL=https://predictor-chi.vercel.app/
set BACKEND_URL=https://predictor-api-whg0.onrender.com

echo Production URLs:
echo Backend API:   %BACKEND_URL%
echo Frontend UI:   %FRONTEND_URL%
echo.

timeout /t 1 /nobreak

echo [1/2] Waking up Render backend (if asleep after 15 min inactivity)...
curl -s %BACKEND_URL%/api/health >nul 2>&1
if errorlevel 1 (
    echo Note: Render backend may take a moment to wake up...
) else (
    echo Backend is ready.
)

timeout /t 1 /nobreak

echo.
echo [2/2] Opening Vercel frontend in browser...
echo.
timeout /t 1 /nobreak

start %FRONTEND_URL%

echo.
echo ===== Demo opened! =====
echo.
echo Frontend: %FRONTEND_URL%
echo Backend:  %BACKEND_URL%
echo.
if errorlevel 1 (
    echo If you see connection errors:
    echo 1. Render backend might be sleeping (takes 30-60 sec to wake)
    echo 2. Check both URLs are reachable manually
    echo.
)
pause
