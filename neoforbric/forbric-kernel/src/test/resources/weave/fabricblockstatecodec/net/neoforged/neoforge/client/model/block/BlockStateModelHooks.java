package net.neoforged.neoforge.client.model.block;

import com.mojang.serialization.Codec;

import fixture.fabricblockstatecodec.VariantCodec;

/** Hand-written stand-in, not NeoForge code: the codecs NeoForge builds the variant fields from, reading "type". */
public final class BlockStateModelHooks {
	private BlockStateModelHooks() {
	}

	public static Codec<String> weightedCodec() {
		return new VariantCodec("neoforge-weighted", "type");
	}

	public static Codec<String> modelCodec() {
		return new VariantCodec("neoforge", "type");
	}
}
