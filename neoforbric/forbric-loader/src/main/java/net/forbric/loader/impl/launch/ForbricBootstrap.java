/*
 * Copyright 2026 The Forbric Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.forbric.loader.impl.launch;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.json.JsonFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.loader.impl.util.SystemProperties;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import net.forbric.loader.impl.access.AccessTransformer;
import net.forbric.loader.impl.access.AccessTransformerParser;
import net.forbric.loader.impl.access.AtDirective;
import net.forbric.loader.impl.discovery.ForbricModDiscoverer;
import net.forbric.loader.impl.mapping.ForbricCache;
import net.forbric.loader.impl.mapping.ForbricMappings;
import net.forbric.loader.impl.mapping.ForgeModRemapper;
import net.forbric.loader.impl.metadata.DiscoveredMod;
import net.forbric.loader.impl.metadata.ModEcosystem;
import net.forbric.loader.impl.metadata.UnifiedDependency;
import net.forbric.loader.impl.transformer.ForbricMergedBaseCompatTransformer;
import net.forbric.loader.impl.transformer.ForbricTransformBridge;
import net.forbric.loader.impl.transformer.TransformChain;
import net.forbric.loader.impl.transformer.TransformContext;
import net.forbric.loader.impl.transformer.TransformPhase;
import net.forbric.loader.impl.util.ForbricLog;

/**
 * Forbric's pre-launch step. It runs the {@linkplain ForbricModDiscoverer unified discovery pass}, installs
 * the unified transform chain into the substrate's pre-Mixin byte path, and — when the mapping inputs are
 * available — automatically remaps + wraps every discovered Forge/NeoForge mod and hands them to the
 * substrate via {@code fabric.addMods}, plus applies their Access Transformers. It then returns control to
 * Knot to boot the game. All Forge setup is best-effort: any failure logs and falls back to a Fabric-only
 * boot rather than aborting.
 *
 * <p>Forge support is driven by system properties so the launch environment can supply the (non-bundled)
 * mapping inputs:
 * <ul>
 *   <li>{@code forbric.intermediary} — path to the Fabric intermediary mappings (tiny) for this MC version,</li>
 *   <li>{@code forbric.mojmap} — path to the Mojang client mappings (ProGuard) for this MC version,</li>
 *   <li>{@code forbric.gameJar} — path to the (obfuscated) vanilla game jar.</li>
 * </ul>
 */
public final class ForbricBootstrap {
	public static final String VERSION = "0.1.0";
	private static final java.util.Set<String> FORGE_OWNED_GUEST_MIXIN_TARGETS = java.util.Set.of(
			"net/minecraft/client/gui/GuiGraphicsExtractor",
			"net/minecraft/client/renderer/EndFlashState",
			"net/minecraft/client/renderer/GameRenderer",
			"net/minecraft/client/renderer/ItemInHandRenderer",
			"net/minecraft/client/renderer/LevelRenderer",
			"net/minecraft/client/renderer/LightmapRenderStateExtractor",
			"net/minecraft/client/renderer/OrderedSubmitNodeCollector",
			"net/minecraft/client/renderer/Projection",
			"net/minecraft/client/renderer/ScreenEffectRenderer",
			"net/minecraft/client/renderer/SkyRenderer",
			"net/minecraft/client/renderer/SubmitNodeCollection",
			"net/minecraft/client/renderer/SubmitNodeStorage",
			"net/minecraft/client/renderer/WeatherEffectRenderer",
			"net/minecraft/server/network/config/SynchronizeRegistriesTask",
			"net/minecraft/tags/TagNetworkSerialization");
	private static final java.util.List<String> FORGE_OWNED_GUEST_MIXIN_TARGET_PREFIXES = java.util.List.of(
			"net/minecraft/client/gui/render/",
			"net/minecraft/client/particle/",
			"net/minecraft/client/renderer/block/",
			"net/minecraft/client/renderer/blockentity/",
			"net/minecraft/client/renderer/chunk/",
			"net/minecraft/client/renderer/debug/",
			"net/minecraft/client/renderer/entity/",
			"net/minecraft/client/renderer/extract/",
			"net/minecraft/client/renderer/feature/",
			"net/minecraft/client/renderer/fog/",
			"net/minecraft/client/renderer/item/",
			"net/minecraft/client/renderer/rendertype/",
			"net/minecraft/client/renderer/state/",
			"net/minecraft/client/resources/model/");

	private ForbricBootstrap() {
	}

	public static void run(String[] args, String side) {
		ForbricLog.info("======================================================");
		ForbricLog.info(" Forbric Loader " + VERSION + " (" + side + ") — unified Fabric + Forge");
		ForbricLog.info("======================================================");

		Path gameDir = resolveGameDir(args);
		Path mods = gameDir.resolve("mods");
		EnvType envType = "server".equalsIgnoreCase(side) ? EnvType.SERVER : EnvType.CLIENT;

		try {
			List<DiscoveredMod> all = new ForbricModDiscoverer().discover(mods);

			// The Forge family this instance's game base carries: NEOFORGE. Fabric layers on it. Explicit via
			// -Dforbric.forgeFamily (the installer writes it), else probed from the staged runtime jar's
			// injected identity (mod id "neoforge").
			java.util.Set<ModEcosystem> activeFamilies = resolveActiveFamilies(all);
			// A representative family for diagnostics — the wrong-family skip below names it in its warning.
			ModEcosystem family = activeFamilies.iterator().next();
			java.util.Set<String> activeFamilySources = new java.util.HashSet<>();
			for (DiscoveredMod mod : all) {
				if (activeFamilies.contains(mod.getEcosystem())) activeFamilySources.add(mod.getSource());
			}

			// A jar carrying BOTH manifests is usually already substrate-loadable via its fabric.mod.json
			// (the staged neoforge-runtime.jar, pre-wrapped mods, genuine dual-loader builds) — preparing its
			// NeoForge identity would duplicate the whole jar. EXCEPTION: "wrongloader traps" — Forge-family-only
			// builds ship a fake fabric.mod.json (unparseable version, an entrypoint that just throws) to
			// fail fast when dropped into a Fabric loader. Those we suppress on the Fabric side
			// (-Dforbric.suppressMods, substrate patch 0006) and load the REAL Forge-family identity instead.
			java.util.Set<String> fabricSources = new java.util.HashSet<>();
			java.util.Map<String, DiscoveredMod> fabricBySource = new java.util.HashMap<>();
			java.util.Map<String, DiscoveredMod> dubiousFabricBySource = new java.util.HashMap<>();
			for (DiscoveredMod mod : all) {
				if (mod.getEcosystem().isForgeFamily()) continue;
				boolean semverOk;
				try {
					net.fabricmc.loader.api.SemanticVersion.parse(mod.getVersion());
					semverOk = true;
				} catch (Exception e) {
					semverOk = false;
				}
				if (semverOk) {
					fabricSources.add(mod.getSource());
					fabricBySource.put(mod.getSource(), mod);
				} else {
					dubiousFabricBySource.put(mod.getSource(), mod);
				}
			}

			List<DiscoveredMod> forge = new ArrayList<>();
			java.util.List<String> suppress = new ArrayList<>();
			java.util.List<String> suppressSources = new ArrayList<>();
			long fabric = 0;
			java.util.List<String> wrongFamilyWarned = new ArrayList<>();
			for (DiscoveredMod mod : all) {
				if (mod.getEcosystem().isForgeFamily()) {
					// The neoforge-runtime.jar (mod id "neoforge") IS the Knot-loaded runtime — it must load
					// through its own fabric identity, never wrapped. Same for any jar Forbric already produced.
					if ("neoforge".equals(mod.getId()) && fabricSources.contains(mod.getSource())) {
						ForbricLog.warn("[Forbric] skipping Forge prep for %s (runtime jar, substrate-loadable)%n", mod.getId());
						continue;
					}

					// A mod with only an inactive family's manifest cannot work on this game base — skip it
					// honestly, naming the right profile.
					if (!activeFamilies.contains(mod.getEcosystem())) {
						if (!activeFamilySources.contains(mod.getSource()) && !wrongFamilyWarned.contains(mod.getId())) {
							wrongFamilyWarned.add(mod.getId());
							ForbricLog.warn("[Forbric] '%s' is a %s mod, but this instance runs the %s game base — "
									+ "skipping it. Install the mod's %s build here, or launch the forbric-%s-26.2 "
									+ "profile to load it.", mod.getId(), mod.getEcosystem().familyId(),
									family.familyId(), family.familyId(), mod.getEcosystem().familyId());
						}
						continue;
					}

					// Genuine multiloader "unimod": one jar shipping BOTH a real Fabric identity and this Forge-family
					// identity, with loader-specific mixins/ATs (e.g. collective_fabric.mixins.json vs
					// collective_neoforge.mixins.json) — and, unlike a wrongloader trap, the SAME mod id on both
					// sides. On the NeoForge game base the NeoForge variant is the one written for this bytecode,
					// so prefer it: suppress the original jar (its Fabric identity would apply the loader-wrong
					// mixins) BY SOURCE — id-based suppression can't be used when both identities share the id —
					// and load the mod through the NeoForge lifecycle (re-wrapped under cache/).
					DiscoveredMod dual = fabricBySource.get(mod.getSource());
					if (dual != null) {
						String file = sourceFileName(mod.getSource());
						if (file != null && !suppressSources.contains(file)) {
							ForbricLog.warn("[Forbric] multiloader jar %s: preferring the Forge-family identity on the NeoForge base, suppressing the Fabric identity in %s%n",
									mod.getId(), file);
							suppressSources.add(file);
						}
						forge.add(mod);
						continue;
					}

					DiscoveredMod trap = dubiousFabricBySource.get(mod.getSource());
					if (trap != null && !suppress.contains(trap.getId())) {
						ForbricLog.warn("[Forbric] suppressing wrongloader-trap fabric identity '%s' of Forge-family mod %s%n",
								trap.getId(), mod.getId());
						suppress.add(trap.getId());
					}
					forge.add(mod);
				} else {
					fabric++;
				}
			}
			if (!suppress.isEmpty()) {
				String existing = System.getProperty("forbric.suppressMods", "");
				System.setProperty("forbric.suppressMods",
						existing.isEmpty() ? String.join(",", suppress) : existing + "," + String.join(",", suppress));
			}
			if (!suppressSources.isEmpty()) {
				String existing = System.getProperty("forbric.suppressModSources", "");
				System.setProperty("forbric.suppressModSources",
						existing.isEmpty() ? String.join(",", suppressSources) : existing + "," + String.join(",", suppressSources));
			}

			// On the NeoForge base (mods/neoforge-runtime.jar, mod id "neoforge"), NeoForge's GameData
			// owns the registry lifecycle: freeze/unfreeze windows (Forbric bridge), id tracking, sync and
			// persistence. Fabric API's registry-sync module manages that same lifecycle for the vanilla base —
			// redundant here, and its mixin anchors do not survive the NeoForge patches (Bootstrap.bootStrap
			// no longer calls wrapStreams). Neutralize its configs via substrate patch 0007; user-supplied
			// -Dforbric.suppressMixinConfigs entries are preserved.
			if (all.stream().anyMatch(m -> "neoforge".equals(m.getId()))) {
				// Hard-suppressed for a SEMANTIC reason (not just a failed mixin): NeoForge's GameData owns the
				// registry lifecycle here — freeze/unfreeze windows, id tracking, sync, persistence — so
				// Fabric's registry-sync must never partially apply and double-manage it. Other Fabric API
				// modules whose mixins simply can't apply on the NeoForge-patched base are handled generically
				// by the best-effort mixin error handler (relaxMixinOverwrites, substrate patch 0007), which
				// skips the unpatchable mixin with a warning instead of needing a name here.
				String defaults = "fabric-registry-sync-v0.mixins.json,fabric-registry-sync-v0.client.mixins.json";
				String existing = System.getProperty("forbric.suppressMixinConfigs", "");
				System.setProperty("forbric.suppressMixinConfigs",
						existing.isEmpty() ? defaults : existing + "," + defaults);
				ForbricLog.warn("[Forbric] NeoForge base detected - suppressing GameData-owned Fabric mixin configs: " + defaults);

				// A guest Forge/NeoForge mod pinned to a DIFFERENT MC version (e.g. xaerominimap-26.1.4, which itself
				// declares minecraft (1.21.10, 26.1.0) while we run 26.2) carries mixins whose @Shadow/@Inject anchors
				// target members that MC version renamed or removed. Per-mixin WARN-skip is NOT enough here: Mixin
				// abandons the ENTIRE target class when one mixin fails during context creation, so a load-bearing
				// co-located mixin from ANOTHER mod (e.g. fabric-rendering-v1 adding GuiRendererExtensions to
				// GuiRenderer) is dropped too, and a later cast to that interface throws ClassCastException. Such a mod
				// cannot function anyway, so suppress its mixin CONFIGS whole — they never enter any target's apply
				// batch — instead of relaxing per-mixin. Fail OPEN: suppress ONLY when the mod ITSELF declares it
				// excludes the running MC version; a missing constraint or any parse trouble keeps the mod, so a
				// compatible mod is never suppressed by mistake.
				String runningMc = System.getProperty("forbric.mcVersion", "26.2");
				java.util.Set<String> versionSuppressedModIds = new java.util.HashSet<>();
				java.util.LinkedHashSet<String> versionSuppressedConfigs = new java.util.LinkedHashSet<>();
				for (DiscoveredMod mod : all) {
					if (!mod.getEcosystem().isForgeFamily()) continue; // only Forge-family guests pin to an exact MC version
					String id = mod.getId();
					if (id.startsWith("forbric") || "neoforge".equals(id) || "minecraft".equals(id)) continue;
					if (!declaresMcIncompatibility(mod, runningMc)) continue;
					versionSuppressedModIds.add(id);
					versionSuppressedConfigs.addAll(mod.getMixinConfigs());
					ForbricLog.warn("[Forbric] '%s' declares it does not support Minecraft %s (version-incompatible mod); "
							+ "suppressing its mixin configs %s and excluding it from Forge-family mod loading — the mod "
							+ "is inert here, install a build for this MC version.", id, runningMc, mod.getMixinConfigs());
				}
				if (!versionSuppressedConfigs.isEmpty()) {
					String csv = String.join(",", versionSuppressedConfigs);
					String prev = System.getProperty("forbric.suppressMixinConfigs", "");
					System.setProperty("forbric.suppressMixinConfigs", prev.isEmpty() ? csv : prev + "," + csv);
				}
				// Same signal, loading side: publish the set so the FML discovery drivers keep these mods OUT of
				// ModSorter (one alien-version mod there aborts the whole ecosystem's LoadingModList — see
				// ForbricVersionGate) and the client asset wiring skips their packs. User-supplied entries append.
				if (!versionSuppressedModIds.isEmpty()) {
					String csv = String.join(",", versionSuppressedModIds);
					String prev = System.getProperty(net.forbric.loader.impl.util.ForbricVersionGate.PROPERTY, "");
					System.setProperty(net.forbric.loader.impl.util.ForbricVersionGate.PROPERTY,
							prev.isEmpty() ? csv : prev + "," + csv);
				}

				// Guest mixin configs deep-hook vanilla internals that the NeoForge-patched base has moved or rewritten, so
				// an anchor no longer resolves and the mixin throws FATAL during prepare/apply (crash-to-desktop).
				// This bites two kinds of guest equally: (1) a Fabric mixin whose injector Forge's binary patches
				// invalidated, and (2) a Forge/NeoForge mod built for a DIFFERENT MC version (e.g. a 1.21.11 build
				// on the 26.2 base) whose @Shadow/@Inject targets a member that no longer exists. Both degrade the
				// same way. Two coordinated defenses, computed from the guest configs ACTUALLY present — NOT gated
