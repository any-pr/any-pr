package net.minecraft.world.level.levelgen.structure.templatesystem;

import java.util.List;

/** A stand-in: the processors of one placement. */
public class StructurePlaceSettings {
	private final List<StructureProcessor> processors;

	public StructurePlaceSettings(List<StructureProcessor> processors) {
		this.processors = processors;
	}

	public List<StructureProcessor> getProcessors() {
		return processors;
	}
}
