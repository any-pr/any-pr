#!/usr/bin/env bash
# M54 gate — a NeoForge mod's play-phase packet to the server is heard: Carry On picks a chest up and puts it back,
# then a pig, driven through the game's own keyboard and mouse.
#
# WHY THIS EXISTS. The merged ServerGamePacketListenerImpl.handleCustomPayload is MinecraftForge's override: it asks
# ForgeHooks.onCustomPayload, drops the answer and returns, and never reaches NeoForge's dispatcher. Carry On tells
# the server "the carry key is held" with exactly such a packet, so the server never believed the key was down and
# a sneak-right-click on a chest opened the chest instead. No crash, no log, compatibility report OK: a player's
# "Carry On does not work". The same drop silenced every NeoForge mod's GUI button and keybind sent upward.
#
# WHY fabric-api IS IN THE PACK. The first repair fell through to super, where fabric-api injects a handler meant for
# the configuration listener alone; on the play listener it throws "Unknown addon" and disconnects the player the
# moment Carry On sends its key. Without fabric-api in the pack that version passes this gate.
#
# WHY THE VERDICT IS THE WORLD. The drill (KernelClientSmoke, -Dforbric.clientSmokeCarry) presses LEFT_SHIFT through
# KeyboardHandler.keyPress and the right button through MouseHandler.onButton, then reads on the SERVER whether the
# chest left its block and came back with its 7 diamonds, and whether the pig left and came back. Carry On's own
# carry data is logged beside that as evidence of which link broke; nothing Forbric says about itself is asserted.
#
# THE NEGATIVE CONTROL. The same run with -Dforbric.playPayloadFallThrough=off must fail to pick anything up — a gate
# that cannot go red without the repair is not measuring it.
#
# Needs: carryon-neoforge-26.2-2.11.2.jar (M54_CARRYON, default run/carry-on/) and a fabric-api jar (M54_FABRIC_API,
# default the client-merged-pack's). Both are third-party jars this tree does not ship.
# GATE-PARALLEL: rundirs=client-carry-m54 mem=3000
set -uo pipefail
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"

RUNDIR="$KERNEL/run/client-carry-m54"
WORLD="ForbricTest"
FIXTURE="${M54_WORLD_FIXTURE:-$KERNEL/run/client-merged-pack/saves/$WORLD}"
CARRYON="${M54_CARRYON:-$KERNEL/run/carry-on/carryon-neoforge-26.2-2.11.2.jar}"
FABRIC_API="${M54_FABRIC_API:-$(ls "$KERNEL"/run/client-merged-pack/mods/fabric-api-*.jar 2>/dev/null | head -1)}"
AT="${M54_AT:-100}"
mkdir -p "$BUILD"

for need in "$CARRYON" "$FABRIC_API"; do
  [ -f "$need" ] || { echo "[kernel] SKIP-FATAL: M54 needs $need (set M54_CARRYON / M54_FABRIC_API)" >&2; exit 3; }
done
[ -d "$FIXTURE" ] || { echo "[kernel] SKIP-FATAL: no world fixture at $FIXTURE (set M54_WORLD_FIXTURE)" >&2; exit 3; }

kernel_jar

# carry_run <label> <extra-jvm> — a fresh world and the drill, one client; the log is $BUILD/gate-m54-<label>.log.
carry_run() {
  local label="$1" extra="$2" log="$BUILD/gate-m54-$1.log"
  reap_stale_server "$RUNDIR"
  # The drill builds its stage in the world, so every run starts from the fixture rather than the last run's save.
  rm -rf "$RUNDIR"
  mkdir -p "$RUNDIR/mods" "$RUNDIR/saves" "$RUNDIR/quickPlay"
  cp "$CARRYON" "$FABRIC_API" "$RUNDIR/mods/"
  cp -R "$FIXTURE" "$RUNDIR/saves/"
  # Its own options rather than the fixture pack's: default key bindings (sneak and Carry On's key both on LEFT_SHIFT,
  # as a player who never opened the controls screen has them), and no pause when the unattended window loses focus.
  printf 'version:4903\nonboardAccessibility:false\npauseOnLostFocus:false\ntutorialStep:none\nlang:en_us\n' \
    > "$RUNDIR/options.txt"
  : > "$log"
  FORBRIC_JVM="-Dforbric.clientSmoke=true -Dforbric.clientSmokeWorld=$WORLD -Dforbric.clientSmokeReadyTicks=60 \
-Dforbric.clientSmokeCarry=$AT -Dforbric.clientSmokeDisconnectTicks=$((AT + 260)) $extra ${M54_EXTRA_JVM:-}" \
  RUNDIR="$RUNDIR" "$KERNEL/run/launch-kernel-client.sh" \
    --quickPlayPath "$RUNDIR/quickPlay/log.json" --quickPlaySingleplayer "$WORLD" > "$log" 2>&1 &
  local pid=$!
  record_server_pid "$RUNDIR" "$pid"
  echo "[kernel] client pid=$pid (this gate never kills by name — another client may be running)"
  await_server "$pid" "$log" 320
}

verdict() { grep -aoE 'carry drill result: .*' "$1" | head -1; }

step "the drill with the repair (must PASS)"
carry_run repaired ""
LOG="$BUILD/gate-m54-repaired.log"
RESULT="$(verdict "$LOG")"
check "the drill ran to its verdict" "carry drill result" "$LOG"
for want in 'chest picked up=true' 'chest put back=true' 'with its contents=true' 'pig picked up=true' 'pig put back=true'; do
  case "$RESULT" in
    *"$want"*) echo "[kernel] PASS $want" ;;
    *) echo "[kernel] FAIL $want — $RESULT"; FAIL=1 ;;
  esac
done
check_absent "nobody was disconnected (fabric-api's \"Unknown addon\", NeoForge's \"No Channel\")" \
  "Unknown addon|lost connection: Internal Exception|No Channel for" "$LOG" '\[Forbric/'
check_absent "no crash report" "Preparing crash report" "$LOG"
check "the client left the world cleanly" "clean disconnect observed" "$LOG"

step "the same drill with the repair switched off (must be RED)"
carry_run control "-Dforbric.playPayloadFallThrough=off"
CONTROL="$(verdict "$BUILD/gate-m54-control.log")"
case "$CONTROL" in
  *'chest picked up=false'*'pig picked up=false'*) echo "[kernel] PASS the control picks nothing up — the gate measures the repair" ;;
  *) echo "[kernel] FAIL the control did not go red, so the gate does not measure the repair: ${CONTROL:-<no verdict>}"; FAIL=1 ;;
esac

step "M54 result"
if [ "$FAIL" -eq 0 ]; then
  echo "[kernel] ✅ M54 CARRY GATE GREEN — Carry On's key reached the server: a chest and a pig carried and put back"
else
  echo "[kernel] ❌ M54 GATE RED — see $BUILD/gate-m54-repaired.log and $BUILD/gate-m54-control.log"
fi
exit "$FAIL"
