package net.minecraft.src;

import net.minecraft.server.MinecraftServer;

public class WorldServerMulti extends WorldServer {
	public WorldServerMulti(MinecraftServer var1, ISaveHandler var2, String var3, int var4, WorldSettings var5, WorldServer var6) {
		super(var1, var2, var3, var4, var5);
		this.mapStorage = var6.mapStorage;
	}
}
