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

package net.forbric.kernel.util;

import java.net.URL;
import java.security.CodeSource;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.forbric.api.ModCatalog;

/**
 * What the game shows as the loader it runs on: {@code forbric-v0.3.1-beta}, on the title screen and in F3.
 *
 * <p>The release version is the installer's, and the installer stages this jar under it —
 * {@code libraries/net/forbric/forbric-kernel/0.3.1-beta/forbric-kernel-0.3.1-beta.jar} — so the jar's own file
 * name carries the version every launcher profile was written with, with nothing new for an installer to write and
 * nothing for an existing install to lack. {@code -Dforbric.version=} overrides it; a kernel run from class
 * directories (a test, a dev launch without a jar) shows {@code forbric-dev}.
 *
 * <p>The display only. The brand the client SENDS stays NeoForge's: servers and NeoForge's own networking read it.
 */
public final class ForbricBranding {
	public static final String PROPERTY = "forbric.version";
	/** The token in F3's "(version/brand)" slot. */
	public static final String BRAND = "forbric";

	private static final Pattern KERNEL_JAR = Pattern.compile("forbric-kernel-(.+)\\.jar");
	private static volatile String version;

	private ForbricBranding() {
	}

	/** The running Forbric release, e.g. {@code 0.3.1-beta}, or null when it cannot be told. */
	public static String version() {
		String known = version;
		if (known == null) {
			CodeSource source = ForbricBranding.class.getProtectionDomain().getCodeSource();
			known = versionFrom(System.getProperty(PROPERTY), source == null ? null : source.getLocation());
			version = known == null ? "" : known;
		}
		return known == null || known.isEmpty() ? null : known;
	}

	/** {@code forbric-v<version>}, or {@code forbric-dev} when the version cannot be told. */
	public static String display() {
		String v = version();
		return v == null ? BRAND + "-dev" : BRAND + "-v" + v;
	}

	public static String brand() {
		return BRAND;
	}

	/** The mods the player installed, one per jar in {@code mods/} — what Forbric's Mods screen lists. */
	public static int installedModCount() {
		return ModCatalog.all().size();
	}

	/** The override when given (a leading {@code v} dropped), else the version in the kernel jar's file name. */
	static String versionFrom(String override, URL location) {
		if (override != null && !override.isBlank()) {
			String v = override.strip();
			return v.startsWith("v") || v.startsWith("V") ? v.substring(1) : v;
		}
		if (location == null) return null;
		String path = location.getPath();
		String file = path.substring(path.lastIndexOf('/') + 1);
		Matcher m = KERNEL_JAR.matcher(file);
		return m.matches() ? java.net.URLDecoder.decode(m.group(1), java.nio.charset.StandardCharsets.UTF_8) : null;
	}
}
