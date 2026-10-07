/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.util;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;

import org.junit.jupiter.api.Test;

/** The release a launcher profile installed, read off the kernel jar's own Maven file name. */
class ForbricBrandingTest {
	@Test void theInstalledJarsFileNameCarriesTheRelease() throws Exception {
		URL installed = new URL("file:/Users/p/Library/Application%20Support/minecraft/libraries/net/forbric/forbric-kernel/"
				+ "0.3.1-beta/forbric-kernel-0.3.1-beta.jar");
		assertEquals("0.3.1-beta", ForbricBranding.versionFrom(null, installed));
		assertEquals("0.4.0", ForbricBranding.versionFrom(null, new URL("file:/C:/mc/libraries/net/forbric/forbric-kernel/0.4.0/forbric-kernel-0.4.0.jar")));
	}

	@Test void theOverrideWinsAndItsLeadingVIsDropped() throws Exception {
		URL installed = new URL("file:/x/forbric-kernel-0.3.1-beta.jar");
		assertEquals("0.3.2", ForbricBranding.versionFrom("v0.3.2", installed));
		assertEquals("0.3.2-beta2", ForbricBranding.versionFrom(" 0.3.2-beta2 ", installed));
	}

	@Test void classDirectoriesAndForeignJarsTellNothing() throws Exception {
		assertNull(ForbricBranding.versionFrom(null, new URL("file:/repo/forbric-kernel/build/classes/java/main/")));
		assertNull(ForbricBranding.versionFrom(null, new URL("file:/x/some-other.jar")));
		assertNull(ForbricBranding.versionFrom("", null));
	}
}
