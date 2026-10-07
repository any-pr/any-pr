/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.mixin;

import java.util.*;
import org.objectweb.asm.tree.*;
import net.forbric.kernel.util.ForbricLog;

/** Keeps Fabric's registry trackers and disconnect unmap while the kernel owns the single registration freeze. */
public final class FabricRegistryInitializationMixinAdapter {
	public static final String PROPERTY="forbric.fabricRegistryInitialization";
	private static final String BASE="net/fabricmc/fabric/mixin/registry/sync/";
	private FabricRegistryInitializationMixinAdapter() { }
	public static boolean enabled(){return !"off".equalsIgnoreCase(System.getProperty(PROPERTY,"on"));}
	public static int adapt(ClassNode mixin){
		if(!enabled())return 0;
		if(mixin.name.equals(BASE+"BootstrapMixin")){
			MethodNode after=find(mixin,"afterInitialize"),delay=find(mixin,"delayRegistryFreeze");
			if(after==null||delay==null||MixinFit.injectorOf(after)==null||MixinFit.injectorOf(delay)==null)return 0;
			set(MixinFit.injectorOf(after),"at",List.of(at("TAIL")));
			delay.visibleAnnotations.remove(MixinFit.injectorOf(delay));
			ForbricLog.info("[Forbric/RegistrySync] restored Fabric's bootstrap state-ID and block-item trackers "
					+ "at the end of native bootstrap; the kernel retains the registry freeze");
			return 1;
		}
		if(!Set.of(BASE+"MainMixin",BASE+"client/MinecraftMixin").contains(mixin.name))return 0;
		MethodNode after=find(mixin,"afterModInit");if(after==null||MixinFit.injectorOf(after)==null)return 0;
		List<MethodInsnNode> calls=new ArrayList<>();
		for(var i:after.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals("net/minecraft/core/registries/BuiltInRegistries")&&c.name.equals("bootStrap")&&c.desc.equals("()V"))calls.add(c);
		if(calls.size()!=1)return 0;
		after.instructions.remove(calls.getFirst());
		if(mixin.name.equals(BASE+"client/MinecraftMixin"))set(MixinFit.injectorOf(after),"at",List.of(at("RETURN")));
		ForbricLog.info("[Forbric/RegistrySync] restored %s post-freeze trackers without repeating BuiltInRegistries.bootStrap",mixin.name);
		return 1;
	}
	private static MethodNode find(ClassNode node,String name){return node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElse(null);}
	private static AnnotationNode at(String value){AnnotationNode node=new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");node.values=new ArrayList<>(List.of("value",value));return node;}
	private static void set(AnnotationNode node,String key,Object value){for(int i=0;i<node.values.size();i+=2)if(node.values.get(i).equals(key)){node.values.set(i+1,value);return;}node.values.add(key);node.values.add(value);}
}
