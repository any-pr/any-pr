package probe.mixin;
import probe.Probe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public class MinecraftProbeMixin {
 @Inject(method="tick",at=@At("TAIL")) private void probeTick(CallbackInfo ci){Probe.tick();}
}
