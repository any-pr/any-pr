/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.fabricmc.api.EnvType;

/**
 * {@link PayloadCodecFunnelInjector}'s output, run: when the game builds its custom-payload codec through NeoForge's
 * overload, a mod hooking vanilla's {@code CustomPacketPayload.codec} (Carpet adds {@code carpet:hello} there) is called
 * and its type is in the codec NeoForge builds, with the protocol and flow it was asked for; where as merged vanilla's
 * overload is never called and a dedicated server cannot encode Carpet's hello, so every player is disconnected at
 * login.
 *
 * <p>The hook is the kernel's real {@code KernelPayloadCodecs}, compiled from {@code src/runtime/java} against stand-ins
 * for the network types it names. The mod's hook is applied to the class file the way a Mixin HEAD injection modifying
 * the argument lands: first thing in vanilla's {@code codec}.
 */
@ExecutesInjector(PayloadCodecFunnelInjector.class)
class PayloadCodecFunnelInjectorExecutionTest {
	private static final Path HOOK_SOURCE = Path.of("src/runtime/java/net/forbric/kernel/runtime/KernelPayloadCodecs.java");
	private static final String PAYLOAD = PayloadCodecFunnelInjector.TARGET;

	private static final Map<String, String> STAND_INS = Map.of(
			"net.minecraft.network.ConnectionProtocol", "package net.minecraft.network; public enum ConnectionProtocol { CONFIGURATION, PLAY }",
			"net.minecraft.network.protocol.PacketFlow", "package net.minecraft.network.protocol; public enum PacketFlow { SERVERBOUND, CLIENTBOUND }",
			"net.minecraft.network.FriendlyByteBuf", "package net.minecraft.network; public class FriendlyByteBuf { }",
			"net.minecraft.network.codec.StreamCodec", "package net.minecraft.network.codec; public interface StreamCodec<B, V> { }",
			PAYLOAD, """
					package net.minecraft.network.protocol.common.custom;

					import java.util.ArrayList;
					import java.util.List;
					import net.minecraft.network.ConnectionProtocol;
					import net.minecraft.network.FriendlyByteBuf;
					import net.minecraft.network.codec.StreamCodec;
					import net.minecraft.network.protocol.PacketFlow;

					public interface CustomPacketPayload {
						interface FallbackProvider<B> {
						}

						record TypeAndCodec<B, T>(String id) {
						}

						/** The codec as built: by whom, for which types, protocol and flow. */
						record Built<B>(String by, List<String> types, Object protocol, Object flow) implements StreamCodec<B, CustomPacketPayload> {
						}

						static <B extends FriendlyByteBuf> StreamCodec<B, CustomPacketPayload> codec(FallbackProvider<B> fallback,
								List<TypeAndCodec<? super B, ?>> types) {
							return new Built<>("vanilla", ids(types), null, null);
						}

						static <B extends FriendlyByteBuf> StreamCodec<B, CustomPacketPayload> codec(FallbackProvider<B> fallback,
								List<TypeAndCodec<? super B, ?>> types, ConnectionProtocol protocol, PacketFlow flow) {
							return new Built<>("neoforge", ids(types), protocol, flow);
						}

						private static List<String> ids(List<? extends TypeAndCodec<?, ?>> types) {
							List<String> ids = new ArrayList<>();
							for (TypeAndCodec<?, ?> type : types) ids.add(type.id());
							return ids;
						}
					}
					""",
			"fixture.Carpet", """
					package fixture;

					import java.util.ArrayList;
					import java.util.List;
					import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

					public final class Carpet {
						/** What Carpet's hook on vanilla's codec does: its own payload type is added to the list. */
						@SuppressWarnings({"unchecked", "rawtypes"})
						public static List addHello(List types) {
							List<Object> with = new ArrayList<>(types);
							with.add(new CustomPacketPayload.TypeAndCodec<>("carpet:hello"));
							return with;
						}
					}
					""");

	/** A HEAD hook on vanilla's codec that replaces the type list, as Mixin lays one down after the kernel's chain. */
	private static byte[] withCarpetHook(byte[] payload) {
		ClassNode node = new ClassNode();
		new ClassReader(payload).accept(node, 0);
		for (MethodNode method : node.methods) {
			if (!method.name.equals("codec") || !method.desc.equals(PayloadCodecFunnelInjector.VANILLA_DESC)) continue;
			InsnList hook = new InsnList();
			hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
			hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "fixture/Carpet", "addHello", "(Ljava/util/List;)Ljava/util/List;", false));
			hook.add(new VarInsnNode(Opcodes.ASTORE, 1));
			method.instructions.insert(hook);
		}
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		return writer.toByteArray();
	}

	private static Object build(ClassLoader loader, boolean neoForge) throws Throwable {
		Class<?> payload = loader.loadClass(PAYLOAD);
		Object fallback = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[] {loader.loadClass(PAYLOAD + "$FallbackProvider")},
				(proxy, method, args) -> null);
		List<Object> types = List.of(InjectorExecution.construct(loader.loadClass(PAYLOAD + "$TypeAndCodec"), "minecraft:brand"));
		if (!neoForge) return InjectorExecution.invokeStatic(payload, "codec", fallback, types);
		Object play = Enum.valueOf(loader.loadClass("net.minecraft.network.ConnectionProtocol").asSubclass(Enum.class), "PLAY");
		Object clientbound = Enum.valueOf(loader.loadClass("net.minecraft.network.protocol.PacketFlow").asSubclass(Enum.class), "CLIENTBOUND");
		return InjectorExecution.invokeStatic(payload, "codec", fallback, types, play, clientbound);
	}

	@Test void carpetsTypeIsInTheCodecNeoForgeBuilds(@TempDir Path work) throws Throwable {
		assertTrue(Files.isRegularFile(HOOK_SOURCE), "the game-side hook's source is part of the checkout: " + HOOK_SOURCE.toAbsolutePath());
		Map<String, String> sources = new HashMap<>(STAND_INS);
		sources.put("net/forbric/kernel/runtime/KernelPayloadCodecs.java", Files.readString(HOOK_SOURCE));
		Map<String, byte[]> original = InjectorExecution.compile(work, sources);
		String internal = PAYLOAD.replace('.', '/');
		byte[] funnelled = InjectorExecution.transform(new PayloadCodecFunnelInjector(), PAYLOAD, original.get(internal), EnvType.SERVER);
		assertNotSame(original.get(internal), funnelled);
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(internal, withCarpetHook(funnelled));
		ClassLoader loader = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(classes.get(internal), loader));

		assertEquals("Built[by=neoforge, types=[minecraft:brand, carpet:hello], protocol=PLAY, flow=CLIENTBOUND]",
				String.valueOf(build(loader, true)), "NeoForge's codec is the one built, with Carpet's type in it");
		assertEquals("Built[by=vanilla, types=[minecraft:brand, carpet:hello], protocol=null, flow=null]",
				String.valueOf(build(loader, false)), "outside a build, vanilla's overload is still vanilla's own");

		Map<String, byte[]> merged = new HashMap<>(original);
		merged.put(internal, withCarpetHook(original.get(internal)));
		assertEquals("Built[by=neoforge, types=[minecraft:brand], protocol=PLAY, flow=CLIENTBOUND]",
				String.valueOf(build(InjectorExecution.load(merged), true)),
				"premise: as merged, Carpet's hook is never reached and its hello cannot be encoded");
		assertSame(funnelled, InjectorExecution.transform(new PayloadCodecFunnelInjector(), PAYLOAD, funnelled, EnvType.SERVER),
				"overloads that already funnel are left alone");
	}
}
