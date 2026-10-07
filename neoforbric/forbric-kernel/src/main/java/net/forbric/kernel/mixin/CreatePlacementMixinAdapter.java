/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.mixin;

import java.util.*;
import java.util.function.Function;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Keep placement context across the whole native placement transaction, including cancellation and rollback. */
final class CreatePlacementMixinAdapter {
	private static final String CONTEXT="net/minecraft/world/item/context/UseOnContext", CIR="org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable", REF="com/llamalad7/mixinextras/sugar/ref/LocalRef";
	static int adapt(ClassNode mixin,Function<String,ClassNode> targets) {
		MethodNode cache=CreateInjectionAdapters.named(mixin,"cacheState"), use=CreateInjectionAdapters.named(mixin,"useOnBlock");
		ClassNode target=targets.apply("net/minecraft/world/item/ItemStack");
		if(cache==null||use==null||target==null||Type.getArgumentTypes(cache.desc).length!=4||Type.getArgumentTypes(use.desc).length!=4)return 0;
		if(MixinFit.injectorOf(cache)==null||MixinFit.injectorOf(use)==null)return 0;
		MethodNode host=CarpetMixinAdapter.named(target,"useOn");
		if(host==null||host.instructions==null)return 0;
		boolean nativeTransaction=false;for(var instruction:host.instructions)if(instruction instanceof MethodInsnNode call&&call.name.equals("onPlaceItemIntoWorld"))nativeTransaction=true;
		if(!nativeTransaction)return 0;
		for(MethodNode original:List.of(cache,use)) {
			boolean caching=original==cache;AnnotationNode inject=MixinFit.injectorOf(original);
			List<AnnotationNode> points=MixinFit.atNodes(inject);if(points.size()!=1)return 0;
			AnnotationNode at=points.getFirst();at.values=new ArrayList<>(List.of("value",caching?"HEAD":"RETURN"));
			MethodNode wrapper=new MethodNode(Opcodes.ACC_PRIVATE,original.name,"(L"+CONTEXT+";L"+CIR+";L"+REF+";)V",null,null);
			wrapper.visibleAnnotations=new ArrayList<>(List.of(inject));
			if(original.invisibleParameterAnnotations!=null){wrapper.invisibleParameterAnnotations=new List[3];wrapper.invisibleParameterAnnotations[2]=original.invisibleParameterAnnotations[3];}
			original.name+="$forbricOriginal";CreateInjectionAdapters.removeInjector(original,inject);original.invisibleParameterAnnotations=null;original.visibleParameterAnnotations=null;
			InsnList c=wrapper.instructions;LabelNode done=new LabelNode();
			if(!caching){c.add(new VarInsnNode(Opcodes.ALOAD,1));c.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,CONTEXT,"getPlayer","()Lnet/minecraft/world/entity/player/Player;",false));c.add(new VarInsnNode(Opcodes.ASTORE,4));
				c.add(new VarInsnNode(Opcodes.ALOAD,2));c.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,CIR,"getReturnValue","()Ljava/lang/Object;",false));c.add(new TypeInsnNode(Opcodes.INSTANCEOF,"net/minecraft/world/InteractionResult$Success"));c.add(new JumpInsnNode(Opcodes.IFEQ,done));c.add(new VarInsnNode(Opcodes.ALOAD,4));c.add(new JumpInsnNode(Opcodes.IFNULL,done));}
			c.add(new VarInsnNode(Opcodes.ALOAD,0));c.add(new VarInsnNode(Opcodes.ALOAD,1));c.add(new VarInsnNode(Opcodes.ALOAD,2));
			if(caching){c.add(new VarInsnNode(Opcodes.ALOAD,0));c.add(new TypeInsnNode(Opcodes.CHECKCAST,target.name));c.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,target.name,"getItem","()Lnet/minecraft/world/item/Item;",false));}
			else c.add(new VarInsnNode(Opcodes.ALOAD,4));
			c.add(new VarInsnNode(Opcodes.ALOAD,3));c.add(MixinHandlerShim.callOwn(mixin,false,original.name,original.desc));
			if(!caching){c.add(done);c.add(new FrameNode(Opcodes.F_APPEND,1,new Object[]{"net/minecraft/world/entity/player/Player"},0,null));}
			c.add(new InsnNode(Opcodes.RETURN));wrapper.maxStack=6;wrapper.maxLocals=caching?4:5;mixin.methods.add(wrapper);
		}
		return 2;
	}
}
