/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Uses actual mod ownership for Controlify and the live Mixin slot for JECharacters, without impersonating Knot. */
public final class CompatPluginPlatformInjector implements ClassTransformer {
    public static final String PROPERTY = "forbric.compatPluginPlatforms";
    private static final String CONTROL = "dev/isxander/controlify/compatibility/CompatMixinPlugin";
    private static final String JECH = "me/towdium/jecharacters/mixin/JechMixinPlugin";
    @Override public String name() { return "forbric-compat-plugin-platforms"; }
    @Override public AnchorSet anchors() { return AnchorSet.scanned("optional Controlify/JECharacters platform integration"); }
    @Override public byte[] transform(String name, byte[] bytes, TransformContext context) {
        String internal = name.replace('.', '/');
        if (bytes == null || "off".equalsIgnoreCase(System.getProperty(PROPERTY, "on")) || (!CONTROL.equals(internal) && !JECH.equals(internal))) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); boolean changed = false;
        if (CONTROL.equals(internal)) {
            MethodNode method = node.methods.stream().filter(m -> m.name.equals("loadPlatform") && m.desc.equals("()L" + CONTROL + "$Platform;")).findFirst().orElse(null);
            if (method == null) return bytes;
            for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode c && c.owner.equals("net/forbric/kernel/interop/CompatPluginOwnership")) return bytes;
            LabelNode nativeService = new LabelNode(), fabric = new LabelNode(); InsnList code = new InsnList();
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"org/spongepowered/asm/service/MixinService","getService","()Lorg/spongepowered/asm/service/IMixinService;",false));
            code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,"org/spongepowered/asm/service/IMixinService","getName","()Ljava/lang/String;",true));
            code.add(new LdcInsnNode("Forbric")); code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false)); code.add(new JumpInsnNode(Opcodes.IFEQ,nativeService));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/forbric/kernel/interop/CompatPluginOwnership","controlifyUsesNeoForge","()Z",false));code.add(new JumpInsnNode(Opcodes.IFEQ,fabric));
            platform(code,"NEOFORGE","dev.isxander.controlify.neoforge.compatibility.NeoforgeCompatMixinPlatform");
            code.add(fabric);code.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
            platform(code,"FABRIC","dev.isxander.controlify.fabric.compatibility.FabricCompatMixinPlatform");
            code.add(nativeService);code.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));method.instructions.insert(code);changed=true;
        } else {
            MethodNode hook=node.methods.stream().filter(m->m.name.equals("hook")&&m.desc.equals("()V")).findFirst().orElse(null);if(hook==null)return bytes;
            AbstractInsnNode end=null; boolean delegate=false, transformer=false;
            for(var instruction:hook.instructions) {
                if(instruction instanceof LdcInsnNode c) {delegate |= "delegate".equals(c.cst);transformer |= "mixinTransformer".equals(c.cst);}
                if(instruction instanceof MethodInsnNode c&&c.name.equals("setAccessible")&&transformer){end=instruction;break;}
            }
            if(!delegate||!transformer||end==null)return bytes;
            AbstractInsnNode next=end.getNext();for(var instruction=hook.instructions.getFirst();instruction!=next;){var following=instruction.getNext();hook.instructions.remove(instruction);instruction=following;}
            InsnList code=new InsnList();
            code.add(new InsnNode(Opcodes.ACONST_NULL));code.add(new VarInsnNode(Opcodes.ASTORE,0));
            code.add(new InsnNode(Opcodes.ACONST_NULL));code.add(new VarInsnNode(Opcodes.ASTORE,1));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/forbric/kernel/mixin/JechWeaverBridge","holder","()Lnet/forbric/kernel/mixin/JechWeaverBridge$Holder;",false));code.add(new VarInsnNode(Opcodes.ASTORE,2));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/forbric/kernel/mixin/JechWeaverBridge","field","()Ljava/lang/reflect/Field;",false));code.add(new VarInsnNode(Opcodes.ASTORE,3));hook.instructions.insert(code);hook.localVariables=null;changed=true;
        }
        if(!changed)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static void platform(InsnList code,String loader,String implementation) {
        code.add(new TypeInsnNode(Opcodes.NEW,CONTROL+"$Platform"));code.add(new InsnNode(Opcodes.DUP));code.add(new FieldInsnNode(Opcodes.GETSTATIC,CONTROL+"$Loader",loader,"L"+CONTROL+"$Loader;"));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"org/spongepowered/asm/service/MixinService","getService","()Lorg/spongepowered/asm/service/IMixinService;",false));code.add(new LdcInsnNode(implementation));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,CONTROL,"loadPlatformImpl","(Lorg/spongepowered/asm/service/IMixinService;Ljava/lang/String;)Ldev/isxander/controlify/compatibility/CompatMixinPlatform;",false));
        code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,CONTROL+"$Platform","<init>","(L"+CONTROL+"$Loader;Ldev/isxander/controlify/compatibility/CompatMixinPlatform;)V",false));code.add(new InsnNode(Opcodes.ARETURN));
    }
}
