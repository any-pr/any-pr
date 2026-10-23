package net.minecraft.server.level;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.level.Level;

/** A stand-in that records what happened to the entities in it. */
public class ServerLevel extends Level {
	public final List<String> events = new ArrayList<>();
}
