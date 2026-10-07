#!/usr/bin/env bash
# Builds the two metadata-only jars gate-m24b-badmetadata.sh stages: one whose only manifest cannot be read, and a
# universal one whose neoforge.mods.toml cannot be read but whose fabric.mod.json can. No classes — a manifest is
# all it takes: one such jar used to stop a 130-jar pack at discovery.
#
# Output: run/canary/forbricbadmetaneo.jar, run/canary/forbricbadmetamulti.jar
set -uo pipefail
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"

SRC="$KERNEL/canary/broken-metadata"
OUT="$KERNEL/run/canary"
mkdir -p "$BUILD" "$OUT"

step "zip the metadata-only canaries"
for pair in "neo-only:forbricbadmetaneo" "multi:forbricbadmetamulti"; do
  dir="${pair%%:*}"; name="${pair##*:}"
  (cd "$SRC/$dir" && jar --create --file "$BUILD/$name.$$.jar" .) || { echo "[kernel] FAIL could not zip $name"; exit 1; }
  publish_canary "$BUILD/$name.$$.jar" "$OUT/$name.jar" || exit 1
  echo "[kernel] built $OUT/$name.jar"
done
