package primitiveenderdragon.briefrelease.importantbugfix.mixin;

import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ASFServerGamePacketListenerImpl {

	@Shadow
	public ServerPlayer player;
	Player playerEntity = player;

	@Inject(method = "handleSetCarriedItem", at = @At("TAIL"))
	private void onHandleSetCarriedItemTail(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
		player.resetAttackStrengthTicker();
	}

	@Inject(method = "handlePlayerAction", at = @At("HEAD"))
	private void onHandlePlayerActionHead(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		((ASFLivingEntityAccessor) player).invokeCollectEquipmentChanges();
	}

	@Inject(method = "handlePlayerAction", at = @At("TAIL"))
	private void onHandlePlayerActionTail(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		player.resetAttackStrengthTicker();
	}

	@Inject(method = "handleAttack", at = @At("HEAD"))
	private void onHandleAttack(ServerboundAttackPacket packet, CallbackInfo ci) {
		((ASFLivingEntityAccessor) player).invokeCollectEquipmentChanges();
	}
}
