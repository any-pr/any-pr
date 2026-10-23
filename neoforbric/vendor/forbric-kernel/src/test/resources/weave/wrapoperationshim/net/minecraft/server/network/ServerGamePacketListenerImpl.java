package net.minecraft.server.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fixture stand-in for the surviving carrier's body: it makes the clone query in the carrier's form, so a wrap
 * written against vanilla's (level, pos, includeData) form names a call this body never makes.
 */
public class ServerGamePacketListenerImpl {
	private final LevelReader level = new LevelReader("overworld");
	private final Player player = new Player("Steve");
	private final BlockState state = new BlockState();
	private ItemStack picked;

	public void handlePickItemFromBlock(ServerboundPickItemFromBlockPacket packet) {
		picked = state.getCloneItemStack(packet.pos(), level, packet.includeData(), player);
	}

	/** The harness's probe: one pick, and what came of it. */
	public String probe() {
		handlePickItemFromBlock(new ServerboundPickItemFromBlockPacket(new BlockPos(1, 2, 3), true));
		return String.valueOf(picked);
	}
}
