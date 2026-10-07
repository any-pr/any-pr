package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;

/** Fixture stand-in. */
public record ServerboundPickItemFromBlockPacket(BlockPos pos, boolean includeData) {
	@Override
	public String toString() {
		return "pick" + pos;
	}
}
