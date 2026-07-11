#!/usr/bin/env bash

set -u

VUE_URL="http://localhost:5173"
SPRING_URL="http://localhost:8080/api/folders"
OLLAMA_URL="http://localhost:11434/api/tags"
CHROMA_URL="http://127.0.0.1:8000"
CHROMA_V2_HEARTBEAT="$CHROMA_URL/api/v2/heartbeat"
CHROMA_V1_HEARTBEAT="$CHROMA_URL/api/v1/heartbeat"

EMBEDDING_MODEL="nomic-embed-text"
CHAT_MODEL="qwen3:8b"

all_ok=true

check_http() {
  local name="$1"
  local url="$2"

  status=$(curl -s -o /dev/null -w "%{http_code}" "$url" || true)

  if [[ "$status" =~ ^2|3 ]]; then
    echo "✅ $name is running at $url"
    return 0
  else
    echo "❌ $name is not responding at $url  HTTP status: $status"
    all_ok=false
    return 1
  fi
}

check_ollama_model() {
  local model="$1"

  if curl -s "$OLLAMA_URL" | grep -q "\"name\":\"$model"; then
    echo "✅ Ollama model installed: $model"
  else
    echo "⚠️  Ollama model not found: $model"
    echo "   Try: ollama pull $model"
    all_ok=false
  fi
}

echo
echo "Checking local-document-assistant dev services..."
echo "------------------------------------------------"

check_http "Vue dev server" "$VUE_URL"
check_http "Spring Boot backend" "$SPRING_URL"

echo
echo "Checking Ollama..."
if check_http "Ollama" "$OLLAMA_URL"; then
  check_ollama_model "$EMBEDDING_MODEL"
  check_ollama_model "$CHAT_MODEL"
fi

echo
echo "Checking Chroma..."
if curl -s -o /dev/null -w "%{http_code}" "$CHROMA_V2_HEARTBEAT" | grep -qE "^(2|3)"; then
  echo "✅ Chroma is running at $CHROMA_V2_HEARTBEAT"
elif curl -s -o /dev/null -w "%{http_code}" "$CHROMA_V1_HEARTBEAT" | grep -qE "^(2|3)"; then
  echo "✅ Chroma is running at $CHROMA_V1_HEARTBEAT"
else
  echo "❌ Chroma is not responding at $CHROMA_URL"
  echo "   Try: chroma run --path ./data/chroma --host 127.0.0.1 --port 8000"
  all_ok=false
fi

echo
echo "Port summary:"
echo "-------------"
for port in 5173 8080 11434 8000; do
  if lsof -i :"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "✅ Port $port is listening"
  else
    echo "❌ Port $port is not listening"
  fi
done

echo
if [ "$all_ok" = true ]; then
  echo "✅ Everything looks ready."
  exit 0
else
  echo "⚠️  One or more services need attention."
  exit 1
fi
