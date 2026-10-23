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

package net.forbric.installer.kernel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Where NeoForge's artifacts live, what Forbric calls the things it builds out of them, and the reader for the
 * {@code config.json} inside NeoForge's {@code -userdev} jar.
 *
 * <p>Everything version-specific is read from that {@code config.json} — its {@code libraries} above all, which is
 * the input list the runtime jar is assembled from — so retargeting a different NeoForge build is data, not code.
 */
final class NeoForgeArtifacts {

	private static final String NEOFORGED_MVN = "https://maven.neoforged.net/releases";
	private static final String CENTRAL = "https://repo1.maven.org/maven2";

	final String mcVersion;
	final String neoforgeVersion;

	NeoForgeArtifacts(String mcVersion, String neoforgeVersion) {
		this.mcVersion = mcVersion;
		this.neoforgeVersion = neoforgeVersion;
	}

	String neoforgedUrl(String coordinate) {
		return NEOFORGED_MVN + "/" + Util.coordinateToPath(stripExtension(coordinate));
	}

	String centralUrl(String coordinate) {
		return CENTRAL + "/" + Util.coordinateToPath(stripExtension(coordinate));
	}

	/** The jar carrying {@code config.json}, the patches and the library list. */
	String userdevCoordinate() {
		return "net.neoforged:neoforge:" + neoforgeVersion + ":userdev";
	}

	/**
	 * NeoForge's own classes.
	 *
	 * <p>{@code assemble-neoforge-runtime.sh:62} claims this one "is NOT fetched here (gradle-module-routed, no
	 * bare jar)" and reads it out of an NFRT cache directory instead. That is not true: the Maven serves it
	 * directly, 6,984,676 bytes, the same file the cache holds. The claim is what tied a build to a developer's
	 * {@code ~/.neoformruntime}, so it is worth stating plainly that it was checked.
	 */
	String universalCoordinate() {
		return "net.neoforged:neoforge:" + neoforgeVersion + ":universal";
	}

	/** What Forbric stages the assembled runtime under. */
	String runtimeCoordinate() {
		return "net.forbric:neoforge-runtime:" + mcVersion;
	}

	/** What Forbric stages NeoForm's patched Minecraft — the game base itself — under. */
	String patchedMcCoordinate() {
		return "net.forbric:patched-mc-neoforge:" + mcVersion;
	}

	/** Strip a Maven {@code @extension} suffix (e.g. {@code :universal@jar} → {@code :universal}). */
	static String stripExtension(String coordinate) {
		int at = coordinate.indexOf('@');
		return at >= 0 ? coordinate.substring(0, at) : coordinate;
	}

	// ---- userdev config.json ----

	/** The subset of {@code config.json} the runtime builder needs: the runtime library coordinates. */
	static final class UserdevConfig {
		final List<String> libraries;

		UserdevConfig(List<String> libraries) {
			this.libraries = libraries;
		}
	}

	/** Read {@code config.json} out of a downloaded NeoForge {@code -userdev} jar. */
	static UserdevConfig readConfig(Path userdevJar) throws IOException {
		byte[] bytes = Zips.readEntry(userdevJar, "config.json");
		if (bytes == null) throw new IOException("no config.json in " + userdevJar.getFileName()
				+ " (not a NeoForge userdev jar?)");
		Map<String, Object> root;
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> parsed = (Map<String, Object>) Json.parse(
					new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
			root = parsed;
		} catch (RuntimeException e) {
			throw new IOException("malformed config.json in " + userdevJar.getFileName() + ": " + e.getMessage(), e);
		}
		List<String> libraries = new ArrayList<>();
		if (root.get("libraries") instanceof List<?> list) {
			for (Object e : list) if (e instanceof String s) libraries.add(s);
		}
		return new UserdevConfig(libraries);
	}
}
