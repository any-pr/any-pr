/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.forbric.kernel.mixin.FabricBlockStateCodecMixinAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * The routing between NeoForge's and Fabric's block-state codecs (issue #16), over two stand-ins that answer the
 * way each real codec does: NeoForge reads {@code "type"} or a plain variant, Fabric reads {@code "fabric:type"}
 * (failing on an id it has no deserializer for) or a plain variant. Each stand-in says which one answered.
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class KernelBlockStateModelFormatsTest {
	private static final Codec<String> NEOFORGE = new StandIn("neoforge", "type", null);
	private static final Codec<String> FABRIC = new StandIn("fabric", "fabric:type", "fabric:registered");

	private record StandIn(String who, String key, String only) implements Codec<String> {
		@Override
		public <T> DataResult<Pair<String, T>> decode(DynamicOps<T> ops, T input) {
			if (ops.getStream(input).result().isPresent()) return DataResult.success(Pair.of(who + " list", input));
			var map = ops.getMap(input).result().orElse(null);
			if (map == null) return DataResult.error(() -> who + ": not a variant");
			T type = map.get(key);
			if (type == null) return DataResult.success(Pair.of(who + " plain", input));
			String id = ops.getStringValue(type).getOrThrow();
			if (only != null && !only.equals(id)) return DataResult.error(() -> who + ": unknown type " + id);
			return DataResult.success(Pair.of(who + " " + id, input));
		}

		@Override
		public <T> DataResult<T> encode(String input, DynamicOps<T> ops, T prefix) {
			// Each writes its own models and plain variants, and says it was the one that wrote.
			return input.startsWith(who) || input.startsWith("plain") ? DataResult.success(ops.createString(who + " wrote " + input))
					: DataResult.error(() -> who + " cannot write " + input);
		}
	}

	private static String read(Codec<String> codec, String json) {
		return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
	}

	@Test void eachDialectReachesTheCodecThatReadsIt() {
		Codec<String> both = KernelBlockStateModelFormats.either(NEOFORGE, FABRIC);
		// The #16 shape: Sophisticated Backpacks' blockstate. Before, Fabric's codec read it as a plain variant.
		assertEquals("neoforge sophisticatedbackpacks:backpack_model_loader", read(both,
				"{\"type\": \"sophisticatedbackpacks:backpack_model_loader\", \"model\": \"sophisticatedbackpacks:block/backpack\", \"y\": 90}"));
		assertEquals("fabric plain", read(FABRIC, "{\"type\": \"sophisticatedbackpacks:backpack_model_loader\", \"model\": \"m\"}"),
				"the stand-in reproduces what fabric-model-loading alone made of it");
		assertEquals("neoforge plain", read(both, "{\"model\": \"minecraft:block/stone\"}"), "a plain variant reads as on NeoForge");
		assertEquals("fabric fabric:registered", read(both, "{\"fabric:type\": \"fabric:registered\", \"model\": \"m\"}"));
		assertEquals("fabric list", read(both, "[{\"model\": \"a\"}, {\"fabric:type\": \"fabric:registered\"}]"),
				"a variant list with a Fabric entry is Fabric's");
		assertEquals("neoforge list", read(both, "[{\"type\": \"x:y\"}, {\"model\": \"b\"}]"));
	}

	@Test void aVariantNamingBothKeysFallsBackToNeoForgeOnlyWhenFabricCannotReadIt() {
		Codec<String> both = KernelBlockStateModelFormats.either(NEOFORGE, FABRIC);
		assertEquals("fabric fabric:registered", read(both, "{\"fabric:type\": \"fabric:registered\", \"type\": \"x:y\"}"));
		assertEquals("neoforge x:y", read(both, "{\"fabric:type\": \"fabric:absent\", \"type\": \"x:y\"}"));
		DataResult<String> fabricOnly = both.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"fabric:type\": \"fabric:absent\"}"));
		assertEquals("fabric: unknown type fabric:absent", fabricOnly.error().orElseThrow().message(),
				"without a NeoForge key, Fabric's error is the one reported, as on Fabric");
	}

	@Test void encodingAsksNeoForgeFirstAndFabricForWhatNeoForgeRejects() {
		Codec<String> both = KernelBlockStateModelFormats.either(NEOFORGE, FABRIC);
		assertEquals(new JsonPrimitive("neoforge wrote plain variant"), both.encodeStart(JsonOps.INSTANCE, "plain variant").getOrThrow(),
				"what both can write, NeoForge writes");
		assertEquals(new JsonPrimitive("neoforge wrote neoforge model"), both.encodeStart(JsonOps.INSTANCE, "neoforge model").getOrThrow());
		assertEquals(new JsonPrimitive("fabric wrote fabric model"), both.encodeStart(JsonOps.INSTANCE, "fabric model").getOrThrow(),
				"what NeoForge rejects, Fabric writes");
	}

	@Test void offOrNothingToCombineReturnsTheCodecFabricInstalled() {
		assertSame(FABRIC, KernelBlockStateModelFormats.either(null, FABRIC));
		assertSame(NEOFORGE, KernelBlockStateModelFormats.either(NEOFORGE, null));
		String old = System.setProperty(FabricBlockStateCodecMixinAdapter.PROPERTY, "off");
		try {
			assertSame(FABRIC, KernelBlockStateModelFormats.either(NEOFORGE, FABRIC));
		} finally {
			if (old == null) System.clearProperty(FabricBlockStateCodecMixinAdapter.PROPERTY);
			else System.setProperty(FabricBlockStateCodecMixinAdapter.PROPERTY, old);
		}
	}
}
