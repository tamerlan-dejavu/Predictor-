#!/bin/bash

# Branch Predictor Lab - Demo Launcher (PRODUCTION)
# фронтенд на Vercel, бэк на Render
# Для локальной разработки: mvn spring-boot:run (отдельный терминал) + cd frontend && npm run dev

FRONTEND_URL="https://predictor-chi.vercel.app/"
BACKEND_URL="https://predictor-api-whg0.onrender.com"

echo
echo "===== Branch Predictor Lab - Demo Launcher (PRODUCTION) ====="
echo

echo "Production URLs:"
echo "Backend API:   $BACKEND_URL"
echo "Frontend UI:   $FRONTEND_URL"
echo

sleep 1

echo "[1/2] Waking up Render backend (if asleep after 15 min inactivity)..."
if curl -s "$BACKEND_URL/api/health" > /dev/null 2>&1; then
    echo "Backend is ready."
else
    echo "Note: Render backend may take a moment to wake up (30-60 sec)..."
fi

sleep 1

echo
echo "[2/2] Opening Vercel frontend in browser..."
echo
sleep 1

# Открываем браузер (кроссплатформенно)
if [[ "$OSTYPE" == "darwin"* ]]; then
    open "$FRONTEND_URL"
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    if command -v xdg-open &> /dev/null; then
        xdg-open "$FRONTEND_URL"
    elif command -v sensible-browser &> /dev/null; then
        sensible-browser "$FRONTEND_URL"
    fi
fi

echo
echo "===== Demo opened! ====="
echo
echo "Frontend: $FRONTEND_URL"
echo "Backend:  $BACKEND_URL"
echo
echo "If you see connection errors:"
echo "1. Render backend might be sleeping (takes 30-60 sec to wake)"
echo "2. Check both URLs are reachable manually"
echo
