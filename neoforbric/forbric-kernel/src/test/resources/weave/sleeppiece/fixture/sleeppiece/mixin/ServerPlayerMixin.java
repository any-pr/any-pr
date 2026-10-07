package fixture.sleeppiece.mixin;

import java.util.List;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Written against vanilla, where startSleepInBed makes its checks itself: apoli's avian veto (a cancellable @Inject at
 * the respawn call whose callback a lambda over its powers sets to a left) and fabric-entity-events' direction wrap (a
 * @WrapOperation of the facing read with a @Cancellable callback). The HEAD handler binds where it is, so the mixin
 * reads PARTIAL, as a real one's others do.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "startSleepInBed", at = @At("HEAD"))
	private void sleeppiece$head(BlockPos pos, CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
		((ServerPlayer) (Object) this).trace.append("head;");
	}

	@Inject(method = "startSleepInBed", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;setRespawnPosition(Lnet/minecraft/server/level/ServerPlayer$RespawnConfig;Z)V"),
			cancellable = true)
	private void sleeppiece$avian(BlockPos pos, CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
		List.of("prevent_sleep").forEach(power -> {
			if (((ServerPlayer) (Object) this).avian) cir.setReturnValue(Either.left(Player.BedSleepingProblem.OTHER_PROBLEM));
		});
	}

	@WrapOperation(method = "startSleepInBed", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getValue(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;"))
	private Comparable<?> sleeppiece$direction(BlockState state, Property<?> property, Operation<Comparable<?>> original, BlockPos pos,
			@Cancellable CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
		Comparable<?> facing = original.call(state, property);
		if (((ServerPlayer) (Object) this).noDirection) {
			cir.setReturnValue(Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_HERE));
			return null;
		}
		return facing;
	}
}
