/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.mixin;
import java.util.List;
import java.util.function.Predicate;
import org.objectweb.asm.tree.*;

/** Reviewed compat-only mixins whose entire job belongs to an absent optional mod. */
final class OptionalMixinDependencies {
	private OptionalMixinDependencies(){ }
	static String absent(ClassNode mixin,Predicate<String> installed){
		if(!mixin.name.equals("net/diebuddies/mixins/immediatelyfast/MixinSignText")||installed.test("immediatelyfast")
				||!mixin.fields.isEmpty()||!mixin.interfaces.isEmpty())return null;
		List<MethodNode> injectors=mixin.methods.stream().filter(m->MixinFit.injectorOf(m)!=null).toList();
		if(injectors.size()!=1)return null;
		if(!List.of("immediatelyFast$shouldCache").equals(MixinFit.value(MixinFit.injectorOf(injectors.getFirst()),"method")))return null;
		return "immediatelyfast";
	}
}
