/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import net.forbric.api.Ecosystem;
import net.forbric.api.ModPresence;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Retains Fabric's config entrypoint contract when SpectreLib's NeoForge build wins arbitration. */
public final class SpectreConfigContractInjector implements ClassTransformer {
    static final String ORIGINAL = "com/illusivesoulworks/spectrelib/config/SpectreConfigInitializer";
    static final String BRIDGE = "net/forbric/kernel/interop/SpectreConfigInitializer";
    public static boolean needed() {
        var mod = ModPresence.metadata("spectrelib");
        return mod != null && mod.getEcosystem() == Ecosystem.NEOFORGE;
    }
    @Override public String name() { return "forbric-spectre-config-contract"; }
    @Override public AnchorSet anchors() { return AnchorSet.scanned("optional SpectreLib config entrypoint contract"); }
    @Override public byte[] transform(String name, byte[] bytes, TransformContext context) {
        if (bytes == null || !needed()) return bytes;
        ClassReader reader = new ClassReader(bytes);
        if (!java.util.Arrays.asList(reader.getInterfaces()).contains(ORIGINAL)) return bytes;
        ClassNode node = new ClassNode(); reader.accept(node, 0);
        node.interfaces.replaceAll(i -> i.equals(ORIGINAL) ? BRIDGE : i);
        ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray();
    }
}
