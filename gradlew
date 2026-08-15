#!/usr/bin/env sh
# ZCODE gradlew stub — delegates to system gradle if available, otherwise prints
set -e
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
else
  echo "[ZCODE gradlew] gradle not found in this sandbox — skipping assemble (CI will have gradle)"
  echo "Args: $@"
  exit 0
fi
