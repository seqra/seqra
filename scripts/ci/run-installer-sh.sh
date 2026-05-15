#!/usr/bin/env bash
# Run an opentaint POSIX install script and propagate its OPENTAINT_BINARY_PATH
# line to $GITHUB_OUTPUT so a verify step can consume it.
# Usage: run-installer-sh.sh PATH_TO_INSTALL_SCRIPT
set -euo pipefail

SCRIPT="${1:?usage: $0 PATH_TO_INSTALL_SCRIPT}"

OUTPUT=$(bash "$SCRIPT")
echo "$OUTPUT"

BINARY_PATH=$(echo "$OUTPUT" | grep '^OPENTAINT_BINARY_PATH=' | cut -d= -f2-)
if [ -z "$BINARY_PATH" ]; then
  echo "$SCRIPT did not emit OPENTAINT_BINARY_PATH" >&2
  exit 1
fi

echo "OPENTAINT_BINARY_PATH=$BINARY_PATH" >> "$GITHUB_OUTPUT"
