package fixture.renamedbody;

import net.minecraft.network.chat.Style;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

/** Adds an entity the way the merged game does, then sets a colour and a shadow colour; returns both traces. */
public class Probe {
	public String run() {
		PersistentEntitySectionManager manager = new PersistentEntitySectionManager();
		manager.addEntity(null, false);
		Style style = new Style();
		style.withColor(1);
		style.applyShadow(2);
		return "manager[" + manager.trace + "] style[" + style.trace + "]";
	}
}
