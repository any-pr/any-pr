package fixture.createstructure;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;

/** The mod's own processor kind: one that controls the entities a placement adds, here by naming them. */
public class ControlProcessor extends StructureProcessor {
	private final String prefix;

	public ControlProcessor(String prefix) {
		this.prefix = prefix;
	}

	public String control(String entity) {
		return prefix + " " + entity;
	}
}
