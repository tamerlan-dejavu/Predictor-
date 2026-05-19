@echo off
REM Branch Predictor Lab - Quick Open (Production)
REM Открывает браузер на Vercel фронтенд (подключен к Render бэку)

set FRONTEND_URL=https://predictor-chi.vercel.app/
set BACKEND_URL=https://predictor-api-whg0.onrender.com

echo.
echo ===== Branch Predictor Lab - Demo =====
echo.
echo Frontend: %FRONTEND_URL%
echo Backend:  %BACKEND_URL%
echo.

start %FRONTEND_URL%
echo Opened %FRONTEND_URL% in default browser.
echo.
echo If you see errors, check that Render backend is awake.
echo (Render spins down after 15 min of inactivity)
echo.
