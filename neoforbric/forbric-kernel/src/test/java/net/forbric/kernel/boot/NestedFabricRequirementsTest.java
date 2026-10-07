package net.forbric.kernel.boot;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.*;
import net.fabricmc.api.EnvType;
import net.forbric.api.CompatibilityFindings;
import net.forbric.api.Side;
import net.forbric.kernel.discovery.ForbricModDiscoverer;
import net.forbric.kernel.fabric.FabricModDiscovery;
import net.forbric.kernel.fabric.NestedFabricRequirements;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Nested Fabric mods are resolved as fabric-loader 0.19.5's ModSolver resolves them: a nested build whose
 * {@code minecraft}/{@code java} requirement excludes this game is not loaded, and neither is what needs only it.
 * The shape is ViaFabric 0.4.22's: one parent, one platform layer per Minecraft line under two different ids.
 */
@ResourceLock("ModCatalog") @ResourceLock("system-properties")
class NestedFabricRequirementsTest {
	private static final String GAME = "26.2";
	private static final String JAVA = String.valueOf(Runtime.version().feature());
	private static final String KEPT_BACK = "[Forbric/JiJ] nested host-mc26-1 1 in host.jar loaded although Fabric Loader would "
			+ "leave it out (minecraft >=26.1 <=26.1.2 does not include 26.2): host requires host-mc26-1 >=1, and nothing else "
			+ "installed meets that";
	@TempDir Path root;

	@BeforeEach @AfterEach void reset() {
		DuplicateModArbiter.reset(); MultiLoaderArbiter.reset(); CompatibilityFindings.reset();
		System.clearProperty(NestedFabricRequirements.SWITCH); System.clearProperty("forbric.crossJarArbitration");
	}

	@Test void aNestedBuildForAnotherMinecraftLineIsLeftOutAndItsSiblingLoads() throws Exception {
		installViaFabricShape();
		var decision = new DuplicateModArbiter.Decision[1];
		String log = capture(() -> decision[0] = DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME));
		// One line for each left-out mod, the one it bundles included.
		assertEquals(List.of("[Forbric/JiJ] nested host-mc26-1 1 in host.jar left out: minecraft >=26.1 <=26.1.2 does not "
				+ "include 26.2 — Fabric Loader does not load it either", "[Forbric/JiJ] nested inner 1 in host-mc26-1.jar left out: only mods left out bundle it — Fabric Loader does not "
				+ "load it either"), jijLines(log));
		var plan = DuplicateModArbiter.currentPlan();
		assertEquals(Set.of("host-mc26-2"), nestedIds(plan));
		Path old = only(plan, "host-mc26-1");
		assertTrue(plan.inventory().nodes().get(old).excluded());
		assertTrue(decision[0].suppressed(old), "the left-out build must not reach the classpath");
		assertFalse(decision[0].rescueJars().contains(old), "nor be lent a class as a last resort");
		assertEquals(Map.of(old, "minecraft >=26.1 <=26.1.2 does not include 26.2", only(plan, "inner"),
				"only mods left out bundle it"), plan.inventory().leftOut());
		assertTrue(plan.inventory().keptBack().isEmpty());
		FabricModDiscovery discovery = new FabricModDiscovery(EnvType.SERVER, root.resolve("legacy-cache"));
		discovery.discover(mods());
		assertEquals(Set.of("host", "host-mc26-2"), ids(discovery));
		assertTrue(plan.verify(discovery.getClasspathJars()));
	}

	@Test void theOffSwitchLoadsEveryNestedBuildAgain() throws Exception {
		installViaFabricShape();
		System.setProperty(NestedFabricRequirements.SWITCH, "off");
		DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME);
		assertEquals(Set.of("host-mc26-1", "inner", "host-mc26-2"), nestedIds(DuplicateModArbiter.currentPlan()));
		assertTrue(DuplicateModArbiter.currentPlan().inventory().leftOut().isEmpty());
	}

	@Test void discoveryWithoutAPlanAppliesTheSameRule() throws Exception {
		installViaFabricShape();
		System.setProperty("forbric.crossJarArbitration", "off");
		DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME);
		assertNull(DuplicateModArbiter.planned(mods(), EnvType.SERVER));
		FabricModDiscovery discovery = new FabricModDiscovery(EnvType.SERVER, root.resolve("jij"));
		discovery.setPlatform(NestedFabricRequirements.Platform.running(GAME));
		String log = capture(() -> discovery.discover(mods()));
		assertEquals(Set.of("host", "host-mc26-2"), ids(discovery));
		assertEquals(2, discovery.getClasspathJars().size());
		assertEquals(List.of("[Forbric/JiJ] nested host-mc26-1 1 in host.jar left out: minecraft >=26.1 <=26.1.2 does not "
				+ "include 26.2 — Fabric Loader does not load it either", "[Forbric/JiJ] nested inner 1 in host-mc26-1.jar left out: only mods left out bundle it — Fabric Loader does not "
				+ "load it either"), jijLines(log));
	}

	@Test void whatOnlyALeftOutModProvidesOrBundlesIsLeftOutWithIt() throws Exception {
		byte[] addon = fabric("addon", "1", Map.of("META-INF/jars/addonlib.jar", fabric("addonlib", "1", Map.of(), "")),
				",\"depends\":{\"host-mc26-1\":\"*\"}");
		byte[] compat = fabric("compat", "1", Map.of(), ",\"depends\":{\"nobody-installed-this\":\"*\"}");
		install("host.jar", fabric("host", "1", Map.of("META-INF/jars/mc26-1.jar", mc261(), "META-INF/jars/addon.jar", addon,
				"META-INF/jars/compat.jar", compat), ""));
		DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME);
		var plan = DuplicateModArbiter.currentPlan();
		// A dependency nothing installed meets is DependencyAudit's to report, as it was: only what Fabric Loader
		// provably could not load here is left out.
		assertEquals(Set.of("compat"), nestedIds(plan));
		assertEquals("it requires host-mc26-1 *, and every installed build of that is left out",
				plan.inventory().leftOut().get(only(plan, "addon")));
		assertEquals("only mods left out bundle it", plan.inventory().leftOut().get(only(plan, "addonlib")));
		assertEquals("only mods left out bundle it", plan.inventory().leftOut().get(only(plan, "inner")));
		FabricModDiscovery legacy = new FabricModDiscovery(EnvType.SERVER, root.resolve("jij"));
		legacy.setPlatform(NestedFabricRequirements.Platform.running(GAME));
		DuplicateModArbiter.reset();
		legacy.discover(mods());
		assertEquals(Set.of("host", "compat"), ids(legacy));
	}

	@Test void aNewerNestedCopyThisGameCannotRunDoesNotDisplaceAnOlderOneItCan() throws Exception {
		install("a.jar", fabric("a", "1", Map.of("META-INF/jars/lib.jar", fabric("lib", "1", Map.of(), "")), ""));
		install("b.jar", fabric("b", "1", Map.of("META-INF/jars/lib.jar", fabric("lib", "2", Map.of(),
				",\"depends\":{\"minecraft\":\"~26.1\"}")), ""));
		DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME);
		var plan = DuplicateModArbiter.currentPlan();
		assertEquals(List.of("1"), plan.nestedFiles().stream().map(p -> plan.inventory().nodes().get(p).claim().versionOf("lib")).toList());
	}

	@Test void theNewestNestedCopyStillWinsWhenEveryCopyCanRun() throws Exception {
		install("a.jar", fabric("a", "1", Map.of("META-INF/jars/lib.jar", fabric("lib", "1", Map.of(), "")), ""));
		install("b.jar", fabric("b", "1", Map.of("META-INF/jars/lib.jar", fabric("lib", "2", Map.of(),
				",\"depends\":{\"minecraft\":\"~26.2\"}")), ""));
		DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME);
		var plan = DuplicateModArbiter.currentPlan();
		assertEquals(List.of("2"), plan.nestedFiles().stream().map(p -> plan.inventory().nodes().get(p).claim().versionOf("lib")).toList());
		assertTrue(plan.inventory().leftOut().isEmpty());
	}

	@Test void aJarInModsIsNeverLeftOut() throws Exception {
		Path chosen = install("old.jar", mc261());
		var decision = DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME);
		assertFalse(decision.suppressed(chosen));
		assertTrue(DuplicateModArbiter.currentPlan().selected().contains(chosen.toAbsolutePath()));
		assertTrue(DuplicateModArbiter.currentPlan().inventory().leftOut().isEmpty());
	}

	@Test void javaAndBreaksAreJudgedButAnUnknownGameAnUnreadableRangeAndTheLoaderLevelAreNot() throws Exception {
		install("host.jar", fabric("host", "1", Map.of(
				"META-INF/jars/java.jar", fabric("needs-java-99", "1", Map.of(), ",\"depends\":{\"java\":\">=99\"}"),
				"META-INF/jars/breaks.jar", fabric("breaks-this-game", "1", Map.of(), ",\"breaks\":{\"minecraft\":\"26.2\"}"),
				"META-INF/jars/garbled.jar", fabric("garbled-range", "1", Map.of(), ",\"depends\":{\"minecraft\":\"<<26.1!\"}"),
				"META-INF/jars/loader.jar", fabric("newer-loader", "1", Map.of(), ",\"depends\":{\"fabricloader\":\">=9.0.0\"}"),
				"META-INF/jars/mc26-1.jar", mc261()), ""));
		DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME);
		var plan = DuplicateModArbiter.currentPlan();
		assertEquals(Set.of("garbled-range", "newer-loader"), nestedIds(plan));
		assertEquals("java >=99 does not include " + JAVA, plan.inventory().leftOut().get(only(plan, "needs-java-99")));
		assertEquals("it breaks minecraft 26.2, which includes 26.2", plan.inventory().leftOut().get(only(plan, "breaks-this-game")));

		DuplicateModArbiter.reset();
		DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER);
		assertTrue(nestedIds(DuplicateModArbiter.currentPlan()).contains("host-mc26-1"), "no game version, no minecraft verdict");
	}

	/**
	 * A NeoForge mod's nested jar is not Fabric Loader's to judge, even when all it carries is a fabric.mod.json:
	 * Fabric Loader never opens a jar without one. FML loads the child its metadata.json declares as the parent's
	 * library; the one that only sits in META-INF/jars no native loader loads, and the kernel keeps it as it always
	 * has. Neither is left out, whichever way the walk reached it.
	 */
	@Test void aForgeFamilyParentsJarJarChildIsNotFabricLoadersToJudge() throws Exception {
		byte[] somelib = fabric("somelib", "1.0.0", Map.of(), ",\"depends\":{\"minecraft\":\"~26.1\"}");
		byte[] otherlib = fabric("otherlib", "1.0.0", Map.of(), ",\"depends\":{\"minecraft\":\"~26.1\"}");
		install("neoparent.jar", neo("neoparent", Map.of("META-INF/jarjar/somelib.jar", somelib, "META-INF/jars/otherlib.jar", otherlib),
				List.of("META-INF/jarjar/somelib.jar")));
		install("host.jar", fabric("host", "1", Map.of("META-INF/jars/mc26-1.jar", mc261()), ""));
		String log = capture(() -> DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME));
		var plan = DuplicateModArbiter.currentPlan();
		assertEquals(Set.of("somelib", "otherlib"), nestedIds(plan));
		assertEquals(Set.of(only(plan, "host-mc26-1"), only(plan, "inner")), plan.inventory().leftOut().keySet());
		assertFalse(plan.inventory().nodes().get(only(plan, "somelib")).excluded());
		assertEquals(List.of("[Forbric/JiJ] nested host-mc26-1 1 in host.jar left out: minecraft >=26.1 <=26.1.2 does not "
				+ "include 26.2 — Fabric Loader does not load it either", "[Forbric/JiJ] nested inner 1 in mc26-1.jar left out: "
				+ "only mods left out bundle it — Fabric Loader does not load it either"), jijLines(log));
	}

	/** One jar, two parents: a Fabric mod's jars and a NeoForge mod's JarJar. It is kept whichever is walked first. */
	@Test void aJarAForgeFamilyParentAlsoBundlesIsKeptWhicheverRouteIsWalkedFirst() throws Exception {
		for (boolean fabricFirst : List.of(true, false)) {
			reset();
			try (var files = Files.list(mods())) { for (Path file : files.toList()) Files.delete(file); }
			install((fabricFirst ? "a" : "b") + "-host.jar", fabric("host", "1", Map.of("META-INF/jars/mc26-1.jar", mc261()), ""));
			install((fabricFirst ? "b" : "a") + "-neoparent.jar", neo("neoparent", Map.of("META-INF/jarjar/mc26-1.jar", mc261()),
					List.of("META-INF/jarjar/mc26-1.jar")));
			String log = capture(() -> DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME));
			var plan = DuplicateModArbiter.currentPlan();
			assertEquals(Set.of("host-mc26-1", "inner"), nestedIds(plan), "fabric first: " + fabricFirst);
			assertTrue(plan.inventory().leftOut().isEmpty(), "fabric first: " + fabricFirst);
			assertEquals(List.of(), jijLines(log), "fabric first: " + fabricFirst);
		}
	}

	/**
	 * A Fabric mod the kernel loads as one, even inside a NeoForge mod's JarJar, has its own jars resolved by Fabric's
	 * rule: that declaration is a Fabric mod's, and Fabric Loader is what reads it.
	 */
	@Test void aFabricModInsideAForgeFamilyModStillHasItsOwnJarsJudged() throws Exception {
		byte[] host = fabric("host", "1", Map.of("META-INF/jars/mc26-1.jar", mc261()), "");
		install("neoparent.jar", neo("neoparent", Map.of("META-INF/jarjar/host.jar", host), List.of("META-INF/jarjar/host.jar")));
		DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME);
		var plan = DuplicateModArbiter.currentPlan();
		assertEquals(Set.of("host"), nestedIds(plan));
		assertEquals(Map.of(only(plan, "host-mc26-1"), "minecraft >=26.1 <=26.1.2 does not include 26.2", only(plan, "inner"),
				"only mods left out bundle it"), plan.inventory().leftOut());
	}

	/**
	 * Native refuses to start when a jar in mods/ hard-depends on a nested mod it left out. The kernel loads that jar
	 * anyway, so leaving its dependency out would only break it: the nested mod is kept, with what it bundles, and a
	 * WARN says so. Both with the plan and without one.
	 */
	@Test void aModInModsThatRequiresALeftOutNestedModKeepsIt() throws Exception {
		Path host = install("host.jar", fabric("host", "1", Map.of("META-INF/jars/mc26-1.jar", mc261()),
				",\"depends\":{\"host-mc26-1\":\">=1\"}"));
		String log = capture(() -> DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME));
		var plan = DuplicateModArbiter.currentPlan();
		assertEquals(Set.of("host-mc26-1", "inner"), nestedIds(plan));
		assertTrue(plan.inventory().leftOut().isEmpty());
		assertEquals(Map.of(only(plan, "host-mc26-1"), new NestedFabricRequirements.KeptBack(
				"minecraft >=26.1 <=26.1.2 does not include 26.2", "host requires host-mc26-1 >=1, and nothing else installed "
				+ "meets that")), plan.inventory().keptBack());
		assertEquals(List.of(KEPT_BACK), jijLines(log));
		assertFalse(plan.inventory().nodes().get(only(plan, "host-mc26-1")).excluded());

		var present = new ForbricModDiscoverer().discoverJar(host);
		String audit = capture(() -> DependencyAudit.report(present, plan.nestedFiles(), Side.DEDICATED_SERVER));
		assertTrue(audit.contains("every hard dependency of 1 mod(s) is present and in range"), audit);

		System.setProperty("forbric.crossJarArbitration", "off");
		DuplicateModArbiter.reset();
		FabricModDiscovery discovery = new FabricModDiscovery(EnvType.SERVER, root.resolve("jij"));
		discovery.setPlatform(NestedFabricRequirements.Platform.running(GAME));
		String legacy = capture(() -> discovery.discover(mods()));
		assertEquals(Set.of("host", "host-mc26-1", "inner"), ids(discovery));
		assertEquals(List.of(KEPT_BACK), jijLines(legacy));
	}

	/** The same from another ecosystem: a NeoForge mod in mods/, and a Fabric jar a NeoForge mod's JarJar carries. */
	@Test void aForgeFamilyModOrItsJarJarChildThatRequiresALeftOutNestedModKeepsIt() throws Exception {
		for (boolean viaJarJarChild : List.of(false, true)) {
			reset();
			try (var files = Files.list(mods())) { for (Path file : files.toList()) Files.delete(file); }
			install("host.jar", fabric("host", "1", Map.of("META-INF/jars/mc26-1.jar", mc261()), ""));
			Path neoparent = install("neoparent.jar", viaJarJarChild
					? neo("neoparent", Map.of("META-INF/jarjar/needs-old.jar", fabric("needs-old", "1", Map.of(),
							",\"depends\":{\"host-mc26-1\":\"*\"}")), List.of("META-INF/jarjar/needs-old.jar"), "")
					: neo("neoparent", Map.of(), List.of(), "[[dependencies.neoparent]]\nmodId=\"host-mc26-1\"\ntype=\"required\"\n"
							+ "versionRange=\"[1,)\"\n"));
			DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME);
			var plan = DuplicateModArbiter.currentPlan();
			assertTrue(nestedIds(plan).contains("host-mc26-1"), "via JarJar child: " + viaJarJarChild);
			assertEquals((viaJarJarChild ? "needs-old requires host-mc26-1 *" : "neoparent requires host-mc26-1 >=1")
					+ ", and nothing else installed meets that", plan.inventory().keptBack().get(only(plan, "host-mc26-1")).because());

			// Without a plan, through the boot's own seam: Fabric discovery reads mods/ and Fabric jars only, so the
			// NeoForge mod's requirement, and the Fabric jar its JarJar carries, must be handed over to it.
			System.setProperty("forbric.crossJarArbitration", "off");
			DuplicateModArbiter.reset();
			var decision = DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME);
			assertNull(DuplicateModArbiter.currentPlan());
			List<Path> forgeFamily = KernelBoot.discoverForgeFamilyModJars(mods(), decision).jars();
			assertEquals(List.of(neoparent), forgeFamily);
			List<Path> jarJar = KernelBoot.extractForgeFamilyJarJar(forgeFamily, root);
			assertEquals(viaJarJarChild ? 1 : 0, jarJar.size());
			FabricModDiscovery discovery = KernelBoot.scanFabricMods(KernelBoot.Side.SERVER, root, decision, GAME, null,
					forgeFamily, jarJar);
			assertEquals(Set.of("host", "host-mc26-1", "inner"), ids(discovery), "via JarJar child: " + viaJarJarChild);
			System.clearProperty("forbric.crossJarArbitration");
		}
	}

	/**
	 * When no build installed meets what a loaded mod asks for and nothing that loads provides the id at all, the
	 * left-out copy is kept, as the kernel loaded it before the rule, and the WARN does not claim it meets the range.
	 * When a left-out copy does meet it, only that one is kept.
	 */
	@Test void aKeptCopyOutsideTheRequiredRangeIsSaidToBeOne() throws Exception {
		byte[] x2 = fabric("x", "2", Map.of(), ",\"depends\":{\"minecraft\":\"~26.1\"}");
		install("r.jar", fabric("r", "1", Map.of("META-INF/jars/x.jar", x2), ",\"depends\":{\"x\":\">=3\"}"));
		String outOfRange = "[Forbric/JiJ] nested x 2 in r.jar loaded although Fabric Loader would leave it out (minecraft "
				+ "~26.1 does not include 26.2): r requires x >=3; no installed build meets that, and no other x would load";
		String log = capture(() -> DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME));
		var plan = DuplicateModArbiter.currentPlan();
		assertEquals(Set.of("x"), nestedIds(plan));
		assertTrue(plan.inventory().leftOut().isEmpty());
		assertEquals(List.of(outOfRange), jijLines(log));

		System.setProperty("forbric.crossJarArbitration", "off");
		DuplicateModArbiter.reset();
		FabricModDiscovery discovery = new FabricModDiscovery(EnvType.SERVER, root.resolve("jij"));
		discovery.setPlatform(NestedFabricRequirements.Platform.running(GAME));
		String legacy = capture(() -> discovery.discover(mods()));
		assertEquals(Set.of("r", "x"), ids(discovery));
		assertEquals(List.of(outOfRange), jijLines(legacy));
		System.clearProperty("forbric.crossJarArbitration");

		// A second left-out copy that does meet the range: it alone comes back, and x 2 stays out.
		DuplicateModArbiter.reset();
		install("s.jar", fabric("s", "1", Map.of("META-INF/jars/x.jar", fabric("x", "3", Map.of(),
				",\"depends\":{\"minecraft\":\"~26.1\"}")), ""));
		capture(() -> DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME));
		var both = DuplicateModArbiter.currentPlan();
		Path three = both.inventory().nodes().values().stream().filter(n -> n.claim() != null && "3".equals(n.claim().versionOf("x")))
				.map(NestedCandidateInventory.Node::path).findFirst().orElseThrow();
		Path two = both.inventory().nodes().values().stream().filter(n -> n.claim() != null && "2".equals(n.claim().versionOf("x")))
				.map(NestedCandidateInventory.Node::path).findFirst().orElseThrow();
		assertEquals(Set.of(three), both.inventory().keptBack().keySet());
		assertEquals("r requires x >=3, and nothing else installed meets that", both.inventory().keptBack().get(three).because());
		assertEquals(Map.of(two, "minecraft ~26.1 does not include 26.2"), both.inventory().leftOut());
		assertEquals(List.of("3"), both.nestedFiles().stream().map(p -> both.inventory().nodes().get(p).claim().versionOf("x")).toList());
	}

	/** What a left-out mod bundles is kept, and the left-out mod with it, when a mod that loads needs it. */
	@Test void aModThatNeedsWhatALeftOutModBundlesKeepsTheChain() throws Exception {
		installViaFabricShape();
		install("user.jar", fabric("user", "1", Map.of(), ",\"depends\":{\"inner\":\"*\"}"));
		DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME);
		var plan = DuplicateModArbiter.currentPlan();
		assertEquals(Set.of("host-mc26-1", "inner", "host-mc26-2"), nestedIds(plan));
		assertEquals(Map.of(only(plan, "inner"), new NestedFabricRequirements.KeptBack("only mods left out bundle it",
				"user requires inner *, and nothing else installed meets that"), only(plan, "host-mc26-1"),
				new NestedFabricRequirements.KeptBack("minecraft >=26.1 <=26.1.2 does not include 26.2",
						"it bundles inner (user requires inner *, and nothing else installed meets that)")), plan.inventory().keptBack());
		assertTrue(plan.inventory().leftOut().isEmpty());

		System.setProperty("forbric.crossJarArbitration", "off");
		DuplicateModArbiter.reset();
		FabricModDiscovery discovery = new FabricModDiscovery(EnvType.SERVER, root.resolve("jij"));
		discovery.setPlatform(NestedFabricRequirements.Platform.running(GAME));
		discovery.discover(mods());
		assertEquals(Set.of("host", "host-mc26-1", "inner", "host-mc26-2", "user"), ids(discovery));
	}

	/**
	 * A left-out copy is kept when only it meets what a loaded mod asks for: here lib 2, beside a lib 1 that can run.
	 * Arbitration then picks between them as it did before the rule, and the newest wins.
	 */
	@Test void aLeftOutCopyIsKeptWhenOnlyItMeetsALoadedModsRange() throws Exception {
		install("a.jar", fabric("a", "1", Map.of("META-INF/jars/lib.jar", fabric("lib", "1", Map.of(), "")), ""));
		install("b.jar", fabric("b", "1", Map.of("META-INF/jars/lib.jar", fabric("lib", "2", Map.of(),
				",\"depends\":{\"minecraft\":\"~26.1\"}")), ""));
		install("c.jar", fabric("c", "1", Map.of(), ",\"depends\":{\"lib\":\">=2\"}"));
		DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME);
		var plan = DuplicateModArbiter.currentPlan();
		assertEquals(List.of("2"), plan.nestedFiles().stream().map(p -> plan.inventory().nodes().get(p).claim().versionOf("lib")).toList());
		assertEquals("c requires lib >=2, and nothing else installed meets that",
				plan.inventory().keptBack().values().iterator().next().because());
	}

	/** {@code off:<ids>} keeps exactly those ids out of the rule, for the player who needs one nested build anyway. */
	@Test void aPinnedIdIsKeptAndEveryOtherStillJudged() throws Exception {
		install("host.jar", fabric("host", "1", Map.of("META-INF/jars/mc26-1.jar", mc261(),
				"META-INF/jars/java.jar", fabric("needs-java-99", "1", Map.of(), ",\"depends\":{\"java\":\">=99\"}")), ""));
		System.setProperty(NestedFabricRequirements.SWITCH, "off: host-mc26-1 ,unrelated");
		DuplicateModArbiter.arbitrate(mods(), EnvType.SERVER, GAME);
		var plan = DuplicateModArbiter.currentPlan();
		assertEquals(Set.of("host-mc26-1", "inner"), nestedIds(plan));
		assertEquals(Set.of(only(plan, "needs-java-99")), plan.inventory().leftOut().keySet());

		System.setProperty("forbric.crossJarArbitration", "off");
		DuplicateModArbiter.reset();
		FabricModDiscovery discovery = new FabricModDiscovery(EnvType.SERVER, root.resolve("jij"));
		discovery.setPlatform(NestedFabricRequirements.Platform.running(GAME));
		discovery.discover(mods());
		assertEquals(Set.of("host", "host-mc26-1", "inner"), ids(discovery));
	}

	private void installViaFabricShape() throws Exception {
		byte[] current = fabric("host-mc26-2", "1", Map.of(), ",\"depends\":{\"minecraft\":[\"26.2\",\"26.3\"],\"host\":\"*\"}");
		install("host.jar", fabric("host", "1", Map.of("META-INF/jars/host-mc26-2.jar", current, "META-INF/jars/host-mc26-1.jar", mc261()),
				",\"depends\":{\"minecraft\":[\"26.1\",\"26.1.1\",\"26.1.2\",\"26.2\",\"26.3\"]}"));
	}
	/** Built for the previous Minecraft line, with a nested jar of its own. */
	private static byte[] mc261() throws Exception {
		return fabric("host-mc26-1", "1", Map.of("META-INF/jars/inner.jar", fabric("inner", "1", Map.of(), "")),
				",\"depends\":{\"minecraft\":\">=26.1 <=26.1.2\"}");
	}
	private Path mods() throws Exception { Path mods = root.resolve("mods"); Files.createDirectories(mods); return mods; }
	private Path install(String name, byte[] bytes) throws Exception { Path path = mods().resolve(name); Files.write(path, bytes); return path; }
	private static Set<String> nestedIds(NestedCandidatePlan plan) {
		return plan.nestedFiles().stream().flatMap(p -> plan.inventory().nodes().get(p).claim().modIds().stream()).collect(Collectors.toSet());
	}
	private static Path only(NestedCandidatePlan plan, String id) {
		List<Path> matches = plan.inventory().nodes().values().stream().filter(n -> n.claim() != null && n.claim().modIds().contains(id))
				.map(NestedCandidateInventory.Node::path).toList();
		assertEquals(1, matches.size(), id);
		return matches.getFirst();
	}
	private static List<String> jijLines(String log) {
		return log.lines().filter(line -> line.contains("[Forbric/JiJ] nested")).map(line -> line.substring(line.indexOf("[Forbric/JiJ]"))).toList();
	}
	private interface Body { void run() throws Exception; }
	private static String capture(Body body) throws Exception {
		PrintStream out = System.out, err = System.err;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		PrintStream sink = new PrintStream(buffer, true, StandardCharsets.UTF_8);
		System.setOut(sink); System.setErr(sink);
		try { body.run(); } finally { System.setOut(out); System.setErr(err); }
		return buffer.toString(StandardCharsets.UTF_8);
	}
	/** A NeoForge mod whose JarJar metadata declares {@code declared}; the other children are only in its directories. */
	private static byte[] neo(String id, Map<String, byte[]> children, List<String> declared) throws Exception {
		return neo(id, children, declared, "");
	}
	/** As above; {@code toml} is appended to its neoforge.mods.toml (its dependencies). */
	private static byte[] neo(String id, Map<String, byte[]> children, List<String> declared, String toml) throws Exception {
		Map<String, byte[]> all = new LinkedHashMap<>(children);
		all.put("META-INF/neoforge.mods.toml", ("modLoader=\"javafml\"\nloaderVersion=\"[1,)\"\nlicense=\"MIT\"\n[[mods]]\nmodId=\""
				+ id + "\"\nversion=\"1\"\n" + toml).getBytes(StandardCharsets.UTF_8));
		List<String> entries = new ArrayList<>();
		for (String path : declared) {
			String artifact = path.substring(path.lastIndexOf('/') + 1).replace(".jar", "");
			entries.add("{\"path\":\"" + path + "\",\"identifier\":{\"group\":\"example\",\"artifact\":\"" + artifact
					+ "\"},\"version\":{\"range\":\"[1,)\",\"artifactVersion\":\"1\"}}");
		}
		all.put("META-INF/jarjar/metadata.json", ("{\"jars\":[" + String.join(",", entries) + "]}").getBytes(StandardCharsets.UTF_8));
		return zip(all);
	}
	private static Set<String> ids(FabricModDiscovery discovery) {
		return discovery.getContainers().stream().map(c -> c.getMetadata().getId()).collect(Collectors.toSet());
	}
	private static byte[] fabric(String id, String version, Map<String, byte[]> children, String extra) throws Exception {
		Map<String, byte[]> all = new LinkedHashMap<>(children);
		String jars = String.join(",", children.keySet().stream().map(name -> "{\"file\":\"" + name + "\"}").toList());
		all.put("fabric.mod.json", ("{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"" + version + "\",\"jars\":[" + jars + "]" + extra + "}")
				.getBytes(StandardCharsets.UTF_8));
		return zip(all);
	}
	private static byte[] zip(Map<String, byte[]> all) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (var entry : new TreeMap<>(all).entrySet()) {
				ZipEntry part = new ZipEntry(entry.getKey()); part.setTime(0); zip.putNextEntry(part); zip.write(entry.getValue()); zip.closeEntry();
			}
		}
		return bytes.toByteArray();
	}
}
