package net.forbric.kernel.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.json.JsonFormat;

import net.forbric.api.Ecosystem;
import net.forbric.kernel.TestFixtures;
import net.forbric.kernel.TestFixtures.Fixture;

/**
 * Census: every guest mixin of the five compatibility packs, judged as KernelGuestMixinAdapter judges it (MixinFit, then
 * MixinRetarget's plan when that leaves fewer misses) with {@code forbric.mixinFit.liveness} on and off, against the
 * staged merged base and carriers. Pins exactly which mixins change verdict, and says what the final-class ledger will
 * do with each injector that never runs: a Mixin injector with a mandatory count is a CONFIRMED row on the mod
 * ("partly did not run"), a MixinExtras one a SUSPECTED note, one with a zero count nothing.
 *
 * <p>Offline, so it resolves against raw jar bytes rather than the transform chain's (MixinFitReport's divergence) except
 * for the duplicate-lambda prune, the one step that removes methods an injector binds to; each pack's own jars stand in
 * for KernelBoot's guest scan; a multi-loader jar's config is given to the loader the default arbitration picks. The report is written to
 * {@code build/reports/mixin-liveness-census.txt}.
 *
 * <p>And the same judgment set against stock 26.2 ({@link #vanillaAnchorsTheMergedBaseLostArePinned}): every anchor a
 * Fabric mixin names that resolves on vanilla and does not on the merged base after the kernel's transforms and anchor
 * movers — a call whose owner the merge's field widening changed, a call a carrier widened or replaced, a method it
 * reshaped (a name that now binds an overload the handler was not written for), a field it re-typed. That is the class
 * debugify's, CreativeCore's and MoogsStructureLib's mixins fell into (each reached Done on native Fabric and stopped on
 * Forbric). Only fabric-api, which every staged machine has, is ASSERTED: a new fabric-api line fails the build. Every
 * other corpus — the pure-Fabric A/B set, the compat packs — is report-only ({@code FORBRIC_ANCHOR_PACKS}, written to
 * {@code build/reports/vanilla-anchor-census.txt}): nothing fails on it, and someone has to read it.
 */
@ResourceLock("system-properties")
class MixinFitLivenessCensusStagedTest {
	private static final Path MERGED = TestFixtures.stagedRoot().resolve("neoforge-base/patched-mc-neoforge-26.2.jar");
	private static final Path INTEROP = TestFixtures.stagedRoot().resolve("merged-base/forge-runtime-interop.jar");
	private static final Path NEO_RUNTIME = TestFixtures.stagedRoot().resolve("neoforge-runtime/neoforge-runtime.jar");
	private static final Map<String, Path> PACKS = new LinkedHashMap<>();
	static {
		String other = System.getenv("FORBRIC_LIVENESS_PACKS");    // "name=dir;name=dir": census those instead, report only
		if (other != null) {
			for (String pack : other.split(";")) if (pack.contains("=")) PACKS.put(pack.substring(0, pack.indexOf('=')), Path.of(pack.substring(pack.indexOf('=') + 1)));
		} else {
			for (String pack : List.of("sweep90", "mix80", "popular", "random")) PACKS.put(pack, Path.of("build/compat-inputs", pack, "mods"));
			PACKS.put("client-merged-pack", Path.of("run/client-merged-pack/mods"));
		}
	}
	private static final Set<String> STANDARD = Set.of("Inject", "Redirect", "ModifyArg", "ModifyArgs", "ModifyConstant", "ModifyVariable");

	/**
	 * What changes, as {@code config:mixin verdict-off -> verdict-on}, pinned; each was read against the merged bytes. A
	 * PARTIAL that stays PARTIAL gained a "never runs" line. Five methods carry all of the FIT → PARTIAL moves:
	 * {@code Hud.extractHotbarAndDecorations} (NeoForge's HUD layers replaced its one caller), {@code LiquidBlock
	 * .shouldSpreadLiquid} (both carriers' {@code FluidInteractionRegistry.canInteract} replaced it), {@code AxeItem
	 * .getStripped} ({@code getToolModifiedState}), {@code LivingEntity.trapdoorUsableAsLadder} ({@code CommonHooks
	 * .isLivingOnLadder}), and the vanilla-shaped forwarders the merge kept beside NeoForge's overloads, which the merged
	 * callers skip ({@code ItemModelGenerator.bakeSideFaces}, {@code ServerExplosion.hurtEntities}, {@code EffectsInInventory
	 * .extractText}, {@code CropBlock.getGrowthSpeed(Block, …)}); plus {@code Block.tryDropExperience} and
	 * {@code ScreenEffectRenderer.getViewBlockingState}, which the merged callers replaced outright. And NeoForge's
	 * renamed tooltip body {@code ItemStack.addDetailsToTooltipComponents}, which nothing calls: R3 moves malilib's last
	 * tooltip hook and trinkets' attribute-line hook there (carrier-renames.txt marks it UNCALLED), where they bind and
	 * never run.
	 */
	private static final Set<String> EXPECTED = Set.of(
			"apoli.mixins.json:legacy.hud_power.HudMixin FIT -> PARTIAL",
			"architectury.mixins.json:MixinServerExplosion PARTIAL -> PARTIAL",
			"balm.fabric.mixins.json:FabricCropBlockMixin PARTIAL -> PARTIAL",
			"bettermounthud.mixins.json:HudMixin FIT -> PARTIAL",
			"configapi-fabric.mixins.json:event.ServerExplosionMixin PARTIAL -> PARTIAL",
			"fabric-block-api-v1.mixins.json:LivingEntityMixin FIT -> PARTIAL",
			"fabric-renderer-api-v1.mixins.json:block.particle.ScreenEffectRendererMixin FIT -> PARTIAL",
			"fabric-rendering-v1.mixins.json:HudMixin PARTIAL -> PARTIAL",
			"mixins.malilib.json:item.MixinItemStack FIT -> PARTIAL",
			"puzzleslib.fabric.mixins.json:BlockFabricMixin FIT -> PARTIAL",
			"puzzleslib.fabric.mixins.json:ServerExplosionFabricMixin FIT -> PARTIAL",
			"puzzleslib.fabric.mixins.json:client.EffectsInInventoryFabricMixin PARTIAL -> PARTIAL",
			"sodium-fabric.mixins.json:features.render.model.ItemModelGeneratorMixin FIT -> PARTIAL",
			"trinkets.fabric.mixins.json:ItemStackMixin FIT -> PARTIAL");
	/** The same, counted per pack: one mixin changes in every pack that carries it. */
	private static final int EXPECTED_IN_PACKS = 30;

	@AfterEach
	void reset() {
		System.clearProperty(MixinFit.LIVENESS_PROPERTY);
		MixinStubRebind.forget();
		MixinRetarget.reset();
		MergedBaseUncalledMethods.forgetGuests();
	}

	@Test
	void exactlyThePinnedMixinsChangeVerdict() throws Exception {
		for (Path p : List.of(MERGED, INTEROP, NEO_RUNTIME)) TestFixtures.require(Fixture.STAGED, Files.isRegularFile(p), p + " required");
		for (Path p : PACKS.values()) TestFixtures.require(Fixture.THIRD_PARTY, Files.isDirectory(p), p + " required (symlink it from the main checkout)");
		Map<String, byte[]> game = new HashMap<>();
		for (Path jar : List.of(NEO_RUNTIME, INTEROP, MERGED)) game.putAll(read(Files.readAllBytes(jar), n -> n.endsWith(".class")));
		// The one step of the transform chain that removes methods an injector binds to: the merge's orphaned duplicate
		// lambdas, which InsertedLambdaArgumentShim then follows into the live one (litematica, fusion). Raw bytes would
		// report those injectors as bound in dead code; the kernel's resolver never serves them.
		Map<String, byte[]> pruned = new java.util.concurrent.ConcurrentHashMap<>();
		net.forbric.kernel.transform.DuplicateLambdaPruneInjector prune = new net.forbric.kernel.transform.DuplicateLambdaPruneInjector();
		Function<String, byte[]> resolver = name -> {
			byte[] raw = game.get(name);
			if (raw == null || !name.startsWith("net/minecraft/")) return raw;
			return pruned.computeIfAbsent(name, n -> {
				byte[] out = prune.transform(n.substring(0, n.length() - ".class".length()).replace('/', '.'), raw, null);
				return out == null ? raw : out;
			});
		};

		StringBuilder report = new StringBuilder();
		Set<String> changed = new java.util.TreeSet<>();
		Set<String> inPacks = new java.util.TreeSet<>();
		Set<String> seen = new LinkedHashSet<>();
		int scanned = 0;
		Map<String, Integer> ledger = new TreeMap<>();
		for (Map.Entry<String, Path> pack : PACKS.entrySet()) {
			List<Path> jars;
			try (Stream<Path> s = Files.list(pack.getValue())) {
				jars = s.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
			}
			// What KernelBoot's guest scan records for this pack: every installed jar, nested ones included.
			MergedBaseUncalledMethods.forgetGuests();
			Map<Path, Map<String, Map<String, byte[]>>> read = new LinkedHashMap<>();
			for (Path jar : jars) {
				read.put(jar, units(jar));
				for (Map<String, byte[]> unit : read.get(jar).values()) {
					for (Map.Entry<String, byte[]> e : unit.entrySet()) if (e.getKey().endsWith(".class")) MergedBaseUncalledMethods.noteGuest(e.getValue());
				}
			}
			for (Path jar : jars) {
				for (Map.Entry<String, Map<String, byte[]>> unit : read.get(jar).entrySet()) {
					Map<String, byte[]> content = unit.getValue();
					Map<String, Ecosystem> owners = configOwners(content);
					for (Map.Entry<String, Ecosystem> config : owners.entrySet()) {
						byte[] json = content.get(config.getKey());
						UnmodifiableConfig parsed = parse(json);
						if (parsed == null) continue;
						String pkg = String.valueOf(parsed.<Object>get(List.of("package")));
						int minimum = parsed.get(List.of("injectors", "defaultRequire")) instanceof Number n ? n.intValue() : 0;
						for (String entry : entries(parsed)) {
							String path = pkg.replace('.', '/') + "/" + entry.replace('.', '/') + ".class";
							byte[] bytes = content.get(path);
							if (bytes == null || !seen.add(pack.getKey() + "|" + unit.getKey() + "|" + config.getKey() + ":" + entry)) continue;
							scanned++;
							String internal = path.substring(0, path.length() - ".class".length());
							MixinStubRebind.noteEcosystem(internal, config.getValue());
							Judged off = judge(bytes, resolver, "off");
							Judged on = judge(bytes, resolver, "on");
							if (off.equals(on)) continue;
							String id = config.getKey() + ":" + entry;
							changed.add(id + " " + off.verdict() + " -> " + on.verdict());
							inPacks.add(pack.getKey() + " " + id);
							report.append(String.format("%s  %s  %s [%s]%n  off: %s %s%n  on:  %s %s%n", pack.getKey(), unit.getKey(), id,
									config.getValue(), off.verdict(), off.plan(), on.verdict(), on.plan()));
							for (String line : on.unresolved()) if (!off.unresolved().contains(line)) report.append("    + ").append(line).append('\n');
							for (String line : off.unresolved()) if (!on.unresolved().contains(line)) report.append("    - ").append(line).append('\n');
							for (Map.Entry<String, String> handler : ledger(bytes, resolver, minimum, on.rewritten()).entrySet()) {
								report.append("    ledger: ").append(handler.getKey()).append(" → ").append(handler.getValue()).append('\n');
								ledger.merge(handler.getValue(), 1, Integer::sum);
							}
						}
					}
				}
			}
		}
		report.insert(0, String.format("liveness census: %d guest mixins in %d packs; %d change verdict (%d distinct); ledger %s%n%n",
				scanned, PACKS.size(), inPacks.size(), changed.size(), ledger));
		Files.createDirectories(Path.of("build/reports"));
		Files.writeString(Path.of("build/reports/mixin-liveness-census.txt"), report.toString());
		System.out.println(report);
		if (System.getenv("FORBRIC_LIVENESS_PACKS") != null) return;    // someone else's packs: the report is the answer
		assertEquals(EXPECTED, changed, report.toString());
		assertEquals(EXPECTED_IN_PACKS, inPacks.size(), report.toString());
	}

	private record Judged(MixinFit.Verdict verdict, List<String> unresolved, String plan, byte[] rewritten) {
		@Override public boolean equals(Object o) {
			return o instanceof Judged j && j.verdict == verdict && j.unresolved.equals(unresolved) && j.plan.equals(plan);
		}

		@Override public int hashCode() {
			return verdict.hashCode();
		}
	}

	/**
	 * As KernelGuestMixinAdapter: the verdict, and the retarget's when the adapter takes it (MixinRetarget.adopt) — fewer
	 * misses, or fewer outright where one now binds in a method nothing runs.
	 */
	private static Judged judge(byte[] bytes, Function<String, byte[]> resolver, String liveness) {
		System.setProperty(MixinFit.LIVENESS_PROPERTY, liveness);
		MixinFit.Result fit = MixinFit.evaluate(bytes, resolver, net.forbric.kernel.classloading.DelegationPolicy::alwaysGame);
		MixinRetarget.Adoption adoption = MixinRetarget.adopt(bytes, fit, resolver,
				b -> MixinFit.evaluate(b, resolver, net.forbric.kernel.classloading.DelegationPolicy::alwaysGame));
		if (adoption != null) {
			return new Judged(adoption.after().verdict(), adoption.after().unresolved(), "retargeted: " + adoption.plan().describe(),
					adoption.rewritten());
		}
		return new Judged(fit.verdict(), fit.unresolved(), "", bytes);
	}

	/**
	 * Each injector that never runs, and what FinalMixinApplications records for it: every handler judged alone, as the
	 * mixin reached Mixin.
	 */
	private static Map<String, String> ledger(byte[] original, Function<String, byte[]> resolver, int configMinimum, byte[] bytes) {
		System.setProperty(MixinFit.LIVENESS_PROPERTY, "on");
		Map<String, String> out = new LinkedHashMap<>();
		ClassNode mixin = MixinFit.parse(bytes);
		for (MethodNode handler : mixin.methods) {
			AnnotationNode injector = MixinFit.injectorOf(handler);
			if (injector == null) continue;
			ClassNode alone = MixinFit.parse(bytes);
			alone.methods.removeIf(m -> MixinFit.injectorOf(m) != null && !(m.name.equals(handler.name) && m.desc.equals(handler.desc)));
			ClassWriter writer = new ClassWriter(0);
			alone.accept(writer);
			MixinFit.Result fit = MixinFit.evaluate(writer.toByteArray(), resolver);
			if (fit.unresolved().stream().noneMatch(line -> line.contains(" never runs: "))) continue;
			boolean grouped = annotations(handler).stream().anyMatch(a -> a.desc.endsWith("/Group;"));
			boolean sugar = sugar(handler);
			Object require = MixinFit.value(injector, "require");
			int minimum = require instanceof Number n && n.intValue() >= 0 ? n.intValue() : grouped ? 0 : configMinimum;
			String kind = injector.desc.substring(injector.desc.lastIndexOf('/') + 1, injector.desc.length() - 1);
			boolean audited = injector.desc.startsWith("Lorg/spongepowered/") && STANDARD.contains(kind);
			String outcome = minimum == 0 ? "OPTIONAL (no row)"
					: audited && !grouped && !sugar ? "CONFIRMED, not required (mod row: partly did not run)" : "SUSPECTED (note)";
			out.put("@" + kind + " " + handler.name, outcome);
		}
		return out;
	}

	private static List<AnnotationNode> annotations(MethodNode m) {
		List<AnnotationNode> out = new ArrayList<>();
		if (m.visibleAnnotations != null) out.addAll(m.visibleAnnotations);
		if (m.invisibleAnnotations != null) out.addAll(m.invisibleAnnotations);
		return out;
	}

	private static boolean sugar(MethodNode m) {
		for (List<AnnotationNode>[] set : java.util.Arrays.asList(m.visibleParameterAnnotations, m.invisibleParameterAnnotations)) {
			if (set == null) continue;
			for (List<AnnotationNode> p : set) if (p != null) for (AnnotationNode a : p) if (a.desc.startsWith("Lcom/llamalad7/mixinextras/")) return true;
		}
		return false;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Packs

	/** A jar and every jar nested in it (Fabric {@code META-INF/jars}, Forge {@code META-INF/jarjar}), each read whole. */
	static Map<String, Map<String, byte[]>> units(Path jar) throws IOException {
		Map<String, Map<String, byte[]>> units = new LinkedHashMap<>();
		Map<String, byte[]> top = read(Files.readAllBytes(jar), MixinFitLivenessCensusStagedTest::wanted);
		units.put(jar.getFileName().toString(), top);
		for (Map.Entry<String, byte[]> e : top.entrySet()) {
			String name = e.getKey();
			if (name.endsWith(".jar") && (name.startsWith("META-INF/jars/") || name.startsWith("META-INF/jarjar/"))) {
				units.put(jar.getFileName() + "!" + name.substring(name.lastIndexOf('/') + 1), read(e.getValue(), MixinFitLivenessCensusStagedTest::wanted));
			}
		}
		return units;
	}

	private static boolean wanted(String name) {
		return name.endsWith(".class") || name.endsWith(".json") || name.endsWith(".jar") || name.endsWith(".toml") || name.endsWith("MANIFEST.MF");
	}

	static Map<String, byte[]> read(byte[] zip, java.util.function.Predicate<String> wanted) throws IOException {
		Map<String, byte[]> out = new LinkedHashMap<>();
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
