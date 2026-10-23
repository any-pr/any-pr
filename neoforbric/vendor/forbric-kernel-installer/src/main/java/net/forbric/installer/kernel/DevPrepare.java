/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.installer.kernel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/** Reuses the installer artifact pipeline without installing a player profile or release payload. */
public final class DevPrepare {

	public static void main(String[] args) throws Exception {
		if (args.length != 3) throw new IllegalArgumentException("usage: DevPrepare <mc-dir> <staged-run-dir> <java>");
		Path mc = Path.of(args[0]).toAbsolutePath();
		Path stage = Path.of(args[1]).toAbsolutePath();
		JdkLocator.Jvm jvm = JdkLocator.locate(mc, Path.of(args[2]), System.out::println);
		if (jvm.feature() < 25) throw new IOException("Minecraft 26.2 development needs JDK 25 or newer");
		Path version = mc.resolve("versions").resolve(Pins.MINECRAFT);
		if (!Files.isRegularFile(version.resolve(Pins.MINECRAFT + ".jar"))
				|| !Files.isRegularFile(version.resolve(Pins.MINECRAFT + ".json"))) {
			new MojangDownloader(System.out::println).downloadClient(Pins.MINECRAFT, version);
		}
		Map<String, Path> artifacts = new ArtifactBuilder(System.out::println).build(mc, Pins.MINECRAFT, jvm);
		copy(artifacts.get(ArtifactBuilder.NEOFORGE_BASE), stage.resolve("neoforge-base/patched-mc-neoforge-26.2.jar"));
		copy(artifacts.get(ArtifactBuilder.NEOFORGE_RUNTIME), stage.resolve("neoforge-runtime/neoforge-runtime.jar"));
		// The build pin beside each staged jar says which pin set produced it, so a consumer of the staged tree can
		// tell this build's jars from ones a launcher install keeps under libraries/ with the same names.
		copy(mc.resolve(".forbric-build/out/patched-mc-neoforge-26.2.jar.pins"),
				stage.resolve("neoforge-base/patched-mc-neoforge-26.2.jar.pins"));
		copy(mc.resolve(".forbric-build/out/neoforge-runtime.jar.pins"),
				stage.resolve("neoforge-runtime/neoforge-runtime.jar.pins"));
		System.out.println("Development artifacts staged under " + stage);
	}

	private static void copy(Path source, Path target) throws IOException {
		Files.createDirectories(target.getParent());
		if (!Files.isRegularFile(target) || Files.mismatch(source, target) != -1) {
			Path temporary = target.resolveSibling(target.getFileName() + ".part");
			Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
