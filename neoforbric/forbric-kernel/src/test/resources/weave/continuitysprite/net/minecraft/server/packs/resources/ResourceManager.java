package net.minecraft.server.packs.resources;

/** A stand-in: only whether a texture exists, which is all the emissive lookup asks. */
public interface ResourceManager {
	boolean has(String texture);
}
