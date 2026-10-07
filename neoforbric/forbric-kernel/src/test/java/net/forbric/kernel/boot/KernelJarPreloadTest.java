package net.forbric.kernel.boot;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate M22's failure in one process: a hook class whose helper is first needed after the jar both came from has been
 * truncated. Without the preload that first use throws NoClassDefFoundError — the server crash report the nightly
 * wrote at the first lava flow; with it the helper was defined while the jar was readable and the call answers.
 */
class KernelJarPreloadTest {
	@TempDir Path directory;

	@Test void theNegativeControlFailsTheFirstUseAfterTheJarIsGone() throws Exception {
		Path jar = hookJar("control.jar");
		try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
			Method answer = Class.forName("probe.Hook", false, loader).getMethod("answer");
			truncate(jar);
			InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> answer.invoke(null));
			assertInstanceOf(NoClassDefFoundError.class, thrown.getCause(), "the helper was read lazily from the truncated jar");
		}
	}

	@Test void aPreloadedJarKeepsAnsweringAfterItIsGone() throws Exception {
		Path jar = hookJar("preloaded.jar");
		try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
			KernelJarPreload.Outcome outcome = KernelJarPreload.define(KernelJarPreload.classNames(jar), loader);
			assertEquals(2, outcome.requested());
			assertEquals(List.of(), outcome.failed());
			Method answer = Class.forName("probe.Hook", false, loader).getMethod("answer");
			truncate(jar);
			assertEquals(42, answer.invoke(null));
		}
	}

	@Test void onlyRootClassesTheBootLoaderMayDefineAreListed() throws Exception {
		Path jar = directory.resolve("listing.jar");
		try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
			put(out, "probe/Hook.class", new byte[] {1});
			put(out, "probe/Hook$Inner.class", new byte[] {1});
			put(out, "probe/package-info.class", new byte[] {1});
			put(out, "module-info.class", new byte[] {1});
			put(out, "META-INF/versions/21/probe/Hook.class", new byte[] {1});
			put(out, "META-INF/jars/forbric-kernel-runtime.jar", new byte[] {1});
			put(out, "net/minecraft/server/Main.class", new byte[] {1});
			put(out, "assets/forbric/icon.png", new byte[] {1});
		}
		assertEquals(List.of("probe.Hook", "probe.Hook$Inner"), KernelJarPreload.classNames(jar));
	}

	@Test void aClassThatCannotBeDefinedIsNotedAndTheRestAreStillDefined() throws Exception {
		Path jar = hookJar("partial.jar");
		try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
			KernelJarPreload.Outcome outcome = KernelJarPreload.define(List.of("probe.Missing", "probe.Helper"), loader);
			assertEquals(1, outcome.defined());
			assertEquals(1, outcome.failed().size());
			assertTrue(outcome.failed().get(0).startsWith("probe.Missing "), outcome.failed().toString());
		}
	}

	@Test void onlyAJarIsAnythingToPreload() throws Exception {
		assertNull(KernelJarPreload.jarOf(KernelJarPreloadTest.class), "test classes come from a directory");
		Path jar = hookJar("located.jar");
		try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
			assertEquals(jar.toRealPath(), KernelJarPreload.jarOf(Class.forName("probe.Hook", false, loader)).toRealPath());
		}
	}

	/** probe.Hook.answer() returns probe.Helper.value(), 42; nothing resolves Helper until answer() runs. */
	private Path hookJar(String name) throws IOException {
		Path jar = directory.resolve(name);
		try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
			put(out, "probe/Hook.class", staticInt("probe/Hook", "answer", mv -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "probe/Helper", "value", "()I", false)));
			put(out, "probe/Helper.class", staticInt("probe/Helper", "value", mv -> mv.visitIntInsn(Opcodes.BIPUSH, 42)));
		}
		return jar;
	}

	private static byte[] staticInt(String owner, String method, java.util.function.Consumer<MethodVisitor> body) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, owner, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, method, "()I", null, null);
		mv.visitCode();
		body.accept(mv);
		mv.visitInsn(Opcodes.IRETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static void put(JarOutputStream out, String name, byte[] bytes) throws IOException {
		out.putNextEntry(new JarEntry(name));
		out.write(bytes);
		out.closeEntry();
	}

	/** What gate M22 does to the boot jar: empty it in place, under the loader that has it open. */
	private static void truncate(Path jar) throws IOException {
		try (FileChannel channel = FileChannel.open(jar, StandardOpenOption.WRITE)) {
			channel.truncate(0);
		}
	}
}
