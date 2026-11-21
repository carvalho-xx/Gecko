#!/usr/bin/env bash
set -uo pipefail

INPUT="$(cat)"

SHOULD_RUN="$(python3 -c '
import json, sys
try:
    data = json.loads(sys.stdin.read() or "{}")
    if data.get("continuationActive"):
        print("0")
    elif data.get("terminationReason", "NO_TOOL_CALL") != "NO_TOOL_CALL":
        print("0")
    else:
        print("1")
except Exception:
    print("0")
' <<< "$INPUT")"

if [ "$SHOULD_RUN" != "1" ]; then
    printf '{"decision":"allow"}\n'
    exit 0
fi

HAS_KOTLIN_CHANGES="$(cd .. && git status --porcelain -- '*.kt' '*.kts' | wc -l)"
if [ "$HAS_KOTLIN_CHANGES" -eq 0 ]; then
    printf '{"decision":"allow"}\n'
    exit 0
fi

OUTPUT="$(cd .. && ./gradlew ktlintCheck 2>&1)"
STATUS=$?

if [ "$STATUS" -eq 0 ]; then
    printf '{"decision":"allow"}\n'
else
    python3 -c '
import json, sys
msg = sys.stdin.read().strip()
if len(msg) > 1500:
    msg = msg[-1500:]
print(json.dumps({"decision": "continue", "reason": "ktlintCheck falhou:\n" + msg}))
' <<< "$OUTPUT"
fi
