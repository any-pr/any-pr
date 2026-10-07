package net.forbric.kernel.mixin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipFile;

import net.forbric.kernel.TestFixtures;
import net.forbric.kernel.TestFixtures.Fixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

@ResourceLock("system-properties")
class C2meBlockUpdateRetargetTest {

	@AfterEach void reset() {
		System.clearProperty(C2meBlockUpdateRetarget.PROPERTY);
	}

	@Test void shippedC2meBindsToTheLiveNotificationCheckWithoutChangingItsHandler() throws Exception {
		ClassNode mixin = mixin(), level = level(false);
		Function<String, byte[]> resources = resources(level);
		assertEquals(MixinFit.Verdict.PARTIAL, MixinFit.evaluate(bytes(mixin), resources).verdict());
		MixinRetarget.Plan plan = MixinRetarget.plan(mixin, resources);
		assertEquals(1, plan.rewrites().size());
		MethodNode handler = handler(mixin);
		AbstractInsnNode[] body = handler.instructions.toArray();
		AnnotationNode injection = MixinFit.injectorOf(handler);
		Object required = MixinFit.value(injection, "require");
		assertEquals(1, MixinRetarget.apply(mixin, plan));
		assertEquals(List.of(C2meBlockUpdateRetarget.HELPER), MixinFit.value(injection, "method"));
		assertEquals(required, MixinFit.value(injection, "require"));
		assertArrayEquals(body, handler.instructions.toArray());
		assertEquals(MixinFit.Verdict.FIT, MixinFit.evaluate(bytes(mixin), resources).verdict());
		assertTrue(MixinRetarget.plan(mixin, resources).isEmpty());
	}

	@Test void vanillaAndTheDisabledRepairKeepTheOriginalSelector() throws Exception {
		assertTrue(MixinRetarget.plan(mixin(), resources(level(true))).isEmpty());
		System.setProperty(C2meBlockUpdateRetarget.PROPERTY, "off");
		assertTrue(MixinRetarget.plan(mixin(), resources(level(false))).isEmpty());
	}

	@Test void missingHelperCallOrDuplicatedStatusCheckRefusesTheMove() throws Exception {
		ClassNode target = level(false);
		MethodNode original = method(target, C2meBlockUpdateRetarget.ORIGINAL);
		for (AbstractInsnNode insn : original.instructions.toArray()) {
			if (insn instanceof MethodInsnNode call && call.name.equals("markAndNotifyBlock")) original.instructions.remove(insn);
		}
		assertTrue(MixinRetarget.plan(mixin(), resources(target)).isEmpty());
		target = level(false);
		MethodNode helper = method(target, C2meBlockUpdateRetarget.HELPER);
		helper.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/server/level/FullChunkStatus",
				"isOrAfter", "(" + C2meBlockUpdateRetarget.STATUS + ")Z", false));
		assertTrue(MixinRetarget.plan(mixin(), resources(target)).isEmpty());
	}

	@Test void aRestoredVanillaAnchorOrStaticHelperRefusesTheMove() throws Exception {
		ClassNode target = level(false);
		method(target, C2meBlockUpdateRetarget.ORIGINAL).instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
				"net/minecraft/server/level/FullChunkStatus", "isOrAfter", "(" + C2meBlockUpdateRetarget.STATUS + ")Z", false));
		assertTrue(MixinRetarget.plan(mixin(), resources(target)).isEmpty());
		target = level(false);
		method(target, C2meBlockUpdateRetarget.HELPER).access |= Opcodes.ACC_STATIC;
		assertTrue(MixinRetarget.plan(mixin(), resources(target)).isEmpty());
	}

	@Test void localCaptureSliceGroupAndDifferentModRefuseTheMove() throws Exception {
		ClassNode target = level(false);
		ClassNode local = mixin();
		handler(local).desc = "(" + C2meBlockUpdateRetarget.STATUS + "I)" + C2meBlockUpdateRetarget.STATUS;
		assertTrue(MixinRetarget.plan(local, resources(target)).isEmpty());
		ClassNode sliced = mixin();
		MixinFit.injectorOf(handler(sliced)).values.addAll(List.of("slice", new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Slice;")));
		assertTrue(MixinRetarget.plan(sliced, resources(target)).isEmpty());
		ClassNode grouped = mixin();
		handler(grouped).visibleAnnotations.add(new AnnotationNode(MixinRetarget.GROUP));
		assertTrue(MixinRetarget.plan(grouped, resources(target)).isEmpty());
		ClassNode other = mixin();
		other.name = "another/mod/MixinWorld";
		assertTrue(MixinRetarget.plan(other, resources(target)).isEmpty());
	}

	private static ClassNode mixin() throws Exception {
		Path jar = Path.of("build/compat-inputs/startup-20260930/c2me-notickvd.jar");
		TestFixtures.require(Fixture.THIRD_PARTY, Files.isRegularFile(jar), "C2ME 0.4.1-beta.1.0 notickvd fixture required");
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			return MixinFit.parse(zip.getInputStream(zip.getEntry(C2meBlockUpdateRetarget.MIXIN + ".class")).readAllBytes());
		}
	}

	private static ClassNode level(boolean vanilla) throws Exception {
		return StagedFabricMixinFixture.game(C2meBlockUpdateRetarget.LEVEL, vanilla);
	}

	private static MethodNode handler(ClassNode mixin) {
		return StagedFabricMixinFixture.method(mixin, "modifyLeastStatus");
	}

	private static MethodNode method(ClassNode target, String selector) {
		return target.methods.stream().filter(m -> selector.equals(m.name + m.desc)).findFirst().orElseThrow();
	}

	private static byte[] bytes(ClassNode node) {
		return StagedFabricMixinFixture.bytes(node);
	}

	private static Function<String, byte[]> resources(ClassNode level) {
		byte[] bytes = bytes(level);
		return name -> name.equals(C2meBlockUpdateRetarget.LEVEL + ".class") ? bytes : null;
	}
}
