/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.mixin;
import java.util.*;import java.util.function.Function;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;

/** Keep Create's redstone, collision and block-break controls around the native operations and event gates. */
public final class CreateInteractionMixinAdapters {
	public static final String PROPERTY="forbric.createInteractionMixins";
	private static final String STATE="net/minecraft/world/level/block/state/BlockState", BLOCK="net/minecraft/world/level/block/Block", POS="net/minecraft/core/BlockPos", OP=MixinWrapOperationShim.OPERATION;
	private CreateInteractionMixinAdapters() { }
	public static int adapt(ClassNode mixin,Function<String,ClassNode> targets){
		if("off".equalsIgnoreCase(System.getProperty(PROPERTY)))return 0;
		return switch(mixin.name){
			case "com/zurrtum/create/mixin/SignalGetterMixin"->signal(mixin,targets);
			case "com/zurrtum/create/mixin/EntityMixin"->bounce(mixin,targets);
			case "com/zurrtum/create/client/mixin/MultiPlayerGameModeMixin"->leftClick(mixin,targets)+breaking(mixin,targets,false);
			case "com/zurrtum/create/mixin/ServerPlayerGameModeMixin"->breaking(mixin,targets,true);
			default->0;
		};
	}
	private static int signal(ClassNode mixin,Function<String,ClassNode> targets){
		MethodNode original=CreateInjectionAdapters.named(mixin,"skip");ClassNode target=targets.apply("net/minecraft/world/level/SignalGetter");MethodNode host=target==null?null:CreateInjectionAdapters.named(target,"getSignal");
		String live="L"+STATE+";shouldCheckWeakPower(Lnet/minecraft/world/level/SignalGetter;L"+POS+";Lnet/minecraft/core/Direction;)Z";
		if(original==null||host==null||Type.getArgumentTypes(original.desc).length!=5||!Type.getArgumentTypes(original.desc)[3].equals(Type.getObjectType(OP))||CarpetMixinAdapter.count(host,live)!=1)return 0;
		AnnotationNode inject=MixinFit.injectorOf(original);if(inject==null)return 0;CarpetMixinAdapter.set(MixinFit.atNodes(inject).getFirst(),"target",live);
		String desc="(L"+STATE+";Lnet/minecraft/world/level/SignalGetter;L"+POS+";Lnet/minecraft/core/Direction;L"+OP+";)Z";
		MethodNode outer=new MethodNode(Opcodes.ACC_PRIVATE,original.name,desc,null,null);outer.visibleAnnotations=new ArrayList<>(List.of(inject));InsnList c=outer.instructions;
		load(c,0,1,2,3);c.add(new VarInsnNode(Opcodes.ALOAD,5));c.add(new InsnNode(Opcodes.ICONST_1));c.add(new LdcInsnNode("0,1"));c.add(new InsnNode(Opcodes.ICONST_3));c.add(new TypeInsnNode(Opcodes.ANEWARRAY,"java/lang/Object"));c.add(new InsnNode(Opcodes.DUP));c.add(new InsnNode(Opcodes.ICONST_2));c.add(new VarInsnNode(Opcodes.ALOAD,4));c.add(new InsnNode(Opcodes.AASTORE));reordered(c);c.add(new VarInsnNode(Opcodes.ALOAD,4));finish(mixin,original,outer,inject,Opcodes.IRETURN,6);return 1;
	}
	private static int bounce(ClassNode mixin,Function<String,ClassNode> targets){
		String authored="(Lnet/minecraft/world/entity/Entity;L"+BLOCK+";L"+OP+";L"+STATE+";)D";
		MethodNode original=mixin.methods.stream().filter(m->m.desc.equals(authored)&&MixinFit.injectorOf(m)!=null).findFirst().orElse(null);ClassNode target=targets.apply("net/minecraft/world/entity/Entity");if(original==null||target==null)return 0;
		if(!original.desc.equals("(Lnet/minecraft/world/entity/Entity;L"+BLOCK+";L"+OP+";L"+STATE+";)D"))return 0;
		List<String> sourceSelectors=MixinFit.stringList(MixinFit.value(MixinFit.injectorOf(original),"method"));if(sourceSelectors.size()!=1||sourceSelectors.getFirst().indexOf('(')<0)return 0;String hostName=sourceSelectors.getFirst().substring(0,sourceSelectors.getFirst().indexOf('('));
		String call="getBlockBounciness(L"+POS+";L"+STATE+";)D";List<MethodNode> hosts=target.methods.stream().filter(m->m.name.equals(hostName)&&CarpetMixinAdapter.count(m,"L"+target.name+";"+call)==1).toList();if(hosts.size()!=1)return 0;
		AnnotationNode inject=MixinFit.injectorOf(original);List<AnnotationNode> ats=MixinFit.atNodes(inject);if(ats.size()!=1)return 0;CarpetMixinAdapter.set(inject,"method",List.of(hosts.getFirst().name+hosts.getFirst().desc));CarpetMixinAdapter.set(ats.getFirst(),"target","L"+target.name+";"+call);
		MethodNode outer=new MethodNode(Opcodes.ACC_PRIVATE,original.name,"(Lnet/minecraft/world/entity/Entity;L"+POS+";L"+STATE+";L"+OP+";)D",null,null);outer.visibleAnnotations=new ArrayList<>(List.of(inject));InsnList c=outer.instructions;
		load(c,0,1,3);c.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,STATE,"getBlock","()L"+BLOCK+";",false));fixed(c,List.of(Type.getObjectType(target.name),Type.getObjectType(POS),Type.getObjectType(STATE)),new int[]{1,2,3},4);c.add(new VarInsnNode(Opcodes.ALOAD,3));finish(mixin,original,outer,inject,Opcodes.DRETURN,5);return 1;
	}
	private static int leftClick(ClassNode mixin,Function<String,ClassNode> targets){
		MethodNode handler=CreateInjectionAdapters.named(mixin,"onLeftClick2");ClassNode target=targets.apply("net/minecraft/client/multiplayer/MultiPlayerGameMode");if(handler==null||target==null)return 0;AnnotationNode inject=MixinFit.injectorOf(handler);if(inject==null)return 0;
		List<MethodNode> hosts=target.methods.stream().filter(m->m.name.equals("lambda$startDestroyBlock$1")&&m.desc.contains("LeftClickBlock;")&&CarpetMixinAdapter.count(m,"L"+target.name+";destroyBlock(L"+POS+";)Z")==1).toList();if(hosts.size()!=1)return 0;String selector=hosts.getFirst().name+hosts.getFirst().desc;
		if(MixinFit.stringList(MixinFit.value(inject,"method")).equals(List.of(selector)))return 0;CarpetMixinAdapter.set(inject,"method",List.of(selector));return 1;
	}
	private static int breaking(ClassNode mixin,Function<String,ClassNode> targets,boolean server){
		MethodNode original=CreateInjectionAdapters.named(mixin,server?"removeBlock":"breakBlock");String owner=server?"net/minecraft/server/level/ServerPlayerGameMode":"net/minecraft/client/multiplayer/MultiPlayerGameMode";ClassNode target=targets.apply(owner);MethodNode host=target==null?null:CreateInjectionAdapters.named(target,"destroyBlock");
		if(original==null||host==null||MixinFit.injectorOf(original)==null||Type.getArgumentTypes(original.desc).length!=(server?6:7))return 0;
		List<MethodInsnNode> calls=new ArrayList<>();for(var i:host.instructions)if(i instanceof MethodInsnNode call&&(server?call.owner.equals(owner)&&call.name.equals("removeBlock"):call.owner.equals(STATE)&&call.name.equals("onDestroyedByPlayer")))calls.add(call);
		if(calls.isEmpty()||(!server&&calls.size()!=1)||calls.stream().map(c->c.desc).distinct().count()!=1)return 0;
		MethodInsnNode live=calls.getFirst();Type[] nativeArgs=Type.getArgumentTypes(live.desc);List<Type> params=new ArrayList<>(List.of(Type.getObjectType(live.owner)));params.addAll(List.of(nativeArgs));params.add(Type.getObjectType(OP));params.add(Type.getObjectType(BLOCK));
		AnnotationNode inject=MixinFit.injectorOf(original);CarpetMixinAdapter.set(MixinFit.atNodes(inject).getFirst(),"target","L"+live.owner+";"+live.name+live.desc);
		MethodNode outer=new MethodNode(Opcodes.ACC_PRIVATE,original.name,Type.getMethodDescriptor(Type.BOOLEAN_TYPE,params.toArray(Type[]::new)),null,null);outer.visibleAnnotations=new ArrayList<>(List.of(inject));
		int[] slots=new int[params.size()];int slot=1;for(int i=0;i<params.size();i++){slots[i]=slot;slot+=params.get(i).getSize();}int op=1+nativeArgs.length;
		outer.invisibleParameterAnnotations=new List[params.size()];outer.invisibleParameterAnnotations[op+1]=original.invisibleParameterAnnotations[server?5:6];
		InsnList c=outer.instructions;c.add(new VarInsnNode(Opcodes.ALOAD,0));
		if(server){c.add(new VarInsnNode(Opcodes.ALOAD,0));c.add(new FieldInsnNode(Opcodes.GETFIELD,mixin.name,"level","Lnet/minecraft/server/level/ServerLevel;"));c.add(new VarInsnNode(Opcodes.ALOAD,slots[1]));c.add(new InsnNode(Opcodes.ICONST_0));}
		else{load(c,slots[1],slots[2],slots[6]);c.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/level/material/FluidState","createLegacyBlock","()L"+STATE+";",false));c.add(new IntInsnNode(Opcodes.BIPUSH,11));}
		fixed(c,params.subList(0,op),Arrays.copyOf(slots,op),slots[op]);c.add(new VarInsnNode(Opcodes.ALOAD,server?slots[2]:slots[0]));c.add(new VarInsnNode(Opcodes.ALOAD,slots[op+1]));finish(mixin,original,outer,inject,Opcodes.IRETURN,slot);return 1;
	}
	private static void load(InsnList c,int... slots){for(int slot:slots)c.add(new VarInsnNode(Opcodes.ALOAD,slot));}
	private static void reordered(InsnList c){c.add(new MethodInsnNode(Opcodes.INVOKESTATIC,MixinWrapOperationShim.RUNTIME,"reordered","(L"+OP+";ZLjava/lang/String;[Ljava/lang/Object;)L"+OP+";",false));}
	private static void fixed(InsnList c,List<Type> params,int[] slots,int operation){c.add(new VarInsnNode(Opcodes.ALOAD,operation));c.add(new InsnNode(Opcodes.ICONST_0));c.add(new LdcInsnNode(""));c.add(new IntInsnNode(Opcodes.BIPUSH,params.size()));c.add(new TypeInsnNode(Opcodes.ANEWARRAY,"java/lang/Object"));for(int i=0;i<params.size();i++){c.add(new InsnNode(Opcodes.DUP));c.add(new IntInsnNode(Opcodes.BIPUSH,i));Type type=params.get(i);c.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD),slots[i]));if(type.equals(Type.BOOLEAN_TYPE))c.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Boolean","valueOf","(Z)Ljava/lang/Boolean;",false));c.add(new InsnNode(Opcodes.AASTORE));}reordered(c);}
	private static void finish(ClassNode mixin,MethodNode original,MethodNode outer,AnnotationNode inject,int exit,int locals){original.name+="$forbricOriginal";CreateInjectionAdapters.removeInjector(original,inject);original.invisibleParameterAnnotations=null;original.visibleParameterAnnotations=null;outer.instructions.add(MixinHandlerShim.callOwn(mixin,false,original.name,original.desc));outer.instructions.add(new InsnNode(exit));outer.maxStack=locals+14;outer.maxLocals=locals;mixin.methods.add(outer);}
}
