package fixture.fabricblockbreak;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Breaks a chest in survival, bedrock (which refuses removal) in survival, and dirt in creative; reports what
 * PlayerBlockBreakEvents.AFTER saw and what each break answered.
 */
public class Probe {
	public String run() {
		List<String> after = new ArrayList<>();
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) ->
				after.add(pos.name() + "/" + state.name() + "/" + (blockEntity == null ? "-" : blockEntity.name())));
		ServerLevel level = new ServerLevel();
		level.place(new BlockPos("chest"), true, new BlockEntity("chest-inventory"));
		level.place(new BlockPos("bedrock"), false, null);
		level.place(new BlockPos("dirt"), true, null);
		ServerPlayerGameMode survival = new ServerPlayerGameMode(level, new ServerPlayer());
		ServerPlayerGameMode creative = new ServerPlayerGameMode(level, new ServerPlayer());
		creative.creative = true;
		boolean chest = survival.destroyBlock(new BlockPos("chest"));
		boolean bedrock = survival.destroyBlock(new BlockPos("bedrock"));
		boolean dirt = creative.destroyBlock(new BlockPos("dirt"));
		return "broke=" + chest + "," + bedrock + "," + dirt + " after=" + after;
	}
}
