package net.forbric.kernel.classloading;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

class RuntimeArchiveAttachmentTest {
    @TempDir Path root;
    private URL directory(String folder, int value) throws Exception {
        Path dir = root.resolve(folder);
        Path file = dir.resolve("guest/Runtime.class"); Files.createDirectories(file.getParent());
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "guest/Runtime", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value", "()I", null, null);
        method.visitCode(); method.visitLdcInsn(value); method.visitInsn(Opcodes.IRETURN); method.visitMaxs(1, 0); method.visitEnd(); writer.visitEnd();
        Files.write(file, writer.toByteArray()); Files.writeString(dir.resolve("payload.txt"), folder);
        return dir.toUri().toURL();
    }
    @Test void attachedRuntimeUsesTheGameLoaderAndItsTransformsAndResources() throws Exception {
        try (var game = new ForbricClassLoader(new URL[0], getClass().getClassLoader());
             var runtime = new URLClassLoader(new URL[] {directory("runtime", 7)}, game)) {
            assertThrows(ClassNotFoundException.class, () -> game.loadClass("guest.Runtime"));
            AtomicInteger transformed = new AtomicInteger();
            game.setTransformer((name, bytes) -> { if (name.equals("guest.Runtime")) transformed.incrementAndGet(); return bytes; });
            game.setFallbackClassLoader(runtime);
            Class<?> loaded = game.loadClass("guest.Runtime");
            assertSame(game, loaded.getClassLoader()); assertEquals(7, loaded.getMethod("value").invoke(null));
            assertEquals(1, transformed.get()); assertNotNull(game.getResource("payload.txt"));
            assertThrows(ClassNotFoundException.class, () -> game.loadClass("guest.Missing"));
        }
    }
    @Test void anAttachedArchiveCannotShadowAnAlreadyOwnedClass() throws Exception {
        try (var game = new ForbricClassLoader(new URL[] {directory("owned", 1)}, getClass().getClassLoader());
             var runtime = new URLClassLoader(new URL[] {directory("runtime", 2)}, game)) {
            game.setFallbackClassLoader(runtime);
            assertEquals(1, game.loadClass("guest.Runtime").getMethod("value").invoke(null));
            assertSame(String.class, game.loadClass("java.lang.String"));
            assertThrows(IllegalArgumentException.class, () -> game.setFallbackClassLoader(game));
        }
    }
}
