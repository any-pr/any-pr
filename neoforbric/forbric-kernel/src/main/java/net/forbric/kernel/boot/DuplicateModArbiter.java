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
	 *
	 * <p>A JarJar/JiJ child is not in {@code mods/}, so {@link #arbitrate(Path, EnvType)} never saw it — and each
	 * loader only dedupes against its own family ({@code KernelFabricLoader.register} drops a duplicate Fabric id,
	 * {@code KernelModLoader} the same for {@code @Mod}), so nobody was checking across. A library nested by a
	 * Fabric mod AND by a MinecraftForge mod therefore loaded twice, once per ecosystem, and initialised twice.
	 * Xaero's is the worked example: {@code xaerominimap-fabric} nests {@code xaerolib-fabric},
	 * {@code xaeroworldmap-forge} nests {@code xaerolib-forge}, and the second {@code XaeroLib.<init>} died on
	 * "Attempted to register a duplicate config channel: xaerolib:main" — but only AFTER its superclass
	 * constructor had already overwritten {@code XaeroLib.INSTANCE} with the half-built object, so a live mixin
	 * then called into it and took the client down on a render frame.
	 *
	 * <p>Arbitrated over the UNION of the top-level claims and the nested ones, not over the nested ones alone:
	 * a nested copy must also lose to a top-level jar of the same mod. The top-level half of the answer is then
	 * held fixed — those jars' discovery has already run by the time this is called, so re-deciding them would
	 * describe a load that did not happen. A union that WOULD have changed one is a bug in the ordering, and says
	 * so rather than pretending.
	 *
	 * @param nestedJars every nested jar both families extracted, in extraction order
	 * @return a decision that suppresses everything phase one did, plus the nested losers
	 */
	/**
	 * One line per suppressed jar whose build carries classes the winning build does not — report only. The
	 * residual the Alias javadoc measures (Jade: 20 Fabric-only + 8 NeoForge-only) is what a mod on the losing side
	 * cannot link against; a loser-only glue class is not a KNOWN loss, so nothing is marked — a confidently wrong
	 * mark is worse than none. Zip listings only, no bytecode. Silent for a pair whose class sets agree.
	 */
	static List<String> divergenceReport(List<Claim> claims, Decision decision) {
		List<String> lines = new ArrayList<>();
		Map<Path, Set<String>> read = new HashMap<>();
		Set<String> elsewhere = null;
		for (Claim loser : claims) {
			if (!decision.suppressed(loser.jar())) continue;
			Path winner = null;
			for (String id : loser.modIds()) {
				Path owner = decision.ownerByModId().get(id);
				if (owner != null && !owner.equals(loser.jar().toAbsolutePath())) { winner = owner; break; }
			}
			if (winner == null) continue;
			List<String> only = loserOnlyClasses(loser.jar(), winner, read);
			if (only.isEmpty()) continue;
			Ecosystem winnerFamily = null;
			for (Claim claim : claims) if (claim.jar().toAbsolutePath().equals(winner)) winnerFamily = claim.ecosystem();
			// The same measurement, kept rather than only printed: a guest mixin that targets one of these has no
			// target on this instance, and nothing else in the chain can tell that from an ordinary absence.
			//
			// But "only the losing build has it" is not "nothing in this instance has it", and the registry is
			// read as the second. A losing build routinely bundles a third mod's classes: sodium's FABRIC build
			// ships fabric-api's ExtendedBlockModelSubmit, and the player's own fabric-api supplies it whatever
			// sodium does. Recording it unsubtracted marked four mods on a 28-mod instance for mixins that were
			// fine. So what every jar that DID load provides — including inside its bundled jars — is taken back
			// out first, and only classes no loaded jar has reach the registry.
			if (elsewhere == null) elsewhere = classesStillLoaded(claims, decision, read);
			List<String> gone = new ArrayList<>();
			for (String name : only) if (!elsewhere.contains(name) && !onTheLaunchClasspath(name)) gone.add(name);
			if (!gone.isEmpty()) {
				ArbitratedAwayClasses.record(gone,
						new ArbitratedAwayClasses.Loss(loser.modIds().get(0), loser.ecosystem(), winnerFamily,
								String.valueOf(loser.jar().getFileName())));
			}
			List<String> shown = only.subList(0, Math.min(8, only.size()));
			lines.add("[Forbric/DupeId] " + loser.modIds().get(0) + ": the losing " + loser.ecosystem() + " build ("
					+ loser.jar().getFileName() + ") carries " + only.size() + " class(es) the winning "
					+ (winnerFamily == null ? "other" : winnerFamily.toString()) + " build does not: "
					+ String.join(", ", shown) + (only.size() > shown.size() ? ", …" : ""));
		}
		return lines;
	}

	/** The .class entries (dotted, no extension) in {@code loser} that {@code winner} lacks; empty if either is unreadable. */
	static List<String> loserOnlyClasses(Path loser, Path winner) {
		return loserOnlyClasses(loser, winner, new HashMap<>());
	}

	private static List<String> loserOnlyClasses(Path loser, Path winner, Map<Path, Set<String>> read) {
		Set<String> winning = classEntries(winner, read);
		if (winning == null) return List.of();
		Set<String> losing = classEntries(loser, read);
		if (losing == null) return List.of();
		List<String> only = new ArrayList<>();
		for (String name : losing) if (!winning.contains(name)) only.add(name);
		java.util.Collections.sort(only);
		return only;
	}

	/**
	 * Every class the jars that DID load bring, so the ones that did not can be named exactly.
	 *
	 * <p>Read once per arbitration, and only when there is something to subtract from — on an instance with no
	 * duplicated mod id nothing here is opened at all. A jar that cannot be read contributes nothing, which
	 * widens the "lost" set rather than narrowing it; that direction is the one a reader can check, because a
	 * name that turns out to be present is visible the moment the mixin applies anyway.
	 */
	private static Set<String> classesStillLoaded(List<Claim> claims, Decision decision, Map<Path, Set<String>> read) {
		Set<String> loaded = new HashSet<>();
		for (Claim claim : claims) {
			if (decision.suppressed(claim.jar())) continue;
			Set<String> names = classEntries(claim.jar(), read);
			if (names != null) loaded.addAll(names);
		}
		return loaded;
	}

	/**
	 * Whether the launch classpath already serves {@code name}, so losing a mod's copy of it costs nothing.
	 *
	 * <p>The third source, after the winning build and the other mods. A losing build often bundles a shaded
	 * LIBRARY: glitchcore's Fabric build carries all 189 {@code com.electronwill.nightconfig.core.*} classes,
	 * which the game's own {@code libraries/} supplies to every mod regardless of which glitchcore loaded. Without
	 * this those 189 were the whole "arbitrated away" set for that mod, measured on the 28-mod instance.
	 *
	 * <p>The system loader is the right question and the sovereign loader is not: this one has {@code libraries/}
	 * and NOT {@code mods/}, which is exactly the line being drawn. Asking the sovereign loader would answer yes
	 * for the losing jar's own classes too — it keeps them readable — and that is measured: the live boot still
	 * served {@code sodium.fabric.render.FluidRendererImpl}'s bytes while the loaded sodium was the NeoForge
	 * build, so a resource check against it was silent on the one case this registry was written for.
	 */
	private static boolean onTheLaunchClasspath(String dottedName) {
		return ClassLoader.getSystemResource(dottedName.replace('.', '/') + ".class") != null;
	}

	private static Set<String> classEntries(Path jar, Map<Path, Set<String>> read) {
		if (read.containsKey(jar)) return read.get(jar);
		Set<String> names = classEntries(jar);
		read.put(jar, names);
		return names;
	}

	/**
	 * Every class a jar brings, INCLUDING the ones inside its bundled jars.
	 *
	 * <p>Counting only top-level entries makes two builds of the same mod look wildly different when one of them
	 * nests its shared half and the other inlines it: sodium's NeoForge build bundles the common
	 * {@code sodium.client.*} classes in {@code META-INF/jars/}, so a flat comparison called 727 classes
	 * "Fabric-only" that both builds plainly have. That was harmless while this fed one log line and stopped
	 * being harmless the moment {@link ArbitratedAwayClasses} made a mixin's target depend on it — four mods were
	 * marked for mixins against classes that were present all along.
	 */
	private static Set<String> classEntries(Path jar) {
		Set<String> names = new LinkedHashSet<>();
		if (wholeInstancePlan != null && wholeInstancePlan.inventory().nodes().containsKey(jar.toAbsolutePath().normalize())) {
			try (var zip = new java.util.zip.ZipFile(jar.toFile())) {
				zip.stream().map(java.util.zip.ZipEntry::getName).filter(name -> name.endsWith(".class"))
						.forEach(name -> names.add(name.substring(0, name.length() - 6).replace('/', '.')));
				return names;
			} catch (IOException unreadable) { return null; }
		}
		if (!collectClasses(jar, names)) return null;
		return names;
	}

	/** Adds {@code jar}'s classes and those of its bundled jars; false when the jar itself cannot be read. */
	private static boolean collectClasses(Path jar, Set<String> names) {
		try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
			for (java.util.zip.ZipEntry entry : zip.stream().toList()) {
				String name = entry.getName();
				if (name.endsWith(".class")) {
					names.add(name.substring(0, name.length() - 6).replace('/', '.'));
				} else if (name.endsWith(".jar")) {
					// Read in memory: the nested jar is a comparison input, not something to extract.
					try (java.util.zip.ZipInputStream nested =
							new java.util.zip.ZipInputStream(zip.getInputStream(entry))) {
						for (java.util.zip.ZipEntry inner; (inner = nested.getNextEntry()) != null; ) {
							String innerName = inner.getName();
							if (!innerName.endsWith(".class")) continue;
							names.add(innerName.substring(0, innerName.length() - 6).replace('/', '.'));
						}
					} catch (java.io.IOException unreadableNested) {
						// One unreadable bundle must not make the whole jar unreadable — it only widens the diff.
					}
				}
			}
		} catch (java.io.IOException unreadable) {
			return false;
		}
		return true;
	}

	public static synchronized Decision arbitrateNested(EnvType envType, List<Path> nestedJars) {
		Decision phase1 = current();
		if ("off".equalsIgnoreCase(System.getProperty(SWITCH, "on"))) return phase1;
		if (wholeInstancePlan != null && envType == cachedSide) {
			wholeInstancePlan.verify(nestedJars == null ? List.of() : nestedJars);
			return phase1;
		}
		if (nestedJars == null || nestedJars.isEmpty()) return phase1;

		ForbricModDiscoverer discoverer = new ForbricModDiscoverer();
		List<Claim> nestedClaims = new ArrayList<>();
		List<Alias> ignored = new ArrayList<>();
		for (Path jar : nestedJars) {
			Claim claim = claimOf(discoverer, jar, envType, ignored);
			if (claim != null) nestedClaims.add(claim);
		}
		if (nestedClaims.isEmpty()) return phase1;
		// Published, not just returned. KernelModLoader reads current() long after the mods directory was walked,
		// to hand NeoForge the presence aliases (KernelModLoader:159), and PassiveSeeder reads it to skip
		// suppressed jars. Leaving the nested half out of the cache would mean the answer this boot acted on and
		// the answer those two see are different answers.
		cached = arbitrateNested(phase1, topLevelClaims, nestedClaims);
		return cached;
	}

	/**
	 * The pure half of the nested pass, so tests can drive it without a filesystem — the same split as
	 * {@link #arbitrate(List, List)}.
	 */
	static Decision arbitrateNested(Decision phase1, List<Claim> topLevel, List<Claim> nestedClaims) {
		// Everything that can claim an id, so a nested copy is also weighed against a TOP-LEVEL jar of the same
		// mod — a library nested inside a Fabric mod must still lose to the NeoForge build the user installed.
		Map<String, List<Claim>> byId = new LinkedHashMap<>();
		for (Claim claim : topLevel) {
			if (phase1.suppressed(claim.jar())) continue;   // already lost phase one; it is not a live claimant
			for (String id : claim.modIds()) byId.computeIfAbsent(id, k -> new ArrayList<>()).add(claim);
		}
		Set<Path> nestedPaths = new LinkedHashSet<>();
		for (Claim claim : nestedClaims) {
			nestedPaths.add(claim.jar().toAbsolutePath());
			for (String id : claim.modIds()) byId.computeIfAbsent(id, k -> new ArrayList<>()).add(claim);
		}

		Set<Path> suppressed = new LinkedHashSet<>(phase1.suppressedJars());
		Map<String, Path> owners = new LinkedHashMap<>(phase1.ownerByModId());
		List<Alias> aliases = new ArrayList<>(phase1.aliases());
		int contested = 0;
		for (Map.Entry<String, List<Claim>> e : byId.entrySet()) {
			List<Claim> claimants = e.getValue();
			if (claimants.size() < 2) continue;

			// ONLY a cross-ECOSYSTEM contest. Same-family duplicates are the ordinary shape of JarJar — one
			// library nested by five mods that each bundle it — and both loaders already keep the first and ignore
			// the rest, without taking anything off the classpath. Withdrawing those jars is not a smaller
			// version of this fix, it is a different and much larger change: it took Sodium's NeoForge build off
			// the classpath (its real mod jar is nested inside a wrapper that declares the same id) and its
			// ServiceLoader lookup then failed. What no loader handles, and what this pass exists for, is the
			// SAME id claimed by two ecosystems, because each family only ever deduplicates within itself.
			Set<Ecosystem> families = new LinkedHashSet<>();
			boolean anyNested = false;
			for (Claim claim : claimants) {
				families.add(claim.ecosystem());
				if (nestedPaths.contains(claim.jar().toAbsolutePath())) anyNested = true;
			}
			if (!anyNested || families.size() < 2) continue;

			Claim winner = pick(e.getKey(), claimants, nestedPreference());
			// A top-level jar is past the point of being withdrawn: phase one already handed it to its family's
			// discovery. If the preference would pick a nested jar over one, keep the top-level jar and say so.
			if (nestedPaths.contains(winner.jar().toAbsolutePath())) {
				Claim installed = null;
				for (Claim claim : claimants) {
					if (!nestedPaths.contains(claim.jar().toAbsolutePath())) { installed = claim; break; }
				}
				if (installed != null) {
					ForbricLog.warn("[Forbric/DupeId] '%s' would be taken from the nested %s, but the top-level %s "
							+ "is already loaded and cannot be withdrawn — keeping the top-level one",
							e.getKey(), winner.jar().getFileName(), installed.jar().getFileName());
					winner = installed;
				}
			}

			contested++;
			owners.put(e.getKey(), winner.jar().toAbsolutePath());
			Set<Ecosystem> lost = new LinkedHashSet<>();
			for (Claim claim : claimants) {
				if (claim == winner) continue;
				if (claim.ecosystem() != winner.ecosystem()) lost.add(claim.ecosystem());
				// SUBSET RULE, as in the top-level pass: a jar may only lose if every id it declares is also
				// claimed by someone else, or a library bundling foo + foo_compat is withdrawn because foo alone
				// collided and foo_compat ends up loaded by nobody.
				if (!nestedPaths.contains(claim.jar().toAbsolutePath())) continue;
				List<String> orphaned = new ArrayList<>();
				for (String id : claim.modIds()) {
					List<Claim> others = byId.getOrDefault(id, List.of());
					if (others.size() < 2) orphaned.add(id);
				}
				if (!orphaned.isEmpty()) {
					ForbricLog.warn("[Forbric/DupeId] keeping the nested %s despite losing '%s' — it also declares "
							+ "%s, which nothing else provides", claim.jar().getFileName(), e.getKey(), orphaned);
					continue;
				}
				suppressed.add(claim.jar().toAbsolutePath());
			}
			// The winner's version, but fall back to any claimant that declared one: an alias exists so
			// isModLoaded answers, and a mod comparing the version it gets back against a range is better served
			// by the losing jar's real number than by versionOf's "0" placeholder.
			String version = winner.versionOf(e.getKey());
			if ("0".equals(version)) {
				for (Claim claim : claimants) {
					String declared = claim.versionOf(e.getKey());
					if (!"0".equals(declared)) { version = declared; break; }
				}
			}
			for (Ecosystem ecosystem : lost) aliases.add(new Alias(e.getKey(), ecosystem, version));
			ForbricLog.info("[Forbric/DupeId] nested mod id '%s' is claimed by %d jars across %s — loading %s (%s). "
					+ "Each loader only deduplicates within its own family, so without this it would have been "
					+ "constructed once per ecosystem%s", e.getKey(), claimants.size(), families,
					winner.jar().getFileName(), winner.ecosystem(),
					lost.isEmpty() ? "" : ", aliased into " + lost);
		}
		if (contested == 0) {
			ForbricLog.debug("[Forbric/DupeId] nested pass: %d nested jar(s), no mod id claimed by more than one "
