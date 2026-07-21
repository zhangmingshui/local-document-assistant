#!/usr/bin/env bash
set -euo pipefail

QUESTIONS_FILE="scripts/qa-evel-questions.json"
BASE_URL="${QA_BASE_URL:-http://localhost:8080}"
OUTPUT_ROOT="data/qa-eval"
STARTED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
RUN_TIMESTAMP="$(date -u +"%Y-%m-%dT%H%M%S")"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to run the QA evaluation." >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required to run the QA evaluation." >&2
  exit 1
fi

if [[ ! -f "$QUESTIONS_FILE" ]]; then
  echo "Questions file not found: $QUESTIONS_FILE" >&2
  exit 1
fi

if ! jq -e 'type == "array"' "$QUESTIONS_FILE" >/dev/null 2>&1; then
  echo "Questions file must contain a valid JSON array: $QUESTIONS_FILE" >&2
  exit 1
fi

OUTPUT_DIR="$OUTPUT_ROOT/$RUN_TIMESTAMP"
mkdir -p "$OUTPUT_ROOT"
suffix=1
while ! mkdir "$OUTPUT_DIR" 2>/dev/null; do
  OUTPUT_DIR="$OUTPUT_ROOT/${RUN_TIMESTAMP}-$suffix"
  suffix=$((suffix + 1))
done

JSONL_REPORT="$OUTPUT_DIR/responses.jsonl"
MARKDOWN_REPORT="$OUTPUT_DIR/responses.md"
SEARCH_RESPONSE_FILE="$OUTPUT_DIR/.search-response.tmp"
QA_RESPONSE_FILE="$OUTPUT_DIR/.qa-response.tmp"
trap 'rm -f "$SEARCH_RESPONSE_FILE" "$QA_RESPONSE_FILE"' EXIT

TOTAL_QUESTIONS="$(jq 'length' "$QUESTIONS_FILE")"

cat > "$MARKDOWN_REPORT" <<EOF
# QA Evaluation Run

Backend:
$BASE_URL

Started:
$STARTED_AT
EOF

append_markdown_list() {
  local heading="$1"
  local values_json="$2"

  printf '\n%s:\n' "$heading" >> "$MARKDOWN_REPORT"
  if [[ "$(jq 'length' <<<"$values_json")" -eq 0 ]]; then
    printf -- '- None\n' >> "$MARKDOWN_REPORT"
  else
    jq -r '.[] | "- \(.)"' <<<"$values_json" >> "$MARKDOWN_REPORT"
  fi
}

echo "Running $TOTAL_QUESTIONS QA evaluation questions..."

for ((index = 0; index < TOTAL_QUESTIONS; index++)); do
  item="$(jq -c ".[$index]" "$QUESTIONS_FILE")"
  id="$(jq -r '.id // ""' <<<"$item")"
  question="$(jq -r '.question // ""' <<<"$item")"
  should_answer="$(jq -r '.shouldAnswer // false' <<<"$item")"
  expected_facts="$(jq -c '.expectedFacts // []' <<<"$item")"
  expected_sources="$(jq -c '.expectedSources // []' <<<"$item")"
  search_request_body="$(jq -nc --arg query "$question" '{query: $query, limit: 8}')"
  qa_request_body="$(jq -nc --arg question "$question" '{question: $question}')"

  : > "$SEARCH_RESPONSE_FILE"
  search_curl_result=""
  if search_curl_result="$(curl --silent --show-error --fail-with-body \
    --output "$SEARCH_RESPONSE_FILE" \
    --write-out $'%{http_code}\t%{time_total}' \
    --request POST "$BASE_URL/api/search" \
    --header 'Content-Type: application/json' \
    --data-binary "$search_request_body")"; then
    :
  else
    curl_exit=$?
    search_response_body="$(<"$SEARCH_RESPONSE_FILE")"
    echo "Search request failed for '$id' (curl exit $curl_exit)." >&2
    if [[ -n "$search_curl_result" ]]; then
      echo "HTTP result: $search_curl_result" >&2
    fi
    if [[ -n "$search_response_body" ]]; then
      echo "Response body: $search_response_body" >&2
    fi
    exit "$curl_exit"
  fi

  IFS=$'\t' read -r search_http_status search_elapsed_seconds <<<"$search_curl_result"
  search_elapsed_ms="$(jq -nr --arg seconds "$search_elapsed_seconds" '($seconds | tonumber) * 1000 | round')"
  search_response_body="$(<"$SEARCH_RESPONSE_FILE")"

  if jq -e . "$SEARCH_RESPONSE_FILE" >/dev/null 2>&1; then
    search_candidates="$(jq -c '
      (.results // .matches // .documents // .chunks // .items // [])
      | if type == "array" then . elif . == null then [] else [.] end
      | to_entries
      | map(
          .key as $index
          | .value
          | if type == "object" then
              {
                rank: ($index + 1),
                fileName: (.fileName // null),
                filename: (.filename // null),
                filePath: (.filePath // null),
                path: (.path // null),
                documentName: (.documentName // null),
                title: (.title // null),
                chunkIndex: (.chunkIndex // .chunkNumber // null),
                relevance: (.relevance // null),
                distance: (.distance // null),
                text: (.text // null),
                snippet: (.snippet // null),
                preview: (.preview // null)
              } | with_entries(select(.value != null))
            else {rank: ($index + 1), text: .} end
        )' "$SEARCH_RESPONSE_FILE")"
  else
    search_candidates='[]'
  fi

  : > "$QA_RESPONSE_FILE"
  qa_curl_result=""
  if qa_curl_result="$(curl --silent --show-error --fail-with-body \
    --output "$QA_RESPONSE_FILE" \
    --write-out $'%{http_code}\t%{time_total}' \
    --request POST "$BASE_URL/api/questions" \
    --header 'Content-Type: application/json' \
    --data-binary "$qa_request_body")"; then
    :
  else
    curl_exit=$?
    response_body="$(<"$QA_RESPONSE_FILE")"
    echo "QA request failed for '$id' (curl exit $curl_exit)." >&2
    if [[ -n "$qa_curl_result" ]]; then
      echo "HTTP result: $qa_curl_result" >&2
    fi
    if [[ -n "$response_body" ]]; then
      echo "Response body: $response_body" >&2
    fi
    exit "$curl_exit"
  fi

  IFS=$'\t' read -r qa_http_status qa_elapsed_seconds <<<"$qa_curl_result"
  qa_elapsed_ms="$(jq -nr --arg seconds "$qa_elapsed_seconds" '($seconds | tonumber) * 1000 | round')"
  total_elapsed_ms=$((search_elapsed_ms + qa_elapsed_ms))
  response_body="$(<"$QA_RESPONSE_FILE")"

  if jq -e . "$QA_RESPONSE_FILE" >/dev/null 2>&1; then
    answer="$(jq -r '(.answer // .response // .message // "") | if type == "string" then . else tostring end' "$QA_RESPONSE_FILE")"
    sources="$(jq -c '
      (.sources // .sourceDocuments // .questionSources // [])
      | if type == "array" then . elif . == null then [] else [.] end
      | map(
          if type == "object" then
            {
              fileName: (.fileName // null),
              filename: (.filename // null),
              filePath: (.filePath // null),
              path: (.path // null),
              documentName: (.documentName // null),
              title: (.title // null),
              chunkIndex: (.chunkIndex // .chunkNumber // null),
              relevance: (.relevance // null),
              distance: (.distance // null)
            } | with_entries(select(.value != null))
          else . end
        )' "$QA_RESPONSE_FILE")"
  else
    answer=""
    sources='[]'
  fi

  expected_facts_found="$(jq -nc --arg answer "$answer" --argjson expected "$expected_facts" \
    '$expected | map(. as $needle | select(($answer | ascii_downcase) | contains($needle | ascii_downcase)))')"
  expected_facts_missing="$(jq -nc --arg answer "$answer" --argjson expected "$expected_facts" \
    '$expected | map(. as $needle | select((($answer | ascii_downcase) | contains($needle | ascii_downcase)) | not))')"
  serialized_sources="$(jq -nc --argjson sources "$sources" --argjson candidates "$search_candidates" \
    '{qaSources: $sources, searchCandidates: $candidates}')"
  expected_sources_found="$(jq -nc --arg sources "$serialized_sources" --argjson expected "$expected_sources" \
    '$expected | map(. as $needle | select(($sources | ascii_downcase) | contains($needle | ascii_downcase)))')"
  expected_sources_missing="$(jq -nc --arg sources "$serialized_sources" --argjson expected "$expected_sources" \
    '$expected | map(. as $needle | select((($sources | ascii_downcase) | contains($needle | ascii_downcase)) | not))')"

  jq -nc \
    --arg id "$id" \
    --arg question "$question" \
    --argjson shouldAnswer "$should_answer" \
    --argjson expectedFacts "$expected_facts" \
    --argjson expectedSources "$expected_sources" \
    --argjson searchElapsedMs "$search_elapsed_ms" \
    --argjson qaElapsedMs "$qa_elapsed_ms" \
    --argjson totalElapsedMs "$total_elapsed_ms" \
    --arg searchResponseBody "$search_response_body" \
    --argjson searchCandidates "$search_candidates" \
    --arg responseBody "$response_body" \
    --arg answer "$answer" \
    --argjson sources "$sources" \
    --argjson expectedFactsFound "$expected_facts_found" \
    --argjson expectedFactsMissing "$expected_facts_missing" \
    --argjson expectedSourcesFound "$expected_sources_found" \
    --argjson expectedSourcesMissing "$expected_sources_missing" \
    '{
      id: $id,
      question: $question,
      shouldAnswer: $shouldAnswer,
      expectedFacts: $expectedFacts,
      expectedSources: $expectedSources,
      searchElapsedMs: $searchElapsedMs,
      qaElapsedMs: $qaElapsedMs,
      totalElapsedMs: $totalElapsedMs,
      searchResponseBody: $searchResponseBody,
      searchCandidates: $searchCandidates,
      responseBody: $responseBody,
      answer: $answer,
      sources: $sources,
      expectedFactsFound: $expectedFactsFound,
      expectedFactsMissing: $expectedFactsMissing,
      expectedSourcesFound: $expectedSourcesFound,
      expectedSourcesMissing: $expectedSourcesMissing
    }' >> "$JSONL_REPORT"

  display_answer="$answer"
  if [[ -z "$display_answer" ]]; then
    display_answer="$response_body"
  fi

  {
    printf '\n## %s\n' "$id"
    printf '\nQuestion:\n%s\n' "$question"
    printf '\nShould answer:\n%s\n' "$should_answer"
  } >> "$MARKDOWN_REPORT"
  append_markdown_list "Expected facts" "$expected_facts"
  append_markdown_list "Expected sources" "$expected_sources"
  printf '\nSearch candidates:\n' >> "$MARKDOWN_REPORT"
  if [[ "$(jq 'length' <<<"$search_candidates")" -eq 0 ]]; then
    printf 'None\n' >> "$MARKDOWN_REPORT"
  else
    jq -r '
      .[]
      | ((.text // .snippet // .preview // "") | tostring | gsub("[[:space:]]+"; " ") | .[0:250]) as $preview
      | "\(.rank). fileName=\(.fileName // .filename // .documentName // .title // .filePath // .path // "unknown") chunkIndex=\(.chunkIndex // "unknown") relevance=\(.relevance // "unknown") distance=\(.distance // "unknown")\n   Preview: \(if $preview == "" then "None" else $preview end)"
    ' <<<"$search_candidates" >> "$MARKDOWN_REPORT"
  fi
  printf '\nActual answer:\n%s\n' "$display_answer" >> "$MARKDOWN_REPORT"
  append_markdown_list "Actual sources" "$(jq -c 'map(if type == "string" then . else tojson end)' <<<"$sources")"
  append_markdown_list "Missing expected facts" "$expected_facts_missing"
  append_markdown_list "Missing expected sources" "$expected_sources_missing"
  printf '\nSearch elapsed:\n%s ms\n' "$search_elapsed_ms" >> "$MARKDOWN_REPORT"
  printf '\nQA elapsed:\n%s ms\n' "$qa_elapsed_ms" >> "$MARKDOWN_REPORT"
  printf '\nTotal elapsed:\n%s ms\n' "$total_elapsed_ms" >> "$MARKDOWN_REPORT"

  echo "[$((index + 1))/$TOTAL_QUESTIONS] $id ... search $search_elapsed_ms ms, qa $qa_elapsed_ms ms"
done

echo "QA evaluation complete."
echo "Markdown report: $MARKDOWN_REPORT"
echo "JSONL report:    $JSONL_REPORT"
