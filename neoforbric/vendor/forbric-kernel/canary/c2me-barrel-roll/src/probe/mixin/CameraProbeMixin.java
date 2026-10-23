package probe.mixin;
import probe.Probe;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Camera.class)
public class CameraProbeMixin {
 @Inject(method="extractRenderState",at=@At("TAIL")) private void probeCamera(CameraRenderState state,float partial,CallbackInfo ci){Probe.camera((Camera)(Object)this,state,partial);}
}
