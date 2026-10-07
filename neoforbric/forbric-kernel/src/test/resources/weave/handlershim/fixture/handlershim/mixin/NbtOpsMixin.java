package fixture.handlershim.mixin;

import java.util.List;

import com.mojang.datafixers.util.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Written the way a mod writes it against VANILLA's lambda: (List, CompoundTag, Pair), in that order. */
@Mixin(NbtOps.class)
public abstract class NbtOpsMixin {
	@Inject(method = "lambda$mergeToMap$3", at = @At("HEAD"))
	private static void handlershim$seeEntry(List<String> trail, CompoundTag tag, Pair<String, String> entry, CallbackInfo ci) {
		trail.add("handler saw " + entry.getFirst() + " in " + tag.name());
	}
}
