#!/usr/bin/env bash
# Smoke-test: DashScope qwen-vl-max multimodal.
# Uses a 1x1 transparent PNG as the smoke-test image (the goal is just to
# prove the model accepts a multimodal payload — Phase 3 will use real
# 病案首页 page renders).
# Usage: DASHSCOPE_API_KEY=sk-... ./dashscope-vision.sh
set -euo pipefail

if [ -z "${DASHSCOPE_API_KEY:-}" ]; then
    echo "[vision] FAIL — DASHSCOPE_API_KEY env var is not set"
    exit 1
fi

URL="https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"

# 1x1 transparent PNG (base64).
IMG_B64="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="

PAYLOAD=$(cat <<EOF
{
  "model": "qwen-vl-max",
  "input": {
    "messages": [
      {
        "role": "user",
        "content": [
          {"image": "data:image/png;base64,${IMG_B64}"},
          {"text": "图中有什么? 一句话回答即可。"}
        ]
      }
    ]
  },
  "parameters": {
    "max_tokens": 32
  }
}
EOF
)

RESP=$(curl -sS -m 60 -X POST "$URL" \
    -H "Authorization: Bearer ${DASHSCOPE_API_KEY}" \
    -H "Content-Type: application/json" \
    -d "$PAYLOAD")

if echo "$RESP" | grep -Eq '"output"|"choices"'; then
    echo "[vision] PASS — qwen-vl-max"
    echo "  preview: $(echo "$RESP" | tr -d '\n' | head -c 200)"
    exit 0
else
    echo "[vision] FAIL — qwen-vl-max"
    echo "  response: $RESP"
    exit 1
fi
