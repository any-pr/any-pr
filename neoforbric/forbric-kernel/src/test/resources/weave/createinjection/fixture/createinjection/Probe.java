package fixture.createinjection;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

/** Moves a carriage from section 11 to section 12 and reports who heard which move, in order. */
public class Probe {
	public String probe() {
		new PersistentEntitySectionManager().move(new EntityAccess() {
			@Override
			public String name() {
				return "carriage";
			}

			@Override
			public long sectionKey() {
				return 12L;
			}
		}, 11L);
		return Trail.drain();
	}
}
