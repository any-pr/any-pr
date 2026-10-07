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

package net.forbric.kernel.boot;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import net.fabricmc.api.EnvType;
import net.forbric.api.DiscoveredMod;
import net.forbric.api.Ecosystem;
import net.forbric.kernel.discovery.ForbricModDiscoverer;
import net.forbric.kernel.fabric.FabricModMetadataParser;
import net.forbric.kernel.fabric.KernelModMetadata;
import net.forbric.kernel.util.ForbricLog;

/**
 * Arbitrates TWO SEPARATE JARS that declare the SAME mod id — the case that appears the moment a Fabric modpack and
 * a NeoForge modpack are merged into one instance.
 *
 * <p><b>Why this is not part of {@link MultiLoaderArbiter}.</b> That one arbitrates ONE jar declaring several loader
 * manifests, and is keyed by jar path; it cannot see two files sharing an id. The two arbitrations also have
 * opposite effects on the classpath: {@code suppressedFor(jar, mine)} means "this jar acts as one family" and the
 * jar STAYS on the classpath, because the winning family needs its classes. Cross-jar means the jar is superseded
 * by a different file and must come OFF, or the losing copy still shadows classes (first-URL-wins: 662 of the
 * Fabric Sodium jar's 777 classes were measured as shadowed by the NeoForge one) and still contributes its mixin
 * configs — which nothing dedupes by mixin CLASS, only by config NAME, with {@code required} stripped, so a double
 * apply is silent. Folding the two together would make {@code suppressedFor} mean two things.
 *
 * <p><b>Reading ids.</b> Forge family via {@link ForbricModDiscoverer}; Fabric via
 * {@link FabricModMetadataParser}, deliberately NOT the metadata-package reader — only the parser reads
 * {@code environment}, and the runtime Fabric scanner filters on it. Arbitrating with a reader blind to
 * {@code environment} would let a client-only Fabric jar win an id on a dedicated server and then be dropped by the
 * environment filter, leaving the mod loaded by NOBODY. Claims are made on the declared id only, never on
 * {@code provides} aliases — {@code sodium-fabric} provides {@code indium}, and an alias claim would suppress a
 * real Indium jar.
 *
 * <p>Composes with the per-jar arbiter: {@link MultiLoaderArbiter#ownerOf} runs first, and only the ids declared
 * under that ecosystem count, so a universal jar enters as exactly one claim.
 *
 * <p>Switches: {@code -Dforbric.crossJarArbitration=off} disables it entirely;
 * {@code -Dforbric.modOwner=sodium=fabric,lithostitched=neoforge} overrides individual mods;
 * {@code -Dforbric.multiLoaderPreference} (shared with {@link MultiLoaderArbiter}) sets the global order.
 * {@link DisabledMods}' {@code forbric-disabled.txt} is applied here too, with the switch on or off: it is the
 * player's own list of jars not to load, not an arbitration.
 */
public final class DuplicateModArbiter {
	static final String SWITCH = "forbric.crossJarArbitration";
	static final String OWNER_OVERRIDE = "forbric.modOwner";
	/** The player-facing override file, next to {@code mods/}. See {@link #loadOverrideFile}. */
	static final String OVERRIDE_FILE = "forbric-mods.txt";

	/** One jar's claim: the ecosystem it loads as, and the mod ids it declares under that ecosystem. */
	public record Claim(Path jar, Ecosystem ecosystem, List<String> modIds,
			Map<String, String> versions) {
		public Claim(Path jar, Ecosystem ecosystem, List<String> modIds) {
			this(jar, ecosystem, modIds, Map.of());
		}

		String versionOf(String modId) {
			return versions.getOrDefault(modId, "0");
		}
	}

	/**
	 * A mod id whose jar for {@code ecosystem} was suppressed, so that ecosystem lost the mod's IDENTITY even
	 * though the winner still supplies its classes.
	 *
	 * <p>This is the residual of arbitration, and it is narrow but real. Two builds of the same multiloader mod are
	 * 98–100% the same classes — measured on the two packs: ferritecore and YACL are identical, lithostitched
	 * differs by 2 + 8 platform-glue classes, Jade by 20 + 8. So a mod on the LOSING side still links against the
	 * winner's copy and still sees the content the winner registered. What it cannot see is the mod itself:
	 * {@code ModList.get().isLoaded(id)} answers false, and a mod that gates an integration on that check silently
	 * disables it. Registering a presence-only container on the losing side closes exactly that gap and nothing
	 * more.
	 */
	public record Alias(String modId, Ecosystem ecosystem, String version) {
	}

	/**
	 * Which jars must not be loaded, who owns each contested id, and which ecosystems need a presence alias.
	 *
	 * <p>{@code rescueJars} is the subset the class loader may still serve a missing class from (see
	 * ForbricClassLoader.setRescueJars). It is NOT every suppressed jar: discovery must skip every unselected
	 * physical candidate, but only the other ecosystem's build of a mod that did load may lend it a class. A
	 * losing JarJar version would mix two builds of one library, a side-excluded jar would make client-only
	 * code loadable on a server, and a losing root's nested tree was never meant to run (PLAN.md:63).
	 */
	public record Decision(Set<Path> suppressedJars, Map<String, Path> ownerByModId, List<Alias> aliases, Set<Path> rescueJars) {
		/** The top-level-only passes, where every suppressed jar is exactly such another-ecosystem build. */
		public Decision(Set<Path> suppressedJars, Map<String, Path> ownerByModId, List<Alias> aliases) {
			this(suppressedJars, ownerByModId, aliases, suppressedJars);
		}

		public boolean suppressed(Path jar) {
			return jar != null && suppressedJars.contains(jar.toAbsolutePath());
		}

		/** The aliases this ecosystem must publish so {@code isLoaded(id)} answers for mods it lost. */
		public List<Alias> aliasesFor(Ecosystem ecosystem) {
			List<Alias> mine = new ArrayList<>();
			for (Alias alias : aliases) {
				if (alias.ecosystem() == ecosystem) mine.add(alias);
			}
			return mine;
		}

		public static Decision none() {
			return new Decision(Set.of(), Map.of(), List.of());
		}
	}

	private static volatile Decision cached;
	private static volatile Path cachedDir;
	private static volatile EnvType cachedSide;
	private static volatile NestedCandidatePlan wholeInstancePlan;

	/**
	 * What the top-level pass claimed, kept so the nested pass can arbitrate over the UNION rather than over the
	 * nested jars alone. Without them a nested jar could only ever be compared with other nested jars, and a
	 * library nested beside a top-level copy of itself would still load twice.
	 */
	private static volatile List<Claim> topLevelClaims = List.of();
	private static volatile List<Alias> topLevelAliases = List.of();

	private DuplicateModArbiter() {
	}

	/**
	 * The decision this boot already made, or {@link Decision#none()} if arbitration has not run.
	 *
	 * <p>For consumers that run after {@code KernelBoot} decided and must not re-scan — notably
	 * {@code KernelModLoader}, which publishes the NeoForge presence aliases long after the mods directory was
	 * walked.
	 */
	public static synchronized Decision current() {
		return cached != null ? cached : Decision.none();
	}

	/** Forgets the decision — for tests, and so a re-launch in one process re-arbitrates. */
	public static synchronized void reset() {
		DisabledMods.reset();
		cached = null;
		cachedDir = null;
		cachedSide = null;
		wholeInstancePlan = null;
		topLevelClaims = List.of();
		topLevelAliases = List.of();
		fileOverrides = Map.of();
	}

	/** Scans {@code modsDir} once and arbitrates. Repeat calls for the same directory return the same decision. */
	public static synchronized Decision arbitrate(Path modsDir, EnvType envType) {
		return arbitrate(modsDir, envType, null);
	}

	/**
	 * As {@link #arbitrate(Path, EnvType)}, holding nested Fabric mods' {@code minecraft} requirements against
	 * {@code minecraftVersion} ({@code null}: not judged). See {@link net.forbric.kernel.fabric.NestedFabricRequirements}.
	 */
	public static synchronized Decision arbitrate(Path modsDir, EnvType envType, String minecraftVersion) {
		if ("off".equalsIgnoreCase(System.getProperty(SWITCH, "on"))) {
			wholeInstancePlan = null;
			cached = null; cachedDir = null; cachedSide = null;
			ForbricLog.warn("[Forbric/DupeId] cross-jar arbitration DISABLED (-D%s=off) — two jars sharing a mod id "
					+ "will BOTH load, shadowing each other's classes and applying each other's mixins", SWITCH);
			// The switch turns off arbitration, not the player's own list. Cached, because the seeder asks
			// current() rather than being handed this, and would otherwise list a jar nobody loaded.
			Set<Path> disabled = DisabledMods.load(modsDir == null ? null : modsDir.getParent(), modsDir);
			if (disabled.isEmpty()) return Decision.none();
			cached = new Decision(disabled, Map.of(), List.of(), Set.of());
			cachedDir = modsDir;
			cachedSide = envType;
			return cached;
		}
		if (cached != null && modsDir != null && modsDir.equals(cachedDir) && envType == cachedSide) return cached;

		Path rundir = modsDir == null ? null : modsDir.getParent();
		loadOverrideFile(rundir);
		// Read before the scan, so a switched-off jar never becomes a claim: it cannot win an id, lose one to a
		// build that is also off, or have its nested jars inventoried.
		Set<Path> disabled = DisabledMods.load(rundir, modsDir);
		List<Alias> universalAliases = new ArrayList<>();
		List<Claim> claims = scan(modsDir, envType, universalAliases, disabled);
		topLevelClaims = List.copyOf(claims);
		topLevelAliases = List.copyOf(universalAliases);
		Decision decision;
		if (claims.isEmpty()) {
			wholeInstancePlan = null;
			decision = new Decision(Set.of(), Map.of(), List.copyOf(universalAliases));
		} else {
			NestedCandidateInventory inventory = NestedCandidateInventory.scan(claims,
					rundir.resolve(".forbric-kernel").resolve("candidates"), envType,
					net.forbric.kernel.fabric.NestedFabricRequirements.Platform.running(minecraftVersion));
			List<Claim> all = inventory.claims();
			Map<String, Ecosystem> overrides = new LinkedHashMap<>();
			for (Claim claim : all) for (String id : claim.modIds()) { Ecosystem forced = overrideFor(id); if (forced != null) overrides.put(id, forced); }
			List<JointCandidateSelector.Rule> contracts = CandidateContractScanner.scanPhysical(all, envType, inventory.symbolOwners());
			var result = ReachableCandidateSelector.solve(inventory, contracts, preference(), nestedPreference(), overrides,
					Math.max(1, Math.min(1_000_000, Integer.getInteger("forbric.arbitrationMaxNodes", 100_000))));
			wholeInstancePlan = new NestedCandidatePlan(inventory, result);
			reportSelection(all, result, overrides);
			// A parent the scan stopped inside has nested jars nobody examined, and both discoveries read only
			// this plan, so they will not load. That must stop or prompt, not pass as a quiet suspicion.
			for (var issue : inventory.issues()) if (issue.bound() && result.selected().contains(issue.source())) {
				String owner = wholeInstancePlan.ownerOf(issue.source());
				net.forbric.api.CompatibilityFindings.record(new net.forbric.api.CompatibilityFinding("arbitration:inventory", owner,
						"Bundled libraries", "arbitration:inventory", net.forbric.api.CompatibilityFinding.Confidence.CONFIRMED, true,
						"Some libraries bundled in this mod were not examined and will not be loaded", List.of(issue.source() + ": " + issue.detail())));
			}
			List<Alias> aliases = new ArrayList<>(universalAliases); aliases.addAll(inventory.universalAliases());
			decision = decisionFromSelection(all, aliases, "whole-instance", result);
			Set<Path> suppressed = new LinkedHashSet<>(decision.suppressedJars());
			for (var node : inventory.nodes().values()) if (!result.selected().contains(node.path())) suppressed.add(node.path());
			decision = new Decision(Set.copyOf(suppressed), decision.ownerByModId(), decision.aliases(), rescuable(inventory, result));
			// Each physical candidate owns only its own classes. Do not count losing nested classes as a root's.
			for (String line : divergenceReport(all, decision)) ForbricLog.info("%s", line);
		}
		decision = withDisabled(decision, disabled);
		writeOverrideTemplate(rundir, decision);
		MergeReport.write(rundir, modsDir, decision);
		cached = decision;
		cachedDir = modsDir;
		cachedSide = envType;
		return decision;
	}

	/**
	 * {@code decision} with the player's switched-off jars added to what is suppressed and to nothing else.
	 *
	 * <p>Not rescue jars. A rescue jar is another ecosystem's build of a mod that IS loaded, lending a class the
	 * winner lacks; a switched-off jar is a mod the player asked not to run, and serving its classes on demand
	 * would run it piecemeal.
	 */
	static Decision withDisabled(Decision decision, Set<Path> disabled) {
		if (disabled.isEmpty()) return decision;
		Set<Path> suppressed = new LinkedHashSet<>(decision.suppressedJars());
		suppressed.addAll(disabled);
		return new Decision(Set.copyOf(suppressed), decision.ownerByModId(), decision.aliases(), decision.rescueJars());
	}

	/**
	 * Unselected candidates that are another ecosystem's build of a mod that did load: every id they claim is
	 * owned by a selected build of a different ecosystem, and they were themselves reachable (a root, or a child
	 * of a selected parent). Side-excluded jars, same-ecosystem version losers, anonymous libraries and anything
	 * inside a losing root are left out.
	 */
	static Set<Path> rescuable(NestedCandidateInventory inventory, JointCandidateSelector.Result result) {
		Map<String, Set<Ecosystem>> winners = new HashMap<>();
		for (var node : inventory.nodes().values()) {
			if (!result.selected().contains(node.path()) || node.claim() == null) continue;
			for (String id : node.claim().modIds()) winners.computeIfAbsent(JointCandidateSelector.key(id), k -> new HashSet<>()).add(node.claim().ecosystem());
		}
		Set<Path> rescue = new LinkedHashSet<>();
		for (var node : inventory.nodes().values()) {
			if (result.selected().contains(node.path()) || node.excluded() || node.claim() == null || node.claim().modIds().isEmpty()) continue;
			boolean reachable = node.root() || inventory.edges().stream().anyMatch(e -> e.child().equals(node.path()) && result.selected().contains(e.parent()));
			boolean otherBuild = node.claim().modIds().stream().allMatch(id -> {
				Set<Ecosystem> owners = winners.get(JointCandidateSelector.key(id));
				return owners != null && !owners.contains(node.claim().ecosystem());
			});
			if (reachable && otherBuild) rescue.add(node.path());
		}
		return Set.copyOf(rescue);
	}

	/** For discovery only: another mods directory or physical side must never borrow this plan. */
	public static NestedCandidatePlan planned(Path modsDir, EnvType side) {
		return modsDir != null && modsDir.equals(cachedDir) && side == cachedSide ? wholeInstancePlan : null;
	}

	public static NestedCandidatePlan currentPlan() { return wholeInstancePlan; }

	/**
	 * The SECOND pass: the same arbitration, over the nested jars both families extract out of their mods.
