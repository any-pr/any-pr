/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.mixin;
import java.util.*;
import org.objectweb.asm.tree.*;
import net.forbric.kernel.util.ForbricLog;
import net.forbric.kernel.transform.CreativePagerBridgeInjector;

/** Retains Fabric PageUp/PageDown input while the API and rendering share the carrier's pager. */
public final class FabricCreativePagerMixinAdapter {
	public static final String PROPERTY="forbric.fabricCreativeKeyboard";
	private static final String MIXIN="net/fabricmc/fabric/mixin/creativetab/client/CreativeModeInventoryScreenMixin";
	private FabricCreativePagerMixinAdapter(){ }
	public static boolean enabled(){return CreativePagerBridgeInjector.enabled()&&!"off".equalsIgnoreCase(System.getProperty(PROPERTY,"on"));}
	public static int adapt(ClassNode mixin){
		if(!enabled()||!mixin.name.equals(MIXIN)||mixin.methods.size()<=2)return 0;
		MethodNode keys=mixin.methods.stream().filter(m->m.name.equals("keyPressed")&&MixinFit.injectorOf(m)!=null).findFirst().orElse(null);
		if(keys==null)return 0;
		int previous=0,next=0;
		for(var i:keys.instructions){
			if(i instanceof FieldInsnNode f&&f.owner.equals(MIXIN))return 0;
			if(i instanceof MethodInsnNode c&&c.owner.equals(MIXIN)){
				if(c.name.equals("switchToPreviousPage")&&c.desc.equals("()Z"))previous++;
				else if(c.name.equals("switchToNextPage")&&c.desc.equals("()Z"))next++;
				else return 0;
			}
		}
		if(previous!=1||next!=1)return 0;
		mixin.methods.removeIf(m->m!=keys&&!m.name.equals("<init>"));mixin.fields.clear();
		ForbricLog.info("[Forbric/CreativePager] retained Fabric's PageUp/PageDown callback; its API and rendering "
				+ "use the same carrier pager instead of creating a second page state");return 1;
	}
}
