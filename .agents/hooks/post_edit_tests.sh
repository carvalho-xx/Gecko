#!/usr/bin/env bash
set -uo pipefail

INPUT="$(cat)"

SHOULD_RUN="$(python3 -c '
import json, sys
try:
    data = json.loads(sys.stdin.read() or "{}")
    args = data.get("toolCall", {}).get("args", {})
    path = args.get("Path") or args.get("path") or ""
    if path.endswith((".kt", ".kts")) and ("/app/" in path or path.endswith("build.gradle.kts")):
        print("1")
    else:
        print("0")
except Exception:
    print("0")
' <<< "$INPUT")"

if [ "$SHOULD_RUN" = "1" ]; then
    (cd .. && ./gradlew testDebugUnitTest -x lint >&2) || true
fi

printf '{}\n'
