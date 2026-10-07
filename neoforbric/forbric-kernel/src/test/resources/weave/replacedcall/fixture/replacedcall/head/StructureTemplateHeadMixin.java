package fixture.replacedcall.head;

import net.minecraft.core.BlockPos;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * StructureTemplateMixin's HEAD injector alone, compiled against vanilla: the only anchor it names is placeEntities by
 * name, whose vanilla shape the merged base no longer has. No anchor resolves (it records through the target's public
 * note, not a shadow), so the mixin is UNFIT.
 */
@Mixin(StructureTemplate.class)
public abstract class StructureTemplateHeadMixin {
	@Inject(method = "placeEntities", at = @At("HEAD"), cancellable = true)
	private void processAndPlaceEntities(ServerLevelAccessor level, BlockPos position, Mirror mirror, Rotation rotation,
			BlockPos pivot, BoundingBox box, boolean finalizeEntities, ProblemReporter reporter, CallbackInfo ci) {
		((StructureTemplate) (Object) this).note("head:" + position + "|" + mirror + "|" + rotation + "|" + pivot + "|" + box + "|" + finalizeEntities);
		if (mirror == Mirror.FRONT_BACK) ci.cancel();
	}
}
