package com.zurrtum.create.mixin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fixture.createstructure.ControlProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureEntityInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shaped like Create Fly's StructureTemplateMixin, written against vanilla: the processors are picked up just before
 * placeInWorld calls placeEntities, applied to the entity iterator inside placeEntities, and dropped at its TAIL.
 */
@Mixin(StructureTemplate.class)
public class StructureTemplateMixin {
	@Unique
	private static final ThreadLocal<List<ControlProcessor>> list = new ThreadLocal<>();

	@Inject(method = "placeInWorld(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Lnet/minecraft/util/RandomSource;I)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;placeEntities(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Mirror;Lnet/minecraft/world/level/block/Rotation;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;ZLnet/minecraft/util/ProblemReporter;)V"))
	private void setProcessors(ServerLevelAccessor level, BlockPos position, BlockPos referencePos, StructurePlaceSettings settings,
			RandomSource random, int updateMode, CallbackInfoReturnable<Boolean> cir) {
		if (level instanceof Level) {
			List<ControlProcessor> controls = new ArrayList<>();
			for (StructureProcessor processor : settings.getProcessors()) {
				if (processor instanceof ControlProcessor control) controls.add(control);
			}
			if (!controls.isEmpty()) list.set(controls);
		}
	}

	@WrapOperation(method = "placeEntities(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Mirror;Lnet/minecraft/world/level/block/Rotation;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;ZLnet/minecraft/util/ProblemReporter;)V",
			at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
	private Iterator<StructureEntityInfo> getIterator(List<StructureEntityInfo> instance, Operation<Iterator<StructureEntityInfo>> original,
			@Local(argsOnly = true) ServerLevelAccessor level) {
		Iterator<StructureEntityInfo> iterator = original.call(instance);
		List<ControlProcessor> controls = list.get();
		if (controls == null) return iterator;
		List<StructureEntityInfo> controlled = new ArrayList<>();
		while (iterator.hasNext()) {
			String name = iterator.next().name();
			for (ControlProcessor control : controls) name = control.control(name);
			controlled.add(new StructureEntityInfo(name));
		}
		return controlled.iterator();
	}

	@Inject(method = "placeEntities(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Mirror;Lnet/minecraft/world/level/block/Rotation;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;ZLnet/minecraft/util/ProblemReporter;)V",
			at = @At("TAIL"))
	private void clearProcessors(CallbackInfo ci) {
		list.remove();
	}
}
