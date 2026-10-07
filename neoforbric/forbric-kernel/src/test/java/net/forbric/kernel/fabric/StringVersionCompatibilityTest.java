/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.fabric;

import static org.junit.jupiter.api.Assertions.*;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.impl.util.version.StringVersion;
import org.junit.jupiter.api.Test;

class StringVersionCompatibilityTest {
	@Test void nativeConstructorLinksAsVersionAndRetainsFabricStringComparison() throws Exception {
		Version version = new StringVersion("6.0.9-1");
		assertEquals("6.0.9-1", version.getFriendlyString());
		assertEquals("6.0.9-1", version.toString());
		assertEquals(new StringVersion("6.0.9-1"), version);
		assertNotEquals(new StringVersion("6.0.9-2"), version);
		assertTrue(version.compareTo(Version.parse("6.0.10")) > 0);
	}
}
