#!/usr/bin/env bash
# Smoke-test: DashScope qwen-max via OpenAI-compatible mode.
# Usage: DASHSCOPE_API_KEY=sk-... ./dashscope-chat.sh
set -euo pipefail

if [ -z "${DASHSCOPE_API_KEY:-}" ]; then
    echo "[chat] FAIL — DASHSCOPE_API_KEY env var is not set"
    exit 1
fi

URL="https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
PAYLOAD='{
  "model": "qwen-max",
  "messages": [
    {"role": "system", "content": "你是简洁助手。"},
    {"role": "user", "content": "请只回复一个字: 好"}
  ],
  "max_tokens": 8
}'

RESP=$(curl -sS -m 30 -X POST "$URL" \
    -H "Authorization: Bearer ${DASHSCOPE_API_KEY}" \
    -H "Content-Type: application/json" \
    -d "$PAYLOAD")

if echo "$RESP" | grep -q '"choices"'; then
    echo "[chat] PASS — qwen-max"
    echo "  preview: $(echo "$RESP" | tr -d '\n' | head -c 200)"
    exit 0
else
    echo "[chat] FAIL — qwen-max"
    echo "  response: $RESP"
    exit 1
fi
