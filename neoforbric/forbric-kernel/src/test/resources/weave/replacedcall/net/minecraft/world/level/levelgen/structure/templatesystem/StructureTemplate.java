package net.minecraft.world.level.levelgen.structure.templatesystem;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A game-side target in its merged shape (hand-written; its name and methods are the MergedBaseCalleeSwaps.REPLACED
 * row's): placeInWorld hands the settings to NeoForge's addEntitiesToWorld where vanilla read five arguments off them for
 * placeEntities, which is gone; MinecraftForge's reshaped placeEntities is declared and nothing calls it.
 */
public class StructureTemplate {
	private final List<String> trace = new ArrayList<>();

	public boolean placeInWorld(ServerLevelAccessor level, BlockPos position, BlockPos referencePos, StructurePlaceSettings settings,
			RandomSource random, int updateMode) {
		trace.add("blocks");
		ProblemReporter reporter = new ProblemReporter() {
		};
		if (!settings.isIgnoreEntities()) this.addEntitiesToWorld(level, position, settings, reporter);
		trace.add("done");
		return true;
	}

	private void addEntitiesToWorld(ServerLevelAccessor level, BlockPos position, StructurePlaceSettings settings,
			ProblemReporter reporter) {
		trace.add("entities");
	}

	private void placeEntities(ServerLevelAccessor level, BlockPos position, Mirror mirror, Rotation rotation, BoundingBox box,
			boolean finalizeEntities, ProblemReporter reporter, StructurePlaceSettings settings) {
		trace.add("forge");
	}

	/** What a guest without a shadow of {@code trace} records through. */
	public void note(String event) {
		trace.add(event);
	}

	/** The harness probe: one placement the guest leaves to the game, one it takes over and cancels. */
	public String probe() {
		ServerLevelAccessor level = new ServerLevelAccessor() {
		};
		StructureTemplate kept = new StructureTemplate();
		kept.placeInWorld(level, new BlockPos(0, 64, 0), new BlockPos(0, 0, 0), new StructurePlaceSettings(Mirror.NONE, Rotation.NONE), null, 2);
		StructureTemplate taken = new StructureTemplate();
		taken.placeInWorld(level, new BlockPos(0, 64, 0), new BlockPos(0, 0, 0),
				new StructurePlaceSettings(Mirror.FRONT_BACK, Rotation.CLOCKWISE_90), null, 2);
		return "kept=" + String.join(",", kept.trace) + " taken=" + String.join(",", taken.trace);
	}
}
