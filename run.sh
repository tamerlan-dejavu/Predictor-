#!/bin/bash

# Branch Predictor Lab - Full Stack Demo Launcher
# Запускает бэкенд + фронтенд и открывает браузер

set -e

echo
echo "===== Branch Predictor Lab - Demo Launcher ====="
echo

# Проверяем наличие Maven и Node.js
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven not found. Please install Maven 3.9+ and add it to PATH."
    exit 1
fi

if ! command -v node &> /dev/null; then
    echo "ERROR: Node.js not found. Please install Node.js 20+ and add it to PATH."
    exit 1
fi

# Получаем директорию скрипта
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

echo "[1/4] Installing frontend dependencies..."
cd frontend
if [ ! -d "node_modules" ]; then
    echo "Running: npm install"
    npm install
else
    echo "npm packages already installed."
fi
cd ..

echo
echo "[2/4] Starting Spring Boot backend on http://localhost:8080..."
echo
mvn spring-boot:run &
BACKEND_PID=$!
sleep 3

echo
echo "[3/4] Starting React frontend on http://localhost:5173..."
echo
cd frontend
npm run dev &
FRONTEND_PID=$!
cd ..

sleep 2

echo
echo "[4/4] Opening browser..."
echo
sleep 1

# Открываем браузер (кроссплатформенно)
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    open http://localhost:5173
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    # Linux
    if command -v xdg-open &> /dev/null; then
        xdg-open http://localhost:5173
    elif command -v sensible-browser &> /dev/null; then
        sensible-browser http://localhost:5173
    fi
fi

echo
echo "===== Demo is ready! ====="
echo
echo "Backend API:  http://localhost:8080"
echo "Frontend UI:  http://localhost:5173"
echo
echo "Press Ctrl+C to stop all servers."
echo

# Ожидаем завершения процессов
wait $BACKEND_PID $FRONTEND_PID
