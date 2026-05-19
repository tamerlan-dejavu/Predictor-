#!/bin/bash

# Branch Predictor Lab - Quick Open (Production)
# Открывает браузер на Vercel фронтенд (подключен к Render бэку)

FRONTEND_URL="https://predictor-chi.vercel.app/"
BACKEND_URL="https://predictor-api-whg0.onrender.com"

echo
echo "===== Branch Predictor Lab - Demo ====="
echo
echo "Frontend: $FRONTEND_URL"
echo "Backend:  $BACKEND_URL"
echo

if [[ "$OSTYPE" == "darwin"* ]]; then
    open "$FRONTEND_URL"
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    if command -v xdg-open &> /dev/null; then
        xdg-open "$FRONTEND_URL"
    elif command -v sensible-browser &> /dev/null; then
        sensible-browser "$FRONTEND_URL"
    fi
else
    echo "Please manually open: $FRONTEND_URL"
fi

echo "If you see errors, check that Render backend is awake."
echo "(Render spins down after 15 min of inactivity)"
echo
