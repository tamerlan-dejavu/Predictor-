#!/bin/bash

# Branch Predictor Lab - Quick Open
# Открывает браузер на http://localhost:5173
# (используйте если бэк и фронт уже запущены)

echo "Opening http://localhost:5173 in default browser..."

if [[ "$OSTYPE" == "darwin"* ]]; then
    open http://localhost:5173
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    if command -v xdg-open &> /dev/null; then
        xdg-open http://localhost:5173
    elif command -v sensible-browser &> /dev/null; then
        sensible-browser http://localhost:5173
    fi
else
    echo "Please manually open: http://localhost:5173"
fi
