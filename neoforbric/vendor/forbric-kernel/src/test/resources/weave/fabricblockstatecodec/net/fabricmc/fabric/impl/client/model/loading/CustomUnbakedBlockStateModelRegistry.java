package net.fabricmc.fabric.impl.client.model.loading;

import com.mojang.serialization.Codec;

import fixture.fabricblockstatecodec.VariantCodec;

/** Hand-written stand-in for fabric-model-loading's registry: Fabric's codecs, reading "fabric:type". */
public final class CustomUnbakedBlockStateModelRegistry {
	public static final Codec<String> WEIGHTED_MODEL_CODEC = new VariantCodec("fabric-weighted", "fabric:type");
	public static final Codec<String> MODEL_CODEC = new VariantCodec("fabric", "fabric:type");

	private CustomUnbakedBlockStateModelRegistry() {
	}
}
