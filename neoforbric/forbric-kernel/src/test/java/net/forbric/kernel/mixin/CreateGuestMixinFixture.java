/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.mixin;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import javax.tools.ToolProvider;
import net.forbric.kernel.TestFixtures.Fixture;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

final class CreateGuestMixinFixture {
	static ClassNode mixin(String name) throws Exception {
		return CarpetMixinAdapterTest.from(Fixture.THIRD_PARTY, Path.of(System.getProperty("forbric.createFlyJar",
				"build/compat-inputs/create-fly/create-fly.jar")), name);
	}
	static URLClassLoader executable(Path root, ClassNode mixin, Map<String, String> sources,
			Predicate<MethodNode> methods) throws Exception {
		List<String> args = new ArrayList<>(List.of("--release", "21", "-d", root.toString()));
		for (var source : sources.entrySet()) {
			Path file = root.resolve(source.getKey()); Files.createDirectories(file.getParent());
			Files.writeString(file, source.getValue()); args.add(file.toString());
		}
		assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
		Path file = root.resolve(mixin.name + ".class"); ClassNode shell = MixinFit.parse(Files.readAllBytes(file));
		for (MethodNode method : mixin.methods) if (methods.test(method)) {
			method.access = Opcodes.ACC_PUBLIC | (method.access & Opcodes.ACC_STATIC);
			method.visibleAnnotations = null; method.invisibleAnnotations = null;
			shell.methods.removeIf(m -> m.name.equals(method.name) && m.desc.equals(method.desc));
			shell.methods.add(method);
		}
		Files.write(file, StagedFabricMixinFixture.bytes(shell));
		return new URLClassLoader(new URL[]{root.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
	}
}
