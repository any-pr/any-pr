package fixture.sleeppiece;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Tries a bed three times — a plain player, one an avian power keeps out of beds, one no direction suits — and returns what each did. */
public class Probe {
	public String run() {
		StringBuilder out = new StringBuilder();
		for (String kind : new String[] {"plain", "avian", "nodirection"}) {
			ServerPlayer player = new ServerPlayer();
			player.avian = kind.equals("avian");
			player.noDirection = kind.equals("nodirection");
			Object result = player.startSleepInBed(new BlockPos());
			out.append(kind).append('[').append(player.trace).append(result).append("] ");
		}
		return out.toString().trim();
	}
}
