package net.minecraft.world.level.levelgen.structure.templatesystem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Stand-in: what vanilla's placeInWorld read placeEntities' arguments off, with vanilla's getter names. */
public class StructurePlaceSettings {
	private final Mirror mirror;
	private final Rotation rotation;

	public StructurePlaceSettings(Mirror mirror, Rotation rotation) {
		this.mirror = mirror;
		this.rotation = rotation;
	}

	public Mirror getMirror() {
		return mirror;
	}

	public Rotation getRotation() {
		return rotation;
	}

	public BlockPos getRotationPivot() {
		return new BlockPos(1, 2, 3);
	}

	public BoundingBox getBoundingBox() {
		return new BoundingBox(16);
	}

	public boolean shouldFinalizeEntities() {
		return true;
	}

	public boolean isIgnoreEntities() {
		return false;
	}
}
