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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builds the two jars a Forbric instance runs on, here, on the machine that will run them.
 *
 * <p>They cannot be shipped. The game base is Minecraft with NeoForge's patches applied by NeoForge's own
 * pipeline, and the runtime is assembled from NeoForge's own distribution; both carry code this project has no
 * right to hand out, so an installer that shipped them would be redistributing Mojang's and NeoForge's work.
 * Building them from the upstreams' own Mavens, on the user's machine, is the only lawful shape this can take.
 *
 * <p>The pipeline:
 *
 * <pre>
 *   neoforge userdev ─┬→ neoforge-runtime
 *                     └→ NFRT → patched-mc-neoforge   (the game base the kernel opens)
 * </pre>
 *
 * <p>NFRT — NeoFormRuntime, the tool NeoForge itself uses — is pointed at the Minecraft the launcher already
 * installed plus the server jar this build downloads from Mojang, and its binary-patch result <em>is</em> the
 * base: nothing is layered on top of it afterwards.
 *
 * <p>Everything lands under {@code <mcDir>/.forbric-build/}, one directory that can be deleted wholesale, and
 * each step short-circuits on a finished output so an interrupted install resumes rather than restarts.
 */
final class ArtifactBuilder {

	/** The coordinates {@link Installer} stages and the profile names, without their version suffix. */
	static final String NEOFORGE_BASE = "net.forbric:patched-mc-neoforge";
	static final String NEOFORGE_RUNTIME = "net.forbric:neoforge-runtime";

	private final Consumer<String> log;

	ArtifactBuilder(Consumer<String> log) {
		this.log = log;
	}

	/**
	 * Produces both, reusing whatever is already built.
	 *
	 * @param mcDir     the Minecraft directory; its {@code .forbric-build/} holds every intermediate, and NFRT
	 *                  reads the client jar its {@code versions/<mc>/} holds
	 * @param jvm       the JVM the build tools run under
	 * @return coordinate (without version) to the finished file, in the shape {@link GameArtifacts#all()} returns
	 */
	Map<String, Path> build(Path mcDir, String mcVersion, JdkLocator.Jvm jvm) throws IOException {
		Path build = mcDir.resolve(".forbric-build");
		Path dl = build.resolve("dl");
		Path tools = build.resolve("tools");
		Path out = build.resolve("out");
		Files.createDirectories(dl);
		Files.createDirectories(out);

		log.accept("");
		log.accept("Building the game artifacts. The first run downloads a few hundred megabytes and takes");
		log.accept("several minutes; afterwards it is cached in " + build + ".");
		log.accept("pins: " + Pins.stamp());

		Http http = new Http(log);

		// ---- NeoForge ----
		log.accept("");
		log.accept("== NeoForge " + Pins.NEOFORGE + " ==");
		NeoForgeArtifacts nfa = new NeoForgeArtifacts(mcVersion, Pins.NEOFORGE);
		Path neoUserdev = dl.resolve("neoforge-userdev.jar");
		http.ensureWithFallback(nfa.neoforgedUrl(nfa.userdevCoordinate()), nfa.centralUrl(nfa.userdevCoordinate()),
				neoUserdev);
		NeoForgeArtifacts.UserdevConfig neoCfg = NeoForgeArtifacts.readConfig(neoUserdev);

		ArtifactResult neoRuntime = new NeoForgeRuntimeBuilder(nfa, http, build,
				out.resolve("neoforge-runtime.jar"), log).build(neoCfg);
		// NFRT's result is the game base itself — the jar the profile's --gameJar names, staged as-is.
		ArtifactResult base = new NfrtRunner(http, tools, build.resolve("nfrt"),
				build.resolve("nfrt-work"), dl, log)
				.run(jvm, mcDir, out.resolve("patched-mc-neoforge-" + mcVersion + ".jar"),
						nfa.patchedMcCoordinate(), mcVersion);

		// After the build, before the profile: the base and the runtime have to link as one game.
		new MergedBaseTool(tools, log).linkCheck(jvm, base.file, neoRuntime.file);

		Map<String, Path> result = new LinkedHashMap<>();
		result.put(NEOFORGE_BASE, base.file);
		result.put(NEOFORGE_RUNTIME, neoRuntime.file);

		log.accept("");
		log.accept("game artifacts ready:");
		for (Map.Entry<String, Path> e : result.entrySet()) {
			log.accept("  " + e.getKey() + "  →  " + e.getValue().getFileName()
					+ " (" + (Files.size(e.getValue()) / (1024 * 1024)) + " MB)");
		}
		return result;
	}
}
