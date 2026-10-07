/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.api.EnvType;

/**
 * {@link ForeignPayloadReceiveInjector}'s output, run: a payload on a channel only another ecosystem negotiated
 * ({@code carpet:hello}) goes down vanilla's path on both sides, where as merged NeoForge's dispatcher disconnects on
 * it; a channel NeoForge registered, and a NeoForge payload class on an unknown channel, stay NeoForge's.
 *
 * <p>The stand-ins keep the merged listener's shape: {@code handleCustomPayload(packet)} asks
 * {@code NetworkRegistry.isModdedPayload} and hands a modded payload to {@code handleModdedPayload}, which "disconnects"
 * on a channel missing from {@code PAYLOAD_REGISTRATIONS}. The hook is the kernel's real {@code PayloadInterop}, which
 * reads that same field.
 */
@ExecutesInjector(ForeignPayloadReceiveInjector.class)
class ForeignPayloadReceiveInjectorExecutionTest {
	private static final String CLIENT = ForeignPayloadReceiveInjector.TARGETS.get(0);
	private static final String SERVER = ForeignPayloadReceiveInjector.TARGETS.get(1);

	private static String listener(String binaryName, String packet) {
		int dot = binaryName.lastIndexOf('.');
		return """
				package %s;

				import java.util.ArrayList;
				import java.util.List;
				import net.minecraft.network.protocol.common.%s;
				import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
				import net.neoforged.neoforge.network.registration.NetworkRegistry;

				public class %s {
					public final List<String> log = new ArrayList<>();

					public void handleCustomPayload(%s packet) {
						CustomPacketPayload payload = packet.payload();
						if (NetworkRegistry.isModdedPayload(payload)) {
							log.add(NetworkRegistry.handleModdedPayload(payload));
							return;
						}
						handleCustomPayload(payload);
					}

					public void handleCustomPayload(CustomPacketPayload payload) {
						log.add("vanilla " + payload.type().id());
					}
				}
				""".formatted(binaryName.substring(0, dot), packet, binaryName.substring(dot + 1), packet);
	}

	private static String packet(String name) {
		return """
				package net.minecraft.network.protocol.common;

				import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

				public record %s(CustomPacketPayload payload) {
				}
				""".formatted(name);
	}

	private static String payload(String binaryName, String namespace, String path) {
		int dot = binaryName.lastIndexOf('.');
		return """
				package %s;

				import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
				import net.minecraft.resources.Identifier;

				public class %s implements CustomPacketPayload {
					@Override
					public CustomPacketPayload.Type type() {
						return new CustomPacketPayload.Type(new Identifier("%s", "%s"));
					}
				}
				""".formatted(binaryName.substring(0, dot), binaryName.substring(dot + 1), namespace, path);
	}

	private static final Map<String, String> STAND_INS = Map.ofEntries(
			Map.entry("net.minecraft.resources.Identifier", """
					package net.minecraft.resources;

					public record Identifier(String namespace, String path) {
						@Override
						public String toString() {
							return namespace + ":" + path;
						}
					}
					"""),
			Map.entry("net.minecraft.network.protocol.common.custom.CustomPacketPayload", """
					package net.minecraft.network.protocol.common.custom;

					import net.minecraft.resources.Identifier;

					public interface CustomPacketPayload {
						Type type();

						record Type(Identifier id) {
						}
					}
					"""),
			Map.entry("net.neoforged.neoforge.network.registration.NetworkRegistry", """
					package net.neoforged.neoforge.network.registration;

					import java.util.Map;
					import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
					import net.minecraft.resources.Identifier;

					public class NetworkRegistry {
						static final Map<String, Map<Identifier, Object>> PAYLOAD_REGISTRATIONS =
								Map.of("play", Map.of(new Identifier("examplemod", "sync"), "registered by a NeoForge mod"));

						public static boolean isModdedPayload(CustomPacketPayload payload) {
							return !payload.type().id().namespace().equals("minecraft");
						}

						public static String handleModdedPayload(CustomPacketPayload payload) {
							Identifier id = payload.type().id();
							boolean known = PAYLOAD_REGISTRATIONS.values().stream().anyMatch(channels -> channels.containsKey(id));
							return known ? "neoforge " + id : "disconnected: No Channel for " + id;
						}
					}
					"""),
			Map.entry("net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket", packet("ClientboundCustomPayloadPacket")),
			Map.entry("net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket", packet("ServerboundCustomPayloadPacket")),
			Map.entry(CLIENT, listener(CLIENT, "ClientboundCustomPayloadPacket")),
			Map.entry(SERVER, listener(SERVER, "ServerboundCustomPayloadPacket")),
			Map.entry("fixture.CarpetHello", payload("fixture.CarpetHello", "carpet", "hello")),
			Map.entry("fixture.ExampleSync", payload("fixture.ExampleSync", "examplemod", "sync")),
			Map.entry("fixture.Brand", payload("fixture.Brand", "minecraft", "brand")),
			Map.entry("net.neoforged.neoforge.network.payload.Unlisted", payload("net.neoforged.neoforge.network.payload.Unlisted",
					"neoforge", "unlisted")));

	private static List<?> receive(ClassLoader loader, String listener, String packet, String... payloads) throws Throwable {
		Object handler = InjectorExecution.construct(loader.loadClass(listener));
		for (String payload : payloads) {
			Object instance = InjectorExecution.construct(loader.loadClass(payload));
			InjectorExecution.invoke(handler, "handleCustomPayload",
					InjectorExecution.construct(loader.loadClass("net.minecraft.network.protocol.common." + packet), instance));
		}
		return (List<?>) handler.getClass().getField("log").get(handler);
	}

	@Test void aChannelOnlyAnotherEcosystemNegotiatedReachesVanillasPathOnBothSides(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = InjectorExecution.compile(work, STAND_INS);
		Map<String, byte[]> classes = new HashMap<>(original);
		for (String target : List.of(CLIENT, SERVER)) {
			String internal = target.replace('.', '/');
			classes.put(internal, InjectorExecution.transform(new ForeignPayloadReceiveInjector(), target, original.get(internal),
					EnvType.CLIENT));
		}
		ClassLoader loader = InjectorExecution.load(classes);
		for (String target : List.of(CLIENT, SERVER)) {
			assertEquals("", InjectorExecution.verify(classes.get(target.replace('.', '/')), loader), target);
		}

		String[] payloads = {"fixture.CarpetHello", "fixture.ExampleSync", "fixture.Brand", "net.neoforged.neoforge.network.payload.Unlisted"};
		List<String> expected = List.of("vanilla carpet:hello", "neoforge examplemod:sync", "vanilla minecraft:brand",
				"disconnected: No Channel for neoforge:unlisted");
		assertEquals(expected, receive(loader, CLIENT, "ClientboundCustomPayloadPacket", payloads));
		assertEquals(expected, receive(loader, SERVER, "ServerboundCustomPayloadPacket", payloads));

		ClassLoader merged = InjectorExecution.load(original);
		assertEquals("disconnected: No Channel for carpet:hello",
				receive(merged, CLIENT, "ClientboundCustomPayloadPacket", "fixture.CarpetHello").get(0),
				"premise: as merged, Carpet's hello disconnects the client");
		for (String target : List.of(CLIENT, SERVER)) {
			byte[] once = classes.get(target.replace('.', '/'));
			assertSame(once, InjectorExecution.transform(new ForeignPayloadReceiveInjector(), target, once, EnvType.CLIENT), target);
		}
	}
}
