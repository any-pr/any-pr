package fixture.defaultconflict.mixin;

import fixture.defaultconflict.BlockEntityRenderFabricExtension;
import net.minecraft.fixture.defaultconflict.BlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** EntityCulling's shape: against vanilla this is the renderer's only default, so the mod has nothing to settle. */
@Mixin(BlockEntityRenderer.class)
public interface BlockEntityRendererMixin<T> extends BlockEntityRenderFabricExtension<T> {
}
