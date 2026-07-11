#!/usr/bin/env bash

set -u

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
DATA_DIR="$PROJECT_ROOT/data"
CHROMA_DIR="$DATA_DIR/chroma"
LOG_DIR="$PROJECT_ROOT/logs/dev-services"
CHROMA_HOST="127.0.0.1"
CHROMA_PORT="8000"

START_OLLAMA=false

for arg in "$@"; do
  case "$arg" in
    --ollama)
      START_OLLAMA=true
      ;;
    *)
      echo "Unknown option: $arg"
      echo "Usage: ./startup-dev-services.sh [--ollama]"
      exit 1
      ;;
  esac
done

mkdir -p "$CHROMA_DIR"
mkdir -p "$LOG_DIR"

is_port_listening() {
  local port="$1"
  lsof -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
}

wait_for_port() {
  local name="$1"
  local port="$2"
  local max_seconds="$3"

  for ((i = 1; i <= max_seconds; i++)); do
    if is_port_listening "$port"; then
      echo "✅ $name is running on port $port"
      return 0
    fi

    sleep 1
  done

  echo "❌ $name did not start on port $port within ${max_seconds}s"
  return 1
}

start_ollama() {
  if is_port_listening 11434; then
    echo "✅ Ollama is already running on port 11434"
    return
  fi

  echo "Starting Ollama..."

  if [[ "$OSTYPE" == "darwin"* ]]; then
    open -a Ollama >/dev/null 2>&1 || true
  else
    ollama serve > "$LOG_DIR/ollama.log" 2>&1 &
  fi

  wait_for_port "Ollama" 11434 30
}

start_chroma() {
  if is_port_listening "$CHROMA_PORT"; then
    echo "✅ Chroma is already running on port $CHROMA_PORT"
    return
  fi

  if ! command -v chroma >/dev/null 2>&1; then
    echo "❌ Chroma CLI not found."
    echo "   Install it first, then try again."
    return 1
  fi

  echo "Starting Chroma..."
  cd "$PROJECT_ROOT" || exit 1

  chroma run --path "$CHROMA_DIR" --host "$CHROMA_HOST" --port "$CHROMA_PORT" > "$LOG_DIR/chroma.log" 2>&1 &

  wait_for_port "Chroma" "$CHROMA_PORT" 30
}

start_vue() {
  if is_port_listening 5173; then
    echo "✅ Vue dev server is already running on port 5173"
    return
  fi

  if [[ ! -d "$FRONTEND_DIR" ]]; then
    echo "❌ Frontend directory not found: $FRONTEND_DIR"
    return 1
  fi

  echo "Starting Vue dev server..."
  cd "$FRONTEND_DIR" || exit 1

  npm run dev > "$LOG_DIR/vue.log" 2>&1 &

  wait_for_port "Vue" 5173 30
}

echo
echo "Starting local-document-assistant dev services..."
echo "------------------------------------------------"

if [[ "$START_OLLAMA" == true ]]; then
  start_ollama
else
  if is_port_listening 11434; then
    echo "✅ Ollama is already running on port 11434"
  else
    echo "⚠️  Ollama is not running."
    echo "   Start it manually, or run:"
    echo "   ./startup-dev-services.sh --ollama"
  fi
fi

start_chroma
start_vue

echo
echo "Service URLs:"
echo "-------------"
echo "Vue:        http://localhost:5173"
echo "Chroma:     http://$CHROMA_HOST:$CHROMA_PORT"
echo "Ollama:     http://localhost:11434"
echo
echo "Spring Boot:"
echo "------------"
echo "Not started by this script. Start it from IntelliJ/debug as usual."
echo
echo "Logs:"
echo "-----"
echo "$LOG_DIR"
echo
echo "Done."
