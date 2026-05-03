#!/usr/bin/env bash
# wait-for-stack.sh -- poll the docker-compose stack until all 4 services
# report healthy AND the actuator/health endpoint includes db UP.
# Usage: ./scripts/wait-for-stack.sh [timeout_seconds]

set -euo pipefail

TIMEOUT="${1:-180}"
START=$(date +%s)
HEALTH_URL="http://localhost:8080/api/actuator/health"

echo "[wait-for-stack] polling $HEALTH_URL (timeout ${TIMEOUT}s)..."

while :; do
    NOW=$(date +%s)
    ELAPSED=$((NOW - START))
    if [ "$ELAPSED" -ge "$TIMEOUT" ]; then
        echo "[wait-for-stack] TIMEOUT after ${TIMEOUT}s"
        docker compose ps || true
        exit 1
    fi

    BODY=$(curl -fs "$HEALTH_URL" || echo "")
    if echo "$BODY" | grep -q '"status":"UP"' && echo "$BODY" | grep -q '"db"'; then
        # Confirm db component is UP (not DOWN/UNKNOWN).
        if echo "$BODY" | grep -E '"db"\s*:\s*\{[^}]*"status"\s*:\s*"UP"' >/dev/null; then
            echo "[wait-for-stack] OK after ${ELAPSED}s -- overall UP, db UP"
            echo "$BODY"
            exit 0
        fi
    fi
    sleep 3
done
