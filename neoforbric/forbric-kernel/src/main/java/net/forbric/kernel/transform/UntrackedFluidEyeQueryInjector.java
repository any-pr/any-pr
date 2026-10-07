/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Preserve vanilla's false result for an eye query on a tag that has no tracker, rather than NeoForge's throw. */
public final class UntrackedFluidEyeQueryInjector implements ClassTransformer {
    public static final String PROPERTY = "forbric.untrackedFluidEyes";
    static final String ENTITY = "net.minecraft.world.entity.Entity";
    static final String INTERACTION = "net.minecraft.world.entity.EntityFluidInteraction";
    static final String OWNER = INTERACTION.replace('.', '/');
    static final String TAG = "Lnet/minecraft/tags/TagKey;";
    static final String DESC = "(" + TAG + ")Z";
    @Override public String name() { return "forbric-untracked-fluid-eyes"; }
    private static boolean enabled() { return !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "on")); }
    @Override public AnchorSet anchors() {
        if (!enabled()) return AnchorSet.scanned("untracked fluid eye queries left native by request");
        return AnchorSet.of(new AnchorSet.Anchor(ENTITY, AnchorSet.Severity.REQUIRED, "a Fabric mod's untracked fluid tag query throws while rendering"),
                new AnchorSet.Anchor(INTERACTION, AnchorSet.Severity.REQUIRED, "vanilla's untracked fluid tag query must answer false"));
    }
    @Override public byte[] transform(String name, byte[] bytes, TransformContext context) {
        if (!enabled() || bytes == null || (!ENTITY.equals(name) && !INTERACTION.equals(name))) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        MethodNode method = node.methods.stream().filter(m -> m.name.equals("isEyeInFluid") && m.desc.equals(DESC)).findFirst().orElse(null);
        if (method == null) return bytes;
        boolean nativeTagLookup = false;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(OWNER) && call.name.equals("getFluidTypeByTag")) nativeTagLookup = true;
        if (!nativeTagLookup) return bytes;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.name.equals("hasTagType")) return bytes;
        LabelNode known = new LabelNode(); InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/forbric/kernel/runtime/KernelFluidTypes", "hasTagType", DESC, false));
        guard.add(new JumpInsnNode(Opcodes.IFNE, known));
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        if (ENTITY.equals(name)) {
            if (node.fields.stream().noneMatch(f -> f.name.equals("fluidInteraction") && f.desc.equals("L" + OWNER + ";"))) return bytes;
            guard.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "fluidInteraction", "L" + OWNER + ";"));
            guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
            guard.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OWNER, "getTracker", "(" + TAG + ")L" + OWNER + "$Tracker;", false));
            guard.add(new JumpInsnNode(Opcodes.IFNONNULL, known));
        } else {
            if (node.fields.stream().noneMatch(f -> f.name.equals("trackerByFluid") && f.desc.equals("Ljava/util/Map;"))) return bytes;
            guard.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "trackerByFluid", "Ljava/util/Map;"));
            guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
            guard.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/Map", "containsKey", "(Ljava/lang/Object;)Z", true));
            guard.add(new JumpInsnNode(Opcodes.IFNE, known));
        }
        guard.add(new InsnNode(Opcodes.ICONST_0)); guard.add(new InsnNode(Opcodes.IRETURN));
        guard.add(known); guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(guard);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
}
