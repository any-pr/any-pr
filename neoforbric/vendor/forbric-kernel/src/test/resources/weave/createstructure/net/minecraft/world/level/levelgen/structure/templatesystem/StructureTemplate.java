package net.minecraft.world.level.levelgen.structure.templatesystem;

import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * A stand-in for the merged base's StructureTemplate: NeoForge's placeInWorld hands the entities to
 * addEntitiesToWorld(level, pos, settings, reporter), which iterates them once. Vanilla's placeEntities, the method
 * Create's mixin names, is not here at all.
 */
public class StructureTemplate {
	private final List<StructureEntityInfo> entityInfoList;

	public StructureTemplate(List<StructureEntityInfo> entityInfoList) {
		this.entityInfoList = entityInfoList;
	}

	public boolean placeInWorld(ServerLevelAccessor level, BlockPos pos, BlockPos referencePos, StructurePlaceSettings settings,
			RandomSource random, int flags) {
		addEntitiesToWorld(level, pos, settings, new ProblemReporter());
		return true;
	}

	private void addEntitiesToWorld(ServerLevelAccessor level, BlockPos pos, StructurePlaceSettings settings, ProblemReporter reporter) {
		for (Iterator<StructureEntityInfo> entities = entityInfoList.iterator(); entities.hasNext();) {
			level.addFreshEntity(entities.next().name());
		}
	}

	public record StructureEntityInfo(String name) {
	}
}
