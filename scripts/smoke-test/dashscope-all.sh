#!/usr/bin/env bash
# Aggregator: runs all three DashScope smoke-tests sequentially and
# reports a single PASS/FAIL summary.
# Exit 0 only if all three pass.
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
RC=0

for SCRIPT in dashscope-chat.sh dashscope-vision.sh dashscope-embedding.sh; do
    echo "=== Running $SCRIPT ==="
    if "$HERE/$SCRIPT"; then
        :
    else
        RC=1
    fi
    echo
done

if [ $RC -eq 0 ]; then
    echo "============================="
    echo "ALL DashScope smoke-tests PASS"
    echo "============================="
    echo "Reminder (C-regulatory-002): verify DashScope console"
    echo "'数据改进计划' toggle is OFF before any production demo."
    exit 0
else
    echo "============================="
    echo "DashScope smoke-tests FAIL"
    echo "============================="
    echo "Common causes:"
    echo "  - DASHSCOPE_API_KEY not set or invalid"
    echo "  - ¥50 余额未充值 (R1)"
    echo "  - 网络无法访问 dashscope.aliyuncs.com"
    exit 1
fi
