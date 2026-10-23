/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.mixin;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CreateFluidMixinAdapterTest {
	private org.objectweb.asm.tree.ClassNode mixin() throws Exception {
		return CreateGuestMixinFixture.mixin(CreateFluidMixinAdapter.MIXIN);
	}

	@Test void releasedHandlerRunsAtBothLiveSitesAndKeepsItsOriginalBody() throws Exception {
		var node = mixin();
		String body = MixinInstructionFingerprint.hash(CarpetMixinAdapter.named(node, "shouldSpreadLiquid"));
		assertEquals(2, CreateFluidMixinAdapter.adapt(node, CarpetMixinAdapterTest::target));
		var original = CarpetMixinAdapter.named(node, "shouldSpreadLiquid$forbricOriginal");
		assertNull(MixinFit.injectorOf(original));
		assertEquals(body, MixinInstructionFingerprint.hash(original));
		CarpetMixinAdapterTest.verify(node);
		assertEquals(0, CreateFluidMixinAdapter.adapt(node, CarpetMixinAdapterTest::target));
	}

	@Test void nativeVanillaOrMissingLiveSiteLeaveTheHandlerUntouched() throws Exception {
		var node = mixin(); byte[] before = CarpetMixinAdapterTest.bytes(node);
		assertEquals(0, CreateFluidMixinAdapter.adapt(node, name -> {
			var target = CarpetMixinAdapterTest.target(name);
			target.methods.removeIf(method -> method.name.equals("neighborChanged"));
			return target;
		}));
		assertArrayEquals(before, CarpetMixinAdapterTest.bytes(node));
		assertEquals(0, CreateFluidMixinAdapter.adapt(node, name -> {
			try { return StagedFabricMixinFixture.game(name, true); } catch (Exception e) { throw new AssertionError(e); }
		}));
		assertArrayEquals(before, CarpetMixinAdapterTest.bytes(node));
	}
}
