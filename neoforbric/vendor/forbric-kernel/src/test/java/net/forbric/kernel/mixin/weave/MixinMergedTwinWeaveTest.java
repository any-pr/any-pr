package net.forbric.kernel.mixin.weave;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import net.fabricmc.api.EnvType;
import net.forbric.api.Ecosystem;
import net.forbric.kernel.mixin.MixinMergedTwin;

/**
 * {@link MixinMergedTwin} through the real weave: a guest mixin naming the vanilla anonymous class also reaches the
 * merge's renamed {@code $forbricneo} twin, which is the copy the running code instantiates.
 *
 * <p>The fixture is Bad Packets' shape in miniature. {@code Payloads$1} is vanilla's anonymous codec and
 * {@code Payloads$1$forbricneo} its renamed twin; {@code Payloads.live()} hands out the twin. The mod's mixin, written
 * against vanilla, targets {@code Payloads$1} by String, adds {@code ChannelHolder}, pins its INVOKE point to the
 * vanilla owner and shadows a method with the default {@code remap}. The probe is the mod's own code: it encodes
 * through both halves and then casts the live codec to {@code ChannelHolder}.
 *
 * <p>All three halves of the adapter are on the observable path. Without the added target the twin is untouched
 * and the cast fails with the ClassCastException Bad Packets died of. Without the unpinned owner the twin gets the
 * handler but no call to it. Without the unmapped shadow Mixin rejects the now two-target mixin and drops it from
 * BOTH classes.
 */
class MixinMergedTwinWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/mergedtwin");
	private static final String CONFIG = "mergedtwin.mixins.json";
	private static final String MOD = "mergedtwin";
	private static final String VANILLA = "fixture/mergedtwin/Payloads$1";
	private static final String TWIN = VANILLA + MixinMergedTwin.NEO_SUFFIX;
	private static final String HOLDER = "fixture/mergedtwin/mod/ChannelHolder";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result twinned;
	private static WeaveHarness.Result off;
	private static Path descriptorFixture;
	private static WeaveHarness.Result descriptorSpelled;

	@BeforeAll static void weaveWithAndWithoutTheTwinPass() throws Exception {
		fixture = WeaveHarness.fixture(work, "mergedtwin", List.of(
				SOURCES.resolve("fixture/mergedtwin/PayloadCodec.java"),
				SOURCES.resolve("fixture/mergedtwin/Payloads.java"),
				SOURCES.resolve("fixture/mergedtwin/Payloads$1$forbricneo.java"),
				SOURCES.resolve("fixture/mergedtwin/mod/ChannelHolder.java"),
				SOURCES.resolve("fixture/mergedtwin/mod/Probe.java"),
				SOURCES.resolve("fixture/mergedtwin/mod/mixin/PayloadsCodecMixin.java")),
				Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		twinned = run("twinned", "on");
		off = run("twin-off", "off");

		// The same mixin with its INVOKE owner written the other way Mixin accepts, `Lowner;name(desc)`: the spelling
		// MinecraftDev generates, and the one fabric-networking-api-v1's CustomPayloadStreamCodecMixin uses on the
		// very class this adapter twins (CustomPacketPayload$1#findCodec).
		Path mixin = SOURCES.resolve("fixture/mergedtwin/mod/mixin/PayloadsCodecMixin.java");
		String dotted = "target = \"fixture/mergedtwin/Payloads$1.findCodec(";
		String source = java.nio.file.Files.readString(mixin);
		assertTrue(source.contains(dotted), "the fixture's INVOKE target changed; update this variant");
		Path variant = java.nio.file.Files.createDirectories(work.resolve("descriptor-src")).resolve("PayloadsCodecMixin.java");
		java.nio.file.Files.writeString(variant, source.replace(dotted, "target = \"Lfixture/mergedtwin/Payloads$1;findCodec("));
		descriptorFixture = WeaveHarness.fixture(work, "mergedtwin-descriptor", List.of(
				SOURCES.resolve("fixture/mergedtwin/PayloadCodec.java"),
				SOURCES.resolve("fixture/mergedtwin/Payloads.java"),
				SOURCES.resolve("fixture/mergedtwin/Payloads$1$forbricneo.java"),
				SOURCES.resolve("fixture/mergedtwin/mod/ChannelHolder.java"),
				SOURCES.resolve("fixture/mergedtwin/mod/Probe.java"),
				variant),
				Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		descriptorSpelled = WeaveHarness.run(work, "twinned-descriptor", descriptorFixture, CONFIG, MOD, Ecosystem.FABRIC,
				EnvType.SERVER, "fixture.mergedtwin.mod.Probe", "run", Map.of(MixinMergedTwin.PROPERTY, "on"));
	}

	/** An owner pinned as {@code Lowner;name(desc)} is unpinned too, so the twin that runs gets the call, not just the handler. */
	@Test void aDescriptorSpelledOwnerIsUnpinnedToo() throws Exception {
		assertTrue(descriptorSpelled.printed("[MergedTwin] live encode=woven:hello"), descriptorSpelled.describe());
		assertTrue(descriptorSpelled.printed("[MergedTwin] vanilla encode=woven:hello"), descriptorSpelled.describe());
		assertTrue(twinHolds(descriptorSpelled), descriptorSpelled.describe());
		WeaveHarness.assertWovenAndVerified(descriptorSpelled, TWIN, descriptorFixture);
		assertEquals(List.of(), losses(descriptorSpelled), descriptorSpelled.describe());
	}

	@Test void theRenamedTwinThatRunsCarriesTheMixin() throws Exception {
		// What the running code did: the twin's encode went through the handler, and the mod's cast succeeded and
		// reached the shadowed method.
		assertTrue(twinned.printed("[MergedTwin] live encode=woven:hello"), twinned.describe());
		assertTrue(twinned.printed(WeaveHarnessMain.DONE + " channel-plain"), twinned.describe());
		// Added, never substituted: the vanilla-named class is still real and still woven.
		assertTrue(twinned.printed("[MergedTwin] vanilla encode=woven:hello"), twinned.describe());

		for (String woven : List.of(VANILLA, TWIN)) {
			assertTrue(WeaveHarness.hasMergedMethod(twinned.defined(woven)), woven + " — " + twinned.describe());
			assertTrue(interfaces(twinned, woven).contains(HOLDER), woven + " " + interfaces(twinned, woven));
			WeaveHarness.assertWovenAndVerified(twinned, woven, fixture);
		}
		// defaultRequire 1 holds on both targets: the unpinned point found its call in the twin too.
		assertEquals(List.of(), losses(twinned), twinned.describe());
	}

	@Test void withTheSwitchOffTheMixinBindsToTheHalfNothingCalls() throws Exception {
		// The bootstrap ran and the mixin applied — to the vanilla-named class only.
		assertTrue(off.printed("[MergedTwin] vanilla encode=woven:hello"), off.describe());
		assertTrue(WeaveHarness.hasMergedMethod(off.defined(VANILLA)), off.describe());
		WeaveHarness.assertWovenAndVerified(off, VANILLA, fixture);

		// The live codec is untouched, and the mod's cast fails the way Bad Packets' did at world join.
		assertTrue(off.printed("[MergedTwin] live encode=plain:hello"), off.describe());
		assertTrue(off.printed(WeaveHarnessMain.THREW + "java.lang.ClassCastException"), off.describe());
		assertTrue(off.printed("Payloads$1$forbricneo cannot be cast to class fixture.mergedtwin.mod.ChannelHolder"),
				off.describe());
		assertFalse(WeaveHarness.hasMergedMethod(off.defined(TWIN)), off.describe());
		assertFalse(interfaces(off, TWIN).contains(HOLDER), interfaces(off, TWIN).toString());
		// And nothing reports it: the mixin applied cleanly, so the audit has no loss to name.
		assertEquals(List.of(), losses(off), off.describe());
	}

	/** The run and its control must be told apart by the very predicates the test uses on them. */
	@Test void theControlFlipsEveryTwinAssertion() throws Exception {
		assertTrue(twinHolds(twinned) && !twinHolds(off), "twin predicate does not separate the runs");
		assertTrue(offHolds(off) && !offHolds(twinned), "control predicate does not separate the runs");
	}

	private static boolean twinHolds(WeaveHarness.Result run) throws Exception {
		return run.printed("[MergedTwin] live encode=woven:hello") && run.printed(WeaveHarnessMain.DONE + " channel-plain")
				&& WeaveHarness.hasMergedMethod(run.defined(TWIN)) && interfaces(run, TWIN).contains(HOLDER);
	}

	private static boolean offHolds(WeaveHarness.Result run) throws Exception {
		return run.printed("[MergedTwin] live encode=plain:hello")
				&& run.printed(WeaveHarnessMain.THREW + "java.lang.ClassCastException")
				&& !WeaveHarness.hasMergedMethod(run.defined(TWIN)) && !interfaces(run, TWIN).contains(HOLDER);
	}

	private static WeaveHarness.Result run(String label, String twins) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"fixture.mergedtwin.mod.Probe", "run", Map.of(MixinMergedTwin.PROPERTY, twins));
	}

	private static List<String> interfaces(WeaveHarness.Result run, String internalName) throws Exception {
		ClassNode node = new ClassNode();
		new ClassReader(run.defined(internalName)).accept(node, ClassReader.SKIP_CODE);
		return node.interfaces;
	}

	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.id().startsWith("mixin-injector:")
				&& f.modId().equals(MOD) && f.confirmedRequired()).toList();
	}
}
