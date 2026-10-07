package fixture.replacedcall.mixin;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A guest mixin compiled against vanilla, where placeInWorld calls placeEntities (MoogsStructureLib's Fabric
 * EntityProcessorMixin has this shape: context before the call, cleared after it, and its own entity placement at the
 * call's HEAD, cancelling vanilla's when it took over).
 */
@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin {
	private static final String PLACE_ENTITIES = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;"
			+ "placeEntities(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;"
			+ "Lnet/minecraft/world/level/block/Mirror;Lnet/minecraft/world/level/block/Rotation;Lnet/minecraft/core/BlockPos;"
			+ "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;ZLnet/minecraft/util/ProblemReporter;)V";

	@Shadow @Final private List<String> trace;

	@Inject(method = "placeInWorld", at = @At(value = "INVOKE", target = PLACE_ENTITIES))
	private void captureContext(CallbackInfoReturnable<Boolean> cir) {
		trace.add("before");
	}

	@Inject(method = "placeInWorld", at = @At(value = "INVOKE", target = PLACE_ENTITIES, shift = At.Shift.AFTER))
	private void clearContext(CallbackInfoReturnable<Boolean> cir) {
		trace.add("after");
	}

	@Inject(method = "placeEntities", at = @At("HEAD"), cancellable = true)
	private void processAndPlaceEntities(ServerLevelAccessor level, BlockPos position, Mirror mirror, Rotation rotation,
			BlockPos pivot, BoundingBox box, boolean finalizeEntities, ProblemReporter reporter, CallbackInfo ci) {
		trace.add("head:" + position + "|" + mirror + "|" + rotation + "|" + pivot + "|" + box + "|" + finalizeEntities);
		if (mirror == Mirror.FRONT_BACK) ci.cancel();
	}
}
