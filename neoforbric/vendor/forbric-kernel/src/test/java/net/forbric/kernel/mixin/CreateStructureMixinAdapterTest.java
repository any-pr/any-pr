/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.mixin;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CreateStructureMixinAdapterTest {
	@Test void setupIterationAndCleanupMoveTogetherToTheCalledEntityPlacementBody() throws Exception {
		var node = CreateGuestMixinFixture.mixin(CreateStructureMixinAdapter.MIXIN);
		Map<String, String> hashes = new HashMap<>();
		for (String name : List.of("setProcessors", "getIterator", "clearProcessors")) hashes.put(name, MixinInstructionFingerprint.hash(CarpetMixinAdapter.named(node, name)));
		assertEquals(3, CreateStructureMixinAdapter.adapt(node, CarpetMixinAdapterTest::target));
		for (String name : hashes.keySet()) assertEquals(hashes.get(name), MixinInstructionFingerprint.hash(CarpetMixinAdapter.named(node, name)));
		for (String name : List.of("getIterator", "clearProcessors")) assertEquals(List.of(CreateStructureMixinAdapter.LIVE), MixinFit.value(MixinFit.injectorOf(CarpetMixinAdapter.named(node, name)), "method"));
		CarpetMixinAdapterTest.verify(node);
		assertEquals(0, CreateStructureMixinAdapter.adapt(node, CarpetMixinAdapterTest::target));
	}
	@Test void aMissingCallerRefusesAllThreeSelectorsTogether() throws Exception {
		var node = CreateGuestMixinFixture.mixin(CreateStructureMixinAdapter.MIXIN); byte[] before = CarpetMixinAdapterTest.bytes(node);
		assertEquals(0, CreateStructureMixinAdapter.adapt(node, name -> {var target=CarpetMixinAdapterTest.target(name);target.methods.removeIf(m->m.name.equals("placeInWorld"));return target;}));
		assertArrayEquals(before, CarpetMixinAdapterTest.bytes(node));
	}
}
