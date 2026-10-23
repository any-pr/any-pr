package forbric.fluidneo;

import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;

/**
 * A NeoForge mod's fluid interaction, registered at common setup as mods do: lava next to an iron block or an emerald
 * block becomes glowstone. Uses NeoForge's API only. Fixture of fluid-parity-gate.py --mods.
 */
@Mod("forbricfluidneo")
public final class FluidNeoCanary {
	public FluidNeoCanary(IEventBus modBus) {
		modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> {
			FluidInteractionRegistry.addInteraction(NeoForgeMod.LAVA_TYPE.value(), new FluidInteractionRegistry.InteractionInformation(
					(level, current, relative, state) -> level.getBlockState(relative).is(Blocks.IRON_BLOCK)
							|| level.getBlockState(relative).is(Blocks.EMERALD_BLOCK),
					(level, current, relative, state) -> {
						level.setBlockAndUpdate(current, Blocks.GLOWSTONE.defaultBlockState());
						System.out.println("[FluidCanary] neoforge lava rule fired at " + current.toShortString());
					}));
			System.out.println("[FluidCanary] neoforge rules registered");
		}));
	}
}
