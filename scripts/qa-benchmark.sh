#!/usr/bin/env bash
set -euo pipefail

QUESTIONS_FILE="${QUESTIONS_FILE:-scripts/qa-benchmark-questions.txt}"
BASE_URL="${QA_BASE_URL:-http://localhost:8080}"
OUTPUT_FILE="${QA_BENCHMARK_OUTPUT:-data/qa-benchmark/responses.jsonl}"
REPEAT_COUNT="${1:-${QA_BENCHMARK_REPEAT:-1}}"

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required." >&2
  exit 1
fi

if [[ ! -f "$QUESTIONS_FILE" ]]; then
  echo "Questions file not found: $QUESTIONS_FILE" >&2
  exit 1
fi

if ! [[ "$REPEAT_COUNT" =~ ^[0-9]+$ ]] || [[ "$REPEAT_COUNT" -lt 1 ]]; then
  echo "Repeat count must be a positive integer." >&2
  exit 1
fi

mkdir -p "$(dirname "$OUTPUT_FILE")"

TOTAL_QUESTIONS="$(awk '!/^[[:space:]]*(#|$)/ { count++ } END { print count + 0 }' "$QUESTIONS_FILE")"

if [[ "$TOTAL_QUESTIONS" -eq 0 ]]; then
  echo "No questions found in $QUESTIONS_FILE" >&2
  exit 1
fi

json_escape() {
  printf '%s' "$1" | sed \
    -e 's/\\/\\\\/g' \
    -e 's/"/\\"/g' \
    -e 's/	/\\t/g'
}

post_question() {
  local question="$1"
  local escaped_question
  escaped_question="$(json_escape "$question")"

  curl -sS \
    -X POST "$BASE_URL/api/questions" \
    -H "Content-Type: application/json" \
    --data-binary "{\"question\":\"$escaped_question\"}"
}

append_jsonl() {
  local run_number="$1"
  local question="$2"
  local response_body="$3"
  local timestamp
  local escaped_question
  local escaped_response_body

  timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  escaped_question="$(json_escape "$question")"
  escaped_response_body="$(json_escape "$response_body")"

  printf '{"timestamp":"%s","run":%s,"question":"%s","responseBody":"%s"}\n' \
    "$timestamp" \
    "$run_number" \
    "$escaped_question" \
    "$escaped_response_body" >> "$OUTPUT_FILE"
}

first_question() {
  grep -vE '^[[:space:]]*(#|$)' "$QUESTIONS_FILE" | sed -n '1p'
}

echo "Writing benchmark responses to $OUTPUT_FILE"
echo "Running warm-up request..."
warmup_question="$(first_question)"
warmup_response="$(post_question "$warmup_question")"
append_jsonl 0 "$warmup_question" "$warmup_response"

for ((run = 1; run <= REPEAT_COUNT; run++)); do
  question_number=0
  while IFS= read -r question; do
    if [[ -z "$question" || "$question" =~ ^[[:space:]]*# ]]; then
      continue
    fi

    question_number=$((question_number + 1))
    echo "Running question $question_number/$TOTAL_QUESTIONS run $run/$REPEAT_COUNT"
    response="$(post_question "$question")"
    append_jsonl "$run" "$question" "$response"
  done < "$QUESTIONS_FILE"
done

echo "Benchmark complete."
