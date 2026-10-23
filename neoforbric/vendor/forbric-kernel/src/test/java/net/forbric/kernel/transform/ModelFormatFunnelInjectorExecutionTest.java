/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import net.fabricmc.api.EnvType;

/**
 * {@link ModelFormatFunnelInjector}'s output, run: NeoForge's model deserializer hands a model in a format it does not
 * own to the kernel before it reads {@code "loader"}, and returns the kernel's answer — so a {@code fabric:type} model
 * is its mod's model and a {@code "loader": "fusion:model"} model reaches the vanilla deserializer, where as merged the
 * first parsed as a plain cuboid (Traveler's Backpack's "Expected BackpackDynamicModel") and the second failed with
 * "Unknown loader". A loader NeoForge registered, and a plain model, take NeoForge's path as before.
 *
 * <p>The stand-in deserializer keeps the shape the splice keys on: the object check, the unwrap stored in the fifth
 * local, and {@code json.has("loader")} straight after. Gson is not on the test classpath, so its four types are
 * stand-ins, and so is the game-side {@code KernelModelFormats}: here it claims {@code fabric:type} and hands an
 * unregistered loader to the vanilla deserializer through the context. Which formats the real one claims, and how, is
 * {@code KernelModelFormatsTest}'s.
 */
@ExecutesInjector(ModelFormatFunnelInjector.class)
@ResourceLock("system-properties")
class ModelFormatFunnelInjectorExecutionTest {
	private static final String DESERIALIZER = ModelFormatFunnelInjector.TARGET.replace('/', '.');

	private static final Map<String, String> STAND_INS = Map.of(
			"com.google.gson.JsonElement", """
					package com.google.gson;

					public abstract class JsonElement {
						public boolean isJsonObject() {
							return this instanceof JsonObject;
						}

						public JsonObject getAsJsonObject() {
							return (JsonObject) this;
						}

						public String getAsString() {
							throw new IllegalStateException("not a primitive");
						}
					}
					""",
			"com.google.gson.JsonObject", """
					package com.google.gson;

					import java.util.LinkedHashMap;
					import java.util.Map;

					public class JsonObject extends JsonElement {
						private final Map<String, JsonElement> members = new LinkedHashMap<>();

						public JsonObject with(String key, String value) {
							members.put(key, new JsonPrimitive(value));
							return this;
						}

						public boolean has(String key) {
							return members.containsKey(key);
						}

						public JsonElement get(String key) {
							return members.get(key);
						}
					}
					""",
			"com.google.gson.JsonPrimitive", """
					package com.google.gson;

					public class JsonPrimitive extends JsonElement {
						private final String value;

						public JsonPrimitive(String value) {
							this.value = value;
						}

						@Override
						public String getAsString() {
							return value;
						}
					}
					""",
			"com.google.gson.JsonDeserializationContext", """
					package com.google.gson;

					public interface JsonDeserializationContext {
						<T> T deserialize(JsonElement json, java.lang.reflect.Type typeOfT) throws JsonParseException;
					}
					""",
			"com.google.gson.JsonParseException", "package com.google.gson; public class JsonParseException extends RuntimeException { "
					+ "public JsonParseException(String message) { super(message); } }",
			"net.minecraft.client.resources.model.UnbakedModel", """
					package net.minecraft.client.resources.model;

					public interface UnbakedModel {
						String describe();
					}
					""",
			"net.minecraft.client.resources.model.cuboid.CuboidModel", """
					package net.minecraft.client.resources.model.cuboid;

					import net.minecraft.client.resources.model.UnbakedModel;

					public class CuboidModel implements UnbakedModel {
						public String describe() {
							return "cuboid";
						}
					}
					""",
			"net.forbric.kernel.runtime.KernelModelFormats", """
					package net.forbric.kernel.runtime;

					import com.google.gson.JsonDeserializationContext;
					import com.google.gson.JsonObject;
					import net.minecraft.client.resources.model.UnbakedModel;
					import net.minecraft.client.resources.model.cuboid.CuboidModel;
					import net.neoforged.neoforge.client.model.UnbakedModelParser;

					public final class KernelModelFormats {
						public static UnbakedModel foreign(JsonObject json, JsonDeserializationContext context) {
							if (json.has("fabric:type")) {
								String type = json.get("fabric:type").getAsString();
								return () -> "fabric model " + type;
							}
							if (json.has("loader") && !UnbakedModelParser.LOADERS.containsKey(json.get("loader").getAsString())) {
								return context.deserialize(json, CuboidModel.class);
							}
							return null;
						}
					}
					""",
			"net.neoforged.neoforge.client.model.UnbakedModelParser", """
					package net.neoforged.neoforge.client.model;

					import java.lang.reflect.Type;
					import java.util.Map;
					import com.google.gson.JsonDeserializationContext;
					import com.google.gson.JsonElement;
					import com.google.gson.JsonObject;
					import com.google.gson.JsonParseException;
					import net.minecraft.client.resources.model.UnbakedModel;
					import net.minecraft.client.resources.model.cuboid.CuboidModel;

					public class UnbakedModelParser {
						public static final Map<String, UnbakedModel> LOADERS = Map.of("neoforge:obj", () -> "neoforge obj model");

						/** NeoForge's: its loaders, else the vanilla deserializer. */
						public static class Deserializer {
							public UnbakedModel deserialize(JsonElement element, Type type, JsonDeserializationContext context) throws JsonParseException {
								if (!element.isJsonObject()) throw new JsonParseException("Expected a model object");
								JsonObject json = element.getAsJsonObject();
								if (json.has("loader")) {
									String id = json.get("loader").getAsString();
									UnbakedModel loaded = LOADERS.get(id);
									if (loaded == null) throw new JsonParseException("Unknown loader: " + id);
									return loaded;
								}
								return context.deserialize(json, CuboidModel.class);
							}
						}
					}
					""",
			"fixture.Context", """
					package fixture;

					import java.lang.reflect.Type;
					import com.google.gson.JsonDeserializationContext;
					import com.google.gson.JsonElement;
					import net.minecraft.client.resources.model.cuboid.CuboidModel;

					/** The context CuboidModel.GSON hands down: its cuboid deserializer, where fusion's hook sits. */
					public class Context implements JsonDeserializationContext {
						@SuppressWarnings("unchecked")
						public <T> T deserialize(JsonElement json, Type typeOfT) {
							if (typeOfT != CuboidModel.class) throw new IllegalArgumentException(String.valueOf(typeOfT));
							if (json.getAsJsonObject().has("loader")) {
								String id = json.getAsJsonObject().get("loader").getAsString();
								return (T) (net.minecraft.client.resources.model.UnbakedModel) () -> "cuboid read by the " + id + " hook";
							}
							return (T) new CuboidModel();
						}
					}
					""");

	@AfterEach void reset() {
		System.clearProperty(ModelFormatFunnelInjector.PROPERTY);
	}

	/** What NeoForge's deserializer makes of a model with these keys, or the message it fails with. */
	private static String parse(ClassLoader loader, String... keysAndValues) throws Throwable {
		Object json = InjectorExecution.construct(loader.loadClass("com.google.gson.JsonObject"));
		for (int i = 0; i < keysAndValues.length; i += 2) InjectorExecution.invoke(json, "with", keysAndValues[i], keysAndValues[i + 1]);
		Object deserializer = InjectorExecution.construct(loader.loadClass(DESERIALIZER));
		Object context = InjectorExecution.construct(loader.loadClass("fixture.Context"));
		try {
			return (String) InjectorExecution.invoke(InjectorExecution.invoke(deserializer, "deserialize", json, Object.class, context), "describe");
		} catch (RuntimeException failed) {
			return failed.getMessage();
		}
	}

	@Test void aFormatNeoForgeDoesNotOwnIsParsedByItsOwner(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = InjectorExecution.compile(work, STAND_INS);
		String internal = ModelFormatFunnelInjector.TARGET;
		byte[] funnelled = InjectorExecution.transform(new ModelFormatFunnelInjector(), DESERIALIZER, original.get(internal), EnvType.CLIENT);
		assertNotSame(original.get(internal), funnelled, "the deserializer is the reviewed shape");
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(internal, funnelled);
		ClassLoader loader = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(funnelled, loader));

		assertEquals("fabric model travelersbackpack:backpack", parse(loader, "fabric:type", "travelersbackpack:backpack"),
				"a fabric:type model is its mod's model");
		assertEquals("cuboid read by the fusion:model hook", parse(loader, "loader", "fusion:model"),
				"an unregistered loader reaches the vanilla deserializer, where fusion reads it");
		assertEquals("neoforge obj model", parse(loader, "loader", "neoforge:obj"), "a NeoForge loader is NeoForge's");
		assertEquals("cuboid", parse(loader, "parent", "block/cube"), "a plain model is a cuboid, as before");

		ClassLoader stock = InjectorExecution.load(original);
		assertEquals("cuboid", parse(stock, "fabric:type", "travelersbackpack:backpack"),
				"premise: as merged, a fabric:type model parses as a plain cuboid");
		assertEquals("Unknown loader: fusion:model", parse(stock, "loader", "fusion:model"),
				"premise: as merged, fusion's loader fails before the vanilla deserializer");
		assertSame(funnelled, InjectorExecution.transform(new ModelFormatFunnelInjector(), DESERIALIZER, funnelled, EnvType.CLIENT),
				"a funnelled deserializer is left alone");
	}

	@Test void switchedOffTheDeserializerIsLeftAsShipped(@TempDir Path work) throws Exception {
		byte[] bytes = InjectorExecution.compile(work, STAND_INS).get(ModelFormatFunnelInjector.TARGET);
		System.setProperty(ModelFormatFunnelInjector.PROPERTY, "off");
		assertSame(bytes, InjectorExecution.transform(new ModelFormatFunnelInjector(), DESERIALIZER, bytes, EnvType.CLIENT));
	}
}
