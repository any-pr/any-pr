/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import net.forbric.kernel.util.ForbricLog;

/** Run Create's original interaction handler at the live carrier reaction sites. */
public final class CreateFluidMixinAdapter {
	public static final String PROPERTY = "forbric.createFluidMixins";
	static final String MIXIN = "com/zurrtum/create/mixin/LiquidBlockMixin";
	static final String TARGET = "net/minecraft/world/level/block/LiquidBlock";
	private CreateFluidMixinAdapter() { }

	public static int adapt(ClassNode mixin, Function<String, ClassNode> targets) {
		if (!MIXIN.equals(mixin.name) || "off".equalsIgnoreCase(System.getProperty(PROPERTY))) return 0;
		MethodNode original = CarpetMixinAdapter.named(mixin, "shouldSpreadLiquid");
		ClassNode target = targets.apply(TARGET);
		if (original == null || target == null || (original.access & Opcodes.ACC_STATIC) != 0
				|| !CarpetFluidMixinAdapter.HANDLER.equals(original.desc)) return 0;
		AnnotationNode injection = MixinFit.injectorOf(original);
		List<AnnotationNode> points = injection == null ? List.of() : MixinFit.atNodes(injection);
		if (!CarpetMixinAdapter.selects(injection, "shouldSpreadLiquid(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z") || points.size() != 1
				|| !"HEAD".equals(MixinFit.value(points.getFirst(), "value"))
				|| !Boolean.TRUE.equals(MixinFit.value(injection, "cancellable"))) return 0;
		// The audited upstream handler only calls Create's registry and cancels with false when handled.
		List<MethodInsnNode> bodyCalls = new ArrayList<>();
		for (var instruction : original.instructions) if (instruction instanceof MethodInsnNode call) bodyCalls.add(call);
		if (bodyCalls.size() != 3 || !bodyCalls.get(0).owner.equals("com/zurrtum/create/infrastructure/fluids/FluidInteractionRegistry")
				|| !bodyCalls.get(0).name.equals("canInteract") || !bodyCalls.get(0).desc.equals(CarpetFluidMixinAdapter.INTERACT)
				|| !bodyCalls.get(1).owner.equals("java/lang/Boolean") || !bodyCalls.get(1).name.equals("valueOf")
				|| !bodyCalls.get(2).owner.equals(CarpetFluidMixinAdapter.CALLBACK) || !bodyCalls.get(2).name.equals("setReturnValue")) return 0;
		List<MethodNode> hosts = new ArrayList<>(); List<MethodInsnNode> calls = new ArrayList<>();
		for (String name : List.of("onPlace", "neighborChanged")) {
			MethodNode host = CarpetMixinAdapter.named(target, name);
			if (host == null) return 0;
			List<MethodInsnNode> nativeCalls = new ArrayList<>();
			for (var instruction : host.instructions) if (instruction instanceof MethodInsnNode call
					&& call.getOpcode() == Opcodes.INVOKESTATIC && CarpetFluidMixinAdapter.REGISTRY.equals(call.owner)
					&& call.name.equals("canInteract") && call.desc.equals(CarpetFluidMixinAdapter.INTERACT)) nativeCalls.add(call);
			if (nativeCalls.size() != 1) return 0;
			hosts.add(host); calls.add(nativeCalls.getFirst());
		}
		FabricFluidFlowMixinAdapter.retainOriginal(original, injection);
		for (int i = 0; i < hosts.size(); i++) mixin.methods.add(FabricFluidFlowMixinAdapter.wrapper(mixin, original, hosts.get(i), calls.get(i)));
		ForbricLog.info("[Forbric/Create] original fluid interaction callback now guards both live carrier reaction sites");
		return 2;
	}
}
