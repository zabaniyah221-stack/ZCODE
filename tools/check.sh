#!/usr/bin/env bash
# TEST C bisection: check minimal — hanya pytest fase0 (Java Fase 0)
set -e
echo "=== ZCODE Test C (bisection) — check minimal ==="
test -f app/src/main/assets/editor/ace/ace.js && echo "ace.js exists"
grep -q "1.44.0" app/src/main/assets/editor/ace/ace.js && echo "Ace 1.44.0"
python3 -m pytest test_zcode_fase0.py -q
echo "=== Test C check passed ==="
