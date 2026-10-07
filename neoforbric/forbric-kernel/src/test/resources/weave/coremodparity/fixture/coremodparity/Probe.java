package fixture.coremodparity;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerPotBlock;

/** Picks a potted poppy and asks the guest's added method about it; reports each answer or the exception it threw. */
public class Probe {
	public String probe() {
		FlowerPotBlock pot = new FlowerPotBlock(() -> new Block("poppy"));
		return "pick=" + attempt(pot::getCloneItemStack) + " describe=" + attempt(((PotDescriber) (Object) pot)::describePot)
				+ " hooks=" + Trace.SEEN;
	}

	private static String attempt(java.util.function.Supplier<String> call) {
		try {
			return call.get();
		} catch (RuntimeException failure) {
			return failure.getClass().getSimpleName();
		}
	}
}
