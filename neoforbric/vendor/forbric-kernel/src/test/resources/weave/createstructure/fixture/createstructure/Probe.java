package fixture.createstructure;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureEntityInfo;

/**
 * Places one template twice: first with the mod's control processor, then with none. What each placement added says
 * whether the processor was picked up before the entities were added, applied while they were iterated, and dropped
 * after — a second placement that still sees it means the cleanup never ran.
 */
public class Probe {
	public String probe() {
		StructureTemplate template = new StructureTemplate(List.of(new StructureEntityInfo("pig"), new StructureEntityInfo("cow")));
		return place(template, new StructurePlaceSettings(List.of(new ControlProcessor("tamed")))) + " | "
				+ place(template, new StructurePlaceSettings(List.of()));
	}

	private static String place(StructureTemplate template, StructurePlaceSettings settings) {
		Level level = new Level();
		template.placeInWorld(level, new BlockPos(), new BlockPos(), settings, new RandomSource(), 2);
		return String.join(", ", level.entities);
	}
}
