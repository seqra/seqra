#!/usr/bin/env bash
# Poll an HTTP URL until it responds; fail and dump the server log on timeout.
# Usage: wait-for-http-server.sh URL [TIMEOUT_SECONDS] [LOG_PATH]
set -euo pipefail

URL="${1:?usage: $0 URL [TIMEOUT_SECONDS] [LOG_PATH]}"
TIMEOUT="${2:-30}"
LOG="${3:-}"

for i in $(seq 1 "$TIMEOUT"); do
  if curl -fsS "$URL" -o /dev/null 2>/dev/null; then
    echo "Server ready after ${i}s"
    exit 0
  fi
  sleep 1
done

echo "Server did not become ready at ${URL} within ${TIMEOUT}s."
if [ -n "$LOG" ] && [ -f "$LOG" ]; then
  echo '--- log ---'
  cat "$LOG"
fi
exit 1
