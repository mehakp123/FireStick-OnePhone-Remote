#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

python3 tools/static_project_check.py
python3 tools/mock_protocol_matrix.py

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
kotlinc \
  app/src/main/java/com/pannu/firestickremote/core/FireTvModel.kt \
  app/src/main/java/com/pannu/firestickremote/core/FireTvEndpoints.kt \
  app/src/main/java/com/pannu/firestickremote/core/IpTools.kt \
  tools/CoreLogicSelfTest.kt \
  -include-runtime -d "$TMP/core-tests.jar"
java -jar "$TMP/core-tests.jar"

echo "ALL AVAILABLE LOCAL SELF-TESTS PASSED"
