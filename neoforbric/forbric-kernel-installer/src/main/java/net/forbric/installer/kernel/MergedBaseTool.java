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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Link-checks the two jars a Forbric instance runs on: the NeoForge-patched game base and the NeoForge runtime
 * beside it, as one class universe. There is nothing else to do to them — the base is NeoForge's own patched
 * Minecraft exactly as NeoFormRuntime produced it, so this class no longer transforms anything, it only passes
 * judgement.
 *
 * <p>The checker itself lives in {@code forbric-merge-tools.jar}, which rides inside the installer as a resource
 * and is unpacked next to the build. Two reasons it is a separate jar run as a subprocess rather than code
 * called in-process:
 *
 * <ul>
 *   <li>The checker needs ASM. Keeping it in its own jar preserves the installer's own rule that it carries
 *       Forbric's jars and nothing else, instead of quietly growing a dependency.</li>
 *   <li>A single-entry {@code -cp} is the one classpath form with nothing to get wrong about {@code ;} versus
 *       {@code :}.</li>
 * </ul>
 */
final class MergedBaseTool {

	/** Where the tools jar rides inside the installer jar. */
	private static final String TOOLS_RESOURCE = "/forbric/tools/forbric-merge-tools.jar";

	private static final String LINK_CHECK_MAIN = "net.forbric.tools.MergedLinkChecker";

	private final Path toolsDir;
	private final Consumer<String> log;

	MergedBaseTool(Path toolsDir, Consumer<String> log) {
		this.toolsDir = toolsDir;
		this.log = log;
	}

	/**
	 * Counts what the game base still points at without either jar defining it, and puts that number in the
	 * install log.
	 *
	 * <p>The development build has always ended with this check; the installer, which builds the artifacts
	 * itself, runs it on what every player actually gets — so a build that comes out broken is refused here
	 * rather than discovered in game.
	 *
	 * <p>The installer pins the supported carrier versions. Its tools carry the same reviewed baseline as the
	 * development build: a missing baseline or a new dangling reference prevents publishing a broken profile.
	 *
	 * @throws Failed when the check ran and the jars did not pass it; any other IOException means it could not run
	 */
	void linkCheck(JdkLocator.Jvm jvm, Path baseJar, Path runtimeJar) throws IOException {
		Path tools = unpackTools();
		List<String> tail = new ArrayList<>();
		int code = exec(List.of(
				jvm.javaBin().toString(),
				"-cp", tools.toString(), LINK_CHECK_MAIN,
				"--baseline-resource", "/net/forbric/tools/link-check-baseline.txt",
				baseJar.toString(), runtimeJar.toString()),
				"link-checking the game base", tail);
		String summary = tail.stream()
				.filter(l -> l.contains("dangling references:"))
				.reduce((a, b) -> b)
				.orElse("[link-check] produced no summary line (exit " + code + ")");
		log.accept("[link-check] " + summary.strip());
		if (code != 0 || !summary.contains(", new 0)")) {
			throw new Failed("the game base failed the reviewed link baseline (exit " + code + "):\n"
					+ String.join("\n", tail));
		}
	}

	/**
	 * The verdict "these jars do not link", as distinct from the check not running at all — so a caller that
	 * did not build the jars itself can say whose they are.
	 */
	static final class Failed extends IOException {
		Failed(String message) {
			super(message);
		}
	}

	/**
	 * Runs {@code cmd}, collecting the last 40 output lines into {@code tail}, and RETURNS the exit code instead
	 * of throwing on it — the checker reports its verdict through that code, and the caller needs both.
	 */
	private int exec(List<String> cmd, String label, List<String> tail) throws IOException {
		log.accept("[link-check] " + label + " …");
		Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				tail.add(line);
				if (tail.size() > 40) tail.remove(0);
			}
		}
		try {
			return p.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			p.destroyForcibly();
			throw new IOException("interrupted while running " + label, e);
		}
	}

	/**
	 * Copies the bundled tools jar out to disk, because a subprocess needs a path, not a resource.
	 *
	 * <p><b>Unconditionally</b>, overwriting whatever is there. It used to return the existing file when one was
	 * present, which is the same "the file exists, so it must be current" mistake {@link BuildStamp} was written
	 * to fix, one level further down — and it defeated that fix completely: the new stamp correctly invalidated
	 * every artifact, and the rebuild then ran the PREVIOUS installer's checker, left behind in
	 * {@code .forbric-build/tools}. It is 280 KB; there is nothing to save by being clever about it.
	 */
	private Path unpackTools() throws IOException {
		Files.createDirectories(toolsDir);
		Path dest = toolsDir.resolve("forbric-merge-tools.jar");
		try (InputStream in = MergedBaseTool.class.getResourceAsStream(TOOLS_RESOURCE)) {
			if (in == null) {
				throw new IOException("this installer was built without " + TOOLS_RESOURCE
						+ " — run ':forbric-loader:mergeToolsJar' and rebuild it");
			}
			Path part = dest.resolveSibling(dest.getFileName() + ".part");
			Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
			Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING);
		}
		return dest;
	}
}
