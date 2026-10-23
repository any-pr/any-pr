package fixture.fabricblockstatecodec;

import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;

/**
 * Reads three variants with the model codec — a NeoForge custom model ("type"), a Fabric one ("fabric:type") and a
 * plain one — and the NeoForge one with the weighted codec, and says who read each and how.
 */
public class Probe {
	public String run() {
		MapOps ops = new MapOps();
		Codec<String> model = BlockStateModel.Unbaked.CODEC;
		Map<String, Object> neoforge = Map.of("type", "sophisticatedbackpacks:backpack");
		return String.join(" | ",
				read(model, ops, neoforge),
				read(model, ops, Map.of("fabric:type", "continuity:ctm")),
				read(model, ops, Map.of("model", "minecraft:block/stone")),
				read(BlockStateModel.Unbaked.HARDCODED_WEIGHTED_CODEC, ops, neoforge));
	}

	private static String read(Codec<String> codec, MapOps ops, Object variant) {
		return codec.decode(ops, variant).result().map(pair -> pair.getFirst()).orElse("unreadable");
	}
}
