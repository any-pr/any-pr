#!/usr/bin/env bash
# M9 gate — the CLIENT half. A tri-ecosystem instance must reach a rendered world and leave it cleanly.
#
# Every client fix in this kernel was verified by launching the game and reading the log by hand, so none of them
# was protected against the next change. This gate is that protection: it drives a real client into a real world
# with -Dforbric.clientSmoke, then asserts the absence of each failure that has actually cost a world load here.
# Those check_absent lines are the point of the gate — they are a list of bugs, each one paid for.
#
# WHY IT KILLS BY PID. A developer (or a second agent session) may have their own Minecraft client open, and a
# name-matched kill would take it down with no warning and no way to tell whose it was. This gate kills the
# process tree it started and nothing else. The server gates now do the same, via await_server in lib.sh.
#
# The window between disconnect and exit is deliberate. Vanilla's own watchdog logs "Client shutdown from
# post-main" ~15s after main returns if a non-daemon thread is still alive, which is how a leaked mod thread
# announces itself — so the gate waits for the process to end on its own rather than killing it at the disconnect.
# GATE-PARALLEL: clone=client-merged-pack:M9_RUNDIR mem=3000
set -uo pipefail
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"

RUNDIR="${M9_RUNDIR:-$KERNEL/run/client-merged-pack}"
WORLD="${M9_WORLD:-ForbricTest}"
LOG="$BUILD/gate-m9-client-boot.log"
COMPAT_STARTED_NS="$(python3 -c 'import time; print(time.time_ns())')"
mkdir -p "$BUILD"

if [ ! -d "$RUNDIR/saves/$WORLD" ]; then
  echo "[kernel] SKIP-FATAL: no world at $RUNDIR/saves/$WORLD — this gate needs a pre-generated save" >&2
  exit 3
fi
if [ ! -f "$RUNDIR/options.txt" ]; then
  # Without it the accessibility onboarding screen sits in front of --quickPlaySingleplayer and nothing ever loads.
  echo "[kernel] SKIP-FATAL: no $RUNDIR/options.txt — quick-play would be blocked by the onboarding screen" >&2
  exit 3
fi

# SEED A MODIFIER BINDING, every run. MinecraftForge writes a modified key as
# `key_key.jei.toggleOverlay:key.keyboard.o:CONTROL_OR_COMMAND` and reads it back through vanilla's
# InputConstants.getKey, which throws on the suffix; Options.load wraps the whole file, so the player loses every
# setting AND the client then SAVES the defaults over the file. That last part is why this has to be re-seeded:
# this fixture carried three of JEI's and lost them exactly that way, taking the evidence with them.
# M9_KEY_MODIFIER_SEED_BEGIN — the contract test runs this exact step against a fixture options.txt.
python3 - "$RUNDIR/options.txt" <<'PY_SEED' || exit 3
from pathlib import Path
import sys
options = Path(sys.argv[1])
lines = options.read_text(encoding='utf-8').splitlines()
for i, line in enumerate(lines):
    if not line.startswith('key_key.') or ':' not in line:
        continue
    name, _, value = line.partition(':')
    if value.endswith(':CONTROL_OR_COMMAND'):
        break
    lines[i] = f'{name}:{value}:CONTROL_OR_COMMAND'
    options.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(f'[kernel] seeded a modifier binding: {lines[i]}')
    break
else:
    raise SystemExit('no key_key.* binding in options.txt to give a modifier to')
PY_SEED
# M9_KEY_MODIFIER_SEED_END

kernel_jar
mkdir -p "$RUNDIR/quickPlay"
rm -f "$RUNDIR/logs/latest.log"
: > "$LOG"

step "launch the client into $WORLD via quick-play ($(ls -1 "$RUNDIR/mods"/*.jar 2>/dev/null | wc -l | tr -d ' ') mods, no compatibility flags)"
# M9_EXTRA_JVM is how the gate's teeth are demonstrated: switch a fix off and this must go RED. Also verified:
#   -Dforbric.mipmapLowering=off   -> 1 red ("an atlas may lower its mip level again")
#   -Dforbric.keyModifierSuffix=off -> 2 red ("the key-modifier suffix is dropped before the name is parsed",
#                                             "options.txt loads with modded modifier bindings in it")
#   -Dforbric.carrierLanguages=off -> 2 red ("NeoForge's own screens have their text", "and MinecraftForge's do too")
#   -Dforbric.blockStateCaches=off -> 1 red ("every block state's cache is computed"). Off, a block a mod
#                                      registered carries an uninitialised cache all run. Vanilla computes it
#                                      lazily, so this is a hot-path repair, not a crash repair — the Lithium
#                                      crash it was once credited with is forbric.blockInfoCaches, below.
#   -Dforbric.blockInfoCaches=off  -> 1 red ("a mod's whole-registry block pass covers the late wave too"). Off,
#                                      every block the kernel registers after Lithium's one pass (fired from
#                                      FuelValues.vanillaBurnTimes) misses it, and Lithium throws rather than
#                                      computing a missed state's flags later: verified on Windows as "Could not
#                                      initialize block state flags for Block{biomesoplenty:fir_leaves}" during
#                                      feature placement. The blockstate→id map half is M26's.
#   -Dforbric.splitterPacketContext=off -> 2 red ("NeoForge's splitter encodes in Fabric's packet context",
#                                      and the anchor census noticing a repair that was handed its target and
#                                      declined — which is the switch working, said twice).
#                                      Off, a Fabric codec reading PacketContext.get() from inside NeoForge's
#                                      splitter sees null; with Polymer in the pack that is update_recipes
#                                      failing to encode and the client disconnected at world join.
#   -Dforbric.itemTooltipBridge=off -> 1 red ("a NeoForge mod can add a line to an item's tooltip"). Off, the
#                                      merged getTooltipLines posts only MinecraftForge's event and every
#                                      NeoForge mod's tooltip line goes into a list nobody built.
#   -Dforbric.fabricMainInConstructor=off -> 2 red ("Fabric main entrypoints run where Fabric runs them",
#                                                   "and not in the pre-Minecraft window"). Off, a Fabric mod that
#                                                   caches Minecraft.getInstance() from onInitialize caches null:
#                                                   ClickCrystals then killed the client inside Minecraft.<init>.
# Verified with
# -Dforbric.pruneDuplicateLambdas=off, which brings back StubException and the failed world load.
FORBRIC_JVM="-Dforbric.clientSmoke=true -Dforbric.clientSmokeWorld=$WORLD -Dforbric.clientSmokeReadyTicks=60 -Dforbric.clientSmokeModsScreen=80 -Dforbric.clientSmokeKeyBinds=70 -Dforbric.clientSmokeDisconnectTicks=140 ${M9_EXTRA_JVM:-}" \
RUNDIR="$RUNDIR" "$KERNEL/run/launch-kernel-client.sh" \
  --quickPlayPath "$RUNDIR/quickPlay/log.json" --quickPlaySingleplayer "$WORLD" > "$LOG" 2>&1 &
CLIENT_PID=$!
echo "[kernel] client pid=$CLIENT_PID (this gate never kills by name — another client may be running)"

# The game log is the one with the mod chatter in it; the launcher log carries the JVM's own output.
GAMELOG="$RUNDIR/logs/latest.log"
for i in $(seq 1 400); do
  kill -0 "$CLIENT_PID" 2>/dev/null || { echo "[kernel] client exited on its own after ~${i}s"; break; }
  grep -qE 'ClientSmoke\] clean disconnect observed|Game crashed|Mod Loading has failed|Failed to load level data|Network Protocol Error' \
       "$GAMELOG" 2>/dev/null && { echo "[kernel] outcome reached after ~${i}s"; break; }
  sleep 1
done

step "let vanilla's post-main watchdog speak before killing anything"
for i in $(seq 1 25); do
  kill -0 "$CLIENT_PID" 2>/dev/null || break
  sleep 1
done

# ONLY this gate's own process tree.
for pid in $(pgrep -P "$CLIENT_PID" 2>/dev/null) "$CLIENT_PID"; do kill "$pid" 2>/dev/null; done
sleep 2
for pid in $(pgrep -P "$CLIENT_PID" 2>/dev/null) "$CLIENT_PID"; do kill -9 "$pid" 2>/dev/null; done

# The launcher log already carries the console appender, so this mostly duplicates it — deliberately, because
# the file appender is the only place some detail lands. Duplication cannot change a verdict: check is >=1
# and check_absent is ==0, so the counts printed below may read double and mean nothing by it.
cat "$GAMELOG" >> "$LOG" 2>/dev/null || true

step "the client entered a world and left it cleanly (must PASS)"
check "smoke controller armed"        "ClientSmoke\] armed on Minecraft.tick"      "$LOG"
check "joined a world"                "ClientSmoke\] joined world via quick-play"  "$LOG"
check "survived real simulation"      "ClientSmoke\] client-ready after"           "$LOG"

# The anchor census on the side that has the most repairs to lose. It must FIRE -- a census that never ran looks
# exactly like a clean one -- and nothing may have been handed its target class and declined it. Every miss here
# is a feature gone with no other symptom, which is how four of them arrived together with a carrier upgrade.
check        "the anchor census ran"        "Forbric/Anchor\] [0-9]+ of [1-9][0-9]* declared repair" "$LOG"
check_absent "every declared repair landed" "Forbric/Anchor\] [0-9]+ of [0-9]+ declared repair\(s\) landed, and" "$LOG"
check_absent "no repair was handed its target and declined" "Forbric/Anchor\] .* made no edit" "$LOG"
# J12: every AT line and access-widener entry is judged against the class it was applied to. Measured on this
# pack: 23 matched nothing — 16 AT lines naming members this Minecraft does not have at all (journeymap's
# SRG-named fields and 1.x members), 6 AT methods whose name is there under another descriptor (an overload this
# Minecraft lacks or a merge re-typing — not judged: bagus_lib's Model.animate, YACL's and Jade's constructors,
# sophisticatedcore's recipe builders), all of which a native loader ignores the same and which mark nobody — and
# The featuresPerStep request initially misses before COREMOD restores its descriptor. The access-only replay
# now applies the missed directive to the actual restored member before Mixin; require that evidence and no
# remaining ecosystem re-typing, rather than pinning the former unresolved diagnostic as a success.
check        "the access census ran"               "Forbric/Access\] [0-9]+ directive\(s\) matched nothing across [1-9][0-9]* transformed class" "$LOG"
check        "no directive remains re-typed by an ecosystem" "Forbric/Access\] [0-9]+ directive\(s\) matched nothing.*: 0 re-typed by an ecosystem" "$LOG"
check        "access rules reached restored members" "Forbric/Access\] replayed [1-9][0-9]* previously unmatched directive" "$LOG"
check "the window title was read"      "ClientSmoke\] window title: Minecraft"     "$LOG"
check_absent "…and it names no single loader" "ClientSmoke\] window title: .*(NeoForge|Forge|Fabric)" "$LOG"
check "left the world cleanly"        "ClientSmoke\] clean disconnect observed"    "$LOG"
check "server side really ran"        "joined the game"                            "$LOG"
check "datapacks fully loaded"        "Loaded [1-9][0-9]* advancements"                 "$LOG"

step "the merge did not leave one ecosystem's opt-out binding the other two (client-fatal both times)"
# VANILLA lowers an atlas's mip level to fit its smallest sprite. MinecraftForge patches SpriteLoader to gate that
# on ForgeConfig.CLIENT.allowMipmapLowering(), default FALSE; the byte merge kept that half. The Logistics mod
# (NeoForge) has an 8x8 sprite in its own atlas, so the GPU refused the upload, the FIRST resource reload died,
# Minecraft dropped every pack, reloaded into the same failure -- and the client rendered a BLACK SCREEN for the
# rest of the run with no crash report and no further log line. That is the worst report shape there is.
check "an atlas may lower its mip level again" \
  'Forbric/MergedBaseCompat\] SpriteLoader lowers an atlas' "$LOG"
check_absent "no resource reload was abandoned" 'Caught error loading resourcepacks' "$LOG"
check_absent "no atlas was refused by the GPU" 'mipLevels must be at most' "$LOG"
# MinecraftForge writes a modified binding as key.keyboard.o:CONTROL_OR_COMMAND and then hands that whole string
# to InputConstants.getKey before splitting the modifier off, so vanilla's Integer.parseInt throws. Options.load
# wraps the WHOLE file in one try/catch: one modded binding costs the player every setting. This gate's own
# fixture has had three of them (JEI's) and lost its options on every run, silently, for as long as it existed.
check "the key-modifier suffix is dropped before the name is parsed" \
  'Forbric/MergedBaseCompat\] InputConstants.getKey now drops' "$LOG"
check_absent "options.txt loads with modded modifier bindings in it" 'Failed to load options' "$LOG"

step "the full FML mod lifecycle ran, not just the phases the kernel used to know about"
# Each of these was missing outright until the kernel started mirroring CommonModLoader.load's task order.
check "construct phase posted"        "posted FML construct to [1-9][0-9]* NeoForge mod"      "$LOG"
# A1: mods were constructed in jar-file-name order, which is not an order. A mod whose jar sorts before a library
# it requires ran first and called that library before it had initialised — the error then comes out of the
# library, blamed on the library. Two real dependency pairs from this pack, each with the library's name sorting
# AFTER its user, so alphabetical order gets both of them wrong.
check "construction is in dependency order" \
  "Forbric/Order\] construction order is dependency order" "$LOG"
# Fabric mods are NOT in dependency order, because Fabric Loader has none: it sorts its resolved set by mod id and
# hands every entrypoint key back in that order, and Fabric mods are written against it. Pets Mod's JOIN listener
# throws in every singleplayer world and fabric-api's invoker does not catch per listener, so each listener
# registered after it is skipped. Natively that spares bclib and OptiGUI, whose ids sort first. In dependency order
# both came after Pets Mod and lost their join handlers. The line is written only once the reorder has happened.
check "Fabric mods initialise in Fabric Loader's order (by mod id)" \
  "Forbric/Order\] [1-9][0-9]* Fabric mod\(s\) initialise in Fabric Loader.s order, by mod id" "$LOG"
for PAIR in "balm:cookingforblockheads" "creativecore:ambientsounds"; do
  LIB="${PAIR%%:*}"; USER_MOD="${PAIR##*:}"
  LIB_AT=$(grep -nE "constructed @Mod $LIB " "$LOG" | head -1 | cut -d: -f1)
  USER_AT=$(grep -nE "constructed @Mod $USER_MOD " "$LOG" | head -1 | cut -d: -f1)
  if [ -n "$LIB_AT" ] && [ -n "$USER_AT" ] && [ "$LIB_AT" -lt "$USER_AT" ]; then
    printf '[kernel] PASS %s is constructed before %s (line %s < %s)\n' "$LIB" "$USER_MOD" "$LIB_AT" "$USER_AT"
  else
    printf '[kernel] FAIL %s is constructed before %s (lib=%s user=%s)\n' "$LIB" "$USER_MOD" "${LIB_AT:-none}" "${USER_AT:-none}"; FAIL=1
  fi
done
check "client setup posted"           "posted FML client setup to [1-9][0-9]* NeoForge mod"   "$LOG"
# B3: common setup used to be posted from the kernel's pre-Minecraft window, on the main thread, with
# Minecraft.getInstance() still null — and common setup is exactly where a mod does its dist-guarded client
# initialisation (caching that singleton into a static, or handing work to its executor). Genuine NeoForge posts
# it from inside Minecraft's own constructor. The THREAD is the evidence: before the fix this line said [main].
check "common setup posted inside Minecraft's constructor" \
  "\[Render thread/INFO\]: \[Forbric/Lifecycle\] posted FML common setup to [1-9][0-9]* NeoForge mod" "$LOG"
check_absent "and not from the window before it exists" \
  "\[main/INFO\]: \[Forbric/Lifecycle\] posted FML common setup" "$LOG"
COMMON_AT=$(grep -nE "posted FML common setup to" "$LOG" | head -1 | cut -d: -f1)
CLIENT_AT=$(grep -nE "posted FML client setup to" "$LOG" | head -1 | cut -d: -f1)
if [ -n "$COMMON_AT" ] && [ -n "$CLIENT_AT" ] && [ "$COMMON_AT" -lt "$CLIENT_AT" ]; then
  printf '[kernel] PASS common setup precedes the sided phase (line %s < %s)\n' "$COMMON_AT" "$CLIENT_AT"
else
  printf '[kernel] FAIL common setup precedes the sided phase (common=%s client=%s)\n' "${COMMON_AT:-none}" "${CLIENT_AT:-none}"; FAIL=1
fi
# With a data map count: NeoForge registers eleven types of its own, so a run whose count is zero or unreadable
# has no proof its data maps exist (the kernel words a zero-count run so that it cannot match this line either).
check "registration events ran"       "ran NeoForge.s registration events.* [1-9][0-9]* data map type" "$LOG"
check "IMC enqueued and processed"    "posted FML IMC (enqueue|process) to [1-9][0-9]* NeoForge mod" "$LOG" 2
check "load complete posted"          "posted FML load complete to [1-9][0-9]* NeoForge mod"  "$LOG"
check_absent "no mod failed a phase"  "failed during (construct|IMC enqueue|IMC process)" "$LOG"

# The client half of the Neo->Forge bridge inventory. This one bridge carries every MinecraftForge mod's client
# reload listeners -- GeckoLib's whole model and animation cache hangs off it -- and it used to be reported only
# by an unasserted "installed the ... bridge" line.
check "the client-side bridge pass is complete" "all 3 CLIENT_MOD_BUS bridge\(s\) installed" "$LOG"
# By "complete", not by count -- see gate-m4 for why. The client ticks are their own pass because they name
# NeoForge's client event package, which a dedicated server must never resolve.
check "the game-bus bridge pass is complete too" "EventMux\] all [0-9][0-9]* GAME_BUS bridge\(s\) installed"      "$LOG"
check "the client game-bus bridges went on too" "EventMux\] all [0-9][0-9]* CLIENT_GAME_BUS bridge\(s\) installed" "$LOG"
# The two transformer-landed passes (Phase 1 A): Forge's client registration hooks inside Minecraft.<init> and the
# block-colour table, and the creative-tab / spawn-placement hooks. Verified by count so a repair that stood down on
# an unexpected base is named, not silently absent.
check "the client initialization bridges landed" "EventMux\] all 3 CLIENT_INIT bridge\(s\) installed"  "$LOG"
check "the registration bridges landed"          "EventMux\] all 2 REGISTRATION bridge\(s\) installed" "$LOG"
check_absent "no bridge reported missing"       "bridge\(s\) MISSING"                        "$LOG"
# F2: fabric-model-loading-api-v1's ModelManagerMixin is TRIMMED to the eight injectors that fit the merged
# ModelManager instead of pinned whole, so Fabric ModelLoadingPlugins dispatch. RED with
# M9_EXTRA_JVM=-Dforbric.guestInjectorPruner=off (the pin returns and the 'pruned' line is absent). The
# check_absent is the missingno regression guard: half-applied, all 4666 block models died on this parse error
# and the world rendered as the checkerboard with no other symptom.
check "ModelManagerMixin trimmed, not pinned" "GuestInjectorPruner\] pruned 2 injector\(s\) from .*ModelManagerMixin" "$LOG"
check_absent "block models still parse"       "JSON data was null or empty"                "$LOG"
# G1: an injector bound by explicit descriptor to a merge-added DELEGATING STUB (NeoForge moved the body of
# SimpleContainer.setItem(int,ItemStack) into a 3-arg overload) is rebound to the delegate, so fabric-transfer's
# setChanged suppression applies again instead of reading PARTIAL. MixinStubRebind makes the move (setItem is a
# carrier-stubs.txt row, and MixinFit judges it where it lands); with it off, MixinRetarget's renamed-body rule makes
# the same one. RED with M9_EXTRA_JVM="-Dforbric.mixinRetarget=off -Dforbric.mixinStubRebind=off" (no move line, and
# the 'applies only partially' lines return).
check "fabric-transfer's SimpleContainer suppression rebound" "Forbric/Mixin\] (retargeted guest mixin fabric-transfer-api-v1 .*SimpleContainerMixin .*setItem\(ILnet/minecraft/world/item/ItemStack;\)V → setItem\(ILnet/minecraft/world/item/ItemStack;Z\)V.*PARTIAL→FIT|net.fabricmc.fabric.mixin.transfer.SimpleContainerMixin: fabric_redirectChanged now targets net.minecraft.world.SimpleContainer.setItem\(ILnet/minecraft/world/item/ItemStack;Z\)V)" "$LOG"
check "…and its BaseContainerBlockEntity twin"      "Forbric/Mixin\] (retargeted guest mixin fabric-transfer-api-v1 .*BaseContainerBlockEntityMixin .*PARTIAL→FIT|net.fabricmc.fabric.mixin.transfer.BaseContainerBlockEntityMixin: fabric_redirectSetChanged now targets net.minecraft.world.level.block.entity.BaseContainerBlockEntity.setItem\(ILnet/minecraft/world/item/ItemStack;Z\)V)" "$LOG"
check_absent "SimpleContainerMixin no longer half-applied" "SimpleContainerMixin applies only partially" "$LOG"
check_absent "BaseContainerBlockEntityMixin no longer half-applied" "BaseContainerBlockEntityMixin applies only partially" "$LOG"
# G3: NeoForge's 12-arg Snippet constructor made MixinExtras reject fabric-rendering-v1's 11-arg wrap whole
# ('has an invalid signature'); buildSnippet now constructs through the vanilla-shaped constructor with the
# stencil test carried by a kernel scope. RED with M9_EXTRA_JVM=-Dforbric.snippetFunnel=off (the 'routed' line
# is absent and the invalid-signature apply failure returns).
check "the snippet call site was funnelled"   "SnippetFunnel\] routed 1 RenderPipeline\\\$Builder.buildSnippet" "$LOG"
check_absent "fabric-rendering-v1's snippet wrap matches the constructor" "RenderPipelineBuilderMixin.*has an invalid signature|Found unexpected argument type com.llamalad7.mixinextras.injector.wrapoperation.Operation" "$LOG"
check_absent "RenderPipelineBuilderMixin is not half-applied either" "RenderPipelineBuilderMixin applies only partially" "$LOG"
# G4: NeoForge swapped BlockState.isAir() for its overridable isEmpty() in LevelChunkSection; fabric-block-api's
# redirect handler IS NeoForge's default isEmpty predicate, so the @At follows the swap (a KNOWN row, census-pinned).
# RED with M9_EXTRA_JVM=-Dforbric.mixinRetarget=off (shared with G1).
check "fabric-block-api's isAir redirect rebound to isEmpty" "Forbric/Mixin\] retargeted guest mixin fabric-block-api-v1 .*LevelChunkSectionMixin .*isAir → isEmpty.*PARTIAL→FIT" "$LOG"
check "…and the block-counter twin"                   "Forbric/Mixin\] retargeted guest mixin fabric-block-api-v1 .*ChunkSectionBlockStateCounterMixin .*isAir → isEmpty.*PARTIAL→FIT" "$LOG"
check_absent "LevelChunkSectionMixin no longer half-applied" "LevelChunkSectionMixin applies only partially" "$LOG"
# G5: the merge re-typed AttributeSupplier$Builder.builder (ImmutableMap.Builder → Map) and widened the two ranged
# goals' `mob` (Monster → Mob); a vanilla-descriptor twin now sits beside each, so fabric-object-builder's
# @Accessor binds instead of InvalidAccessorException on every boot. The goals are only loaded when a ranged mob
# spawns, so only the builder is asserted. RED with M9_EXTRA_JVM=-Dforbric.widenedFieldTwins=off.
check "the attribute builder got its vanilla-typed twin" "WidenedFields\] net.minecraft.world.entity.ai.attributes.AttributeSupplier\\\$Builder: vanilla-descriptor twin" "$LOG"
check_absent "fabric-object-builder's attribute accessor binds" "InvalidAccessorException.*builder:Lcom/google/common/collect/ImmutableMap\\\$Builder;" "$LOG"
check_absent "…and the kernel reports no unbound accessor for it" "guest accessor mixin .*AttributeSupplierBuilderAccessor cannot bind" "$LOG"
# G6: ItemStack.addDetailsToTooltip is scrapeable again (vanilla's component order copied to its head from the
# merge's own renamed body). RED with M9_EXTRA_JVM=-Dforbric.tooltipOrderScrape=off.
check "vanilla's tooltip component order restored" "TooltipOrder\] restored a scrapeable vanilla component order of [2-9][0-9] type" "$LOG"
# G7: no mixin in this pack lands on a renumbered vanilla anonymous class (chat_heads' ChatComponent$1 is
# capture-only and must NOT be named). No RED demonstration is possible here — no staged mixin targets a
# relocated name; the unit test carries the mechanism. This pins today's state.
check_absent "no pack mixin lands on a renumbered anonymous class" "targets .* a renumbered anonymous class" "$LOG"
# G8: every installed jar is scanned for reads of a vanilla field the merge re-typed (KeyMapping.MAP as a Map,
# WeightedList$Builder.result as an ImmutableList.Builder). The count line always prints; the pack's readers are
# NeoForge builds compiled against the lookup descriptor, so the finding is 0. RED (line absent) with
# M9_EXTRA_JVM=-Dforbric.fieldDriftAudit=off.
check "field-drift audit ran over the whole pack" "Forbric/FieldDrift\] scanned [0-9][0-9]+ jar\(s\): [0-9]+ reference" "$LOG"
check_absent "no pack jar reads a re-typed vanilla field" "Forbric/FieldDrift\] .* reads .* \(cost" "$LOG"
# H5 (the FluidRenderer.tesselate funnel for MinecraftForge fluid models) is asserted in gate-m26, not here: this
# pack carries sodium, which replaces vanilla's chunk and fluid meshing, so the vanilla funnel is never reached.

step "a Forge-family mod's own content and data actually arrived (must PASS)"
# Three fixes that only this pack exercises, each demonstrable: -Dforbric.modDataPacks=off,
# -Dforbric.registryAliasParity=off, -Dforbric.neoRegistrationOrder=off each turn this gate RED.
check "datapacks served"              "Forbric/DataPacks\] served [1-9][0-9]* datapack"             "$LOG"
# The carriers are where the c: convention-tag skeleton lives — 513 tag files that exist in NO other jar, and that
# every cross-mod recipe is written against. Assert the NUMBER: the line keeps printing when the count goes to zero.
CARRIERS=$(grep -aoE 'served [0-9]+ datapack\(s\).*— [0-9]+ loader carrier' "$LOG" | grep -oE '[0-9]+ loader' | grep -oE '[0-9]+' | head -1)
assert_eq "loader carriers served" 2 "${CARRIERS:-none}"
# The ORDER, not the file names. This asserted the basenames of one machine's staged artifacts, so pointing the
# gate at another machine's — a user's own install, where the same jars are named forge-runtime-26.2.jar — failed
