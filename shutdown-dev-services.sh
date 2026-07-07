#!/usr/bin/env bash

set -u

STOP_OLLAMA_SERVER=false

for arg in "$@"; do
  case "$arg" in
    --ollama)
      STOP_OLLAMA_SERVER=true
      ;;
    *)
      echo "Unknown option: $arg"
      echo "Usage: ./shutdown-dev-services.sh [--ollama]"
      exit 1
      ;;
  esac
done

shutdown_port() {
  local name="$1"
  local port="$2"

  local pids
  pids=$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | sort -u || true)

  if [[ -z "$pids" ]]; then
    echo "✅ $name is not running on port $port"
    return
  fi

  echo "Stopping $name on port $port..."
  echo "$pids" | while read -r pid; do
    if [[ -n "$pid" ]]; then
      echo "  Sending SIGTERM to PID $pid"
      kill -TERM "$pid" 2>/dev/null || true
    fi
  done

  # Wait up to 10 seconds
  for i in {1..10}; do
    sleep 1

    local still_running
    still_running=$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | sort -u || true)

    if [[ -z "$still_running" ]]; then
      echo "✅ $name stopped"
      return
    fi
  done

  echo "⚠️  $name did not stop gracefully. Forcing shutdown..."
  lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | sort -u | while read -r pid; do
    if [[ -n "$pid" ]]; then
      echo "  Sending SIGKILL to PID $pid"
      kill -KILL "$pid" 2>/dev/null || true
    fi
  done
}

stop_ollama_models() {
  if ! command -v ollama >/dev/null 2>&1; then
    echo "✅ Ollama CLI not found, skipping model unload"
    return
  fi

  echo "Unloading Ollama models if loaded..."

  # These are the models we have been using in the prototype.
  ollama stop nomic-embed-text >/dev/null 2>&1 || true
  ollama stop qwen3:8b >/dev/null 2>&1 || true
  ollama stop qwen2.5:7b >/dev/null 2>&1 || true

  echo "✅ Ollama model unload attempted"
}

echo
echo "Shutting down local-document-assistant dev services..."
echo "-----------------------------------------------------"

shutdown_port "Vue dev server" 5173
shutdown_port "Spring Boot backend" 8080
shutdown_port "Chroma server" 8000

echo
stop_ollama_models

if [[ "$STOP_OLLAMA_SERVER" == true ]]; then
  echo
  echo "Stopping Ollama server/app on port 11434..."
  shutdown_port "Ollama" 11434
else
  echo
  echo "ℹ️  Ollama server left running."
  echo "   To stop it as well, run:"
  echo "   ./shutdown-dev-services.sh --ollama"
fi

echo
echo "Done."
