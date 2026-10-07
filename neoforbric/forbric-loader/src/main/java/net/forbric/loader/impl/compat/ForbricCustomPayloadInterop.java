/*
 * Copyright 2026 The Forbric Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.forbric.loader.impl.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.WeakHashMap;

import net.forbric.loader.impl.util.ForbricLog;

/**
 * Runtime bridge for the Fabric/NeoForge custom-payload codec path.
 *
 * <p>The NeoForge-patched Minecraft base keeps NeoForge's four-argument {@code CustomPacketPayload.codec(...)}
 * plumbing, but Fabric API registers payload codecs in its own side/protocol registries. The vanilla/Neo lookup is
 * id-only, which is unsafe when two loader APIs intentionally use the same vanilla channel id with different Java
 * payload classes ({@code minecraft:register}/{@code minecraft:unregister}). This helper returns a tiny
 * {@code StreamCodec} proxy that chooses the concrete codec by runtime payload type for encode, and by available
 * protocol registry for decode.
 *
 * <p>No Minecraft, Fabric or NeoForge type is referenced directly here. The loader core is parent-loaded, so
 * all game/loader API interaction is reflective against the Knot class loader that owns the live classes.
 */
public final class ForbricCustomPayloadInterop {
	private static final String FABRIC_REGISTRY = "net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl";
	private static final String FABRIC_REGISTRATION_PAYLOAD = "net.fabricmc.fabric.impl.networking.RegistrationPayload";
	private static final String FABRIC_COMMON_VERSION_PAYLOAD = "net.fabricmc.fabric.impl.networking.CommonVersionPayload";
	private static final String FABRIC_COMMON_REGISTER_PAYLOAD = "net.fabricmc.fabric.impl.networking.CommonRegisterPayload";
	private static final String NEO_NETWORK_REGISTRY = "net.neoforged.neoforge.network.registration.NetworkRegistry";
	private static final String NEO_REGISTER_PAYLOAD = "net.neoforged.neoforge.network.payload.MinecraftRegisterPayload";
	private static final String NEO_UNREGISTER_PAYLOAD = "net.neoforged.neoforge.network.payload.MinecraftUnregisterPayload";
	private static final String NEO_COMMON_VERSION_PAYLOAD = "net.neoforged.neoforge.network.payload.CommonVersionPayload";
	private static final String NEO_COMMON_REGISTER_PAYLOAD = "net.neoforged.neoforge.network.payload.CommonRegisterPayload";
	private static final String NEO_PAYLOAD_REGISTRATION = "net.neoforged.neoforge.network.registration.PayloadRegistration";
	private static final String CLIENTBOUND_CUSTOM_PAYLOAD_PACKET = "net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket";
	private static final String SERVERBOUND_CUSTOM_PAYLOAD_PACKET = "net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket";
	private static final String FORBRIC_MIRROR_VERSION = "forbric-bridge";
	private static final Map<ClassLoader, Boolean> MIRRORED_LOADERS = Collections.synchronizedMap(new WeakHashMap<>());

	private ForbricCustomPayloadInterop() {
	}

	public static void bootstrapMirrors(ClassLoader cl) {
		ClassLoader loader = cl != null ? cl : loaderFor();
		synchronized (MIRRORED_LOADERS) {
			if (MIRRORED_LOADERS.putIfAbsent(loader, Boolean.TRUE) != null) return;
		}

		try {
			mirrorPayloadRegistries(loader);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric] could not bootstrap custom-payload mirrors", unwrap(t));
		}
	}

	/**
	 * Called from bytecode patched into {@code CustomPacketPayload$1$forbricneo.findCodec(...)}.
	 *
	 * @return an object implementing the live {@code net.minecraft.network.codec.StreamCodec} interface.
	 */
	public static Object findCodec(Map<?, ?> localCodecs, Object id, Object protocol, Object packetFlow, Object fallback) {
		bootstrapMirrors(loaderFor(id, protocol, packetFlow));
		Object local = localCodecs != null ? localCodecs.get(id) : null;
		Object fabricEntry = fabricTypeAndCodec(id, protocol, packetFlow);
		Object fabric = typeAndCodecCodec(fabricEntry);
		Object neo = neoCodec(id, protocol, packetFlow);
		Object fallbackCodec = fallbackCodec(fallback, id);

		Object direct = uniqueCodec(local, fabric, neo, fallbackCodec);
		probe(() -> "codec for " + id + " " + protocol + "/" + packetFlow + ": local=" + (local != null)
				+ " fabric=" + (fabric != null) + " neo=" + (neo != null)
				+ " fallback=" + (fallbackCodec != null)
				+ (direct != null ? " -> direct " + direct.getClass().getSimpleName() : " -> proxy"));
		if (direct != null) return direct;

		ClassLoader loader = loaderFor(id, protocol, packetFlow);
		Class<?> streamCodec = load(loader, "net.minecraft.network.codec.StreamCodec");
		if (streamCodec == null) {
			return firstNonNull(local, fabric, neo, fallbackCodec);
		}

		CandidateSet candidates = new CandidateSet(id, local, fabricEntry, fabric, neo, fallbackCodec);
		return Proxy.newProxyInstance(loader, new Class<?>[] { streamCodec }, new CodecInvocationHandler(candidates));
	}

	/**
	 * Called by the Fabric-channel-addon mixin. Returns {@code Boolean.TRUE} only when the target's original
	 * {@code handle(...)} method should be considered complete and skipped.
	 */
	public static Boolean handleFabricChannelRegistrationAddon(Object addon, Object payload) {
		// Every decision this method makes, under -Dforbric.debug. Added because the one question the code could not
		// answer from its own log was the only one that mattered when multiplayer broke: did the client's channel
		// declaration reach the server at all, and if not, which of the five branches below swallowed it. Each
		// branch that returns now says so; "nothing in the log" used to be the answer to all five.
		probe(() -> "addon " + simpleName(addon) + " <- " + payloadId(payload) + " [" + simpleName(payload) + "]");
		bootstrapMirrors(loaderFor(addon, payload));
		Boolean commonNegotiation = handleFabricCommonNegotiationAddon(addon, payload);
		if (commonNegotiation != null) {
			probe(() -> "  common-networking negotiation handled it -> " + commonNegotiation);
			return commonNegotiation;
		}

		Registration registration = registration(payload);
		if (registration == null) {
			probe(() -> "  not a channel registration; leaving it to the addon's own body");
			if (ForbricLog.debugEnabled()) describeFrozenRegistrySnapshot(payload);
			return null;
		}
		probe(() -> "  " + (registration.register ? "register" : "unregister") + " " + registration.channels.size()
				+ " channel(s): " + registration.channels);

		// The order of the two halves below is a wire contract on BOTH ends, and each half pins one edge of it:
		//
		//  1. NeoForge's bookkeeping first. On the server, Fabric's receiveRegistration — the mirror in step 2 —
		//     runs startConfiguration() synchronously, and Fabric's registry-sync task sends fabric:registry/sync
		//     from inside it; NeoForge's checkPacket vetoes any channel the client has not declared, and it learns
		//     the client's channels from exactly this onMinecraftRegister. Mirror first and the send throws
		//     "Payload fabric:registry/sync may not be sent to the client!" and the handshake stalls for good.
		//  2. Fabric's mirror. On the client this is what sends the client's own minecraft:register. Fabric's own
		//     body would make exactly this receiveRegistration call and return true, so a payload that is already
		//     Fabric's is mirrored here too rather than left to the body, which runs only after this method returns.
		Object connection = fieldValue(addon, "connection");
		if (connection != null) {
			syncNeoChannels(connection, registration.register, registration.channels);
		} else {
			probe(() -> "  no connection field on the addon; NeoForge's half was NOT told");
		}

		Object fabricPayload = payload != null && payload.getClass().getName().equals(FABRIC_REGISTRATION_PAYLOAD)
				? payload
				: createFabricRegistrationPayload(payload, registration.register, registration.channels);
		if (fabricPayload == null) {
			probe(() -> "  could NOT synthesize Fabric's payload; Fabric's half was skipped");
		} else {
			boolean mirrored = invokeReceiveRegistration(addon, registration.register, fabricPayload);
			probe(() -> "  mirrored into Fabric receiveRegistration: " + mirrored
					+ "; sendable=" + channelSet(addon, "getSendableChannels")
					+ " receivable=" + channelSet(addon, "getReceivableChannels"));
		}

		return fabricPayload == null ? null : Boolean.TRUE;
	}

	/**
	 * Debug-only: for a NeoForge {@code neoforge:frozen_registry} payload, names the registry it carries, the
	 * registry's runtime class, how many entries its {@code MappedRegistry.byKey} actually holds, and every snapshot
	 * entry that is not a real local key. Written to explain "Failed to sync registries from the server:
	 * NullPointerException: holder is null" out of {@code MappedRegistry.registerIdMapping}, which NeoForge's handler
	 * reports without naming a registry. The first run of it ruled out aliases and missing entries (every remote
	 * name was a real local key); the class and the {@code byKey} count then distinguish a registry whose live map
	 * disagrees with the incoming snapshot. The fix belongs to whatever drives the registry remap on this side.
	 */
	private static void describeFrozenRegistrySnapshot(Object payload) {
		if (payload == null || !"neoforge:frozen_registry".equals(payloadId(payload))) return;
		try {
			Object registryName = invokeNoArg(payload, "registryName");
			Object snapshot = invokeNoArg(payload, "snapshot");
			Object ids = invokeNoArg(snapshot, "getIds");                       // Int2ObjectSortedMap<Identifier>
			Object aliases = invokeNoArg(snapshot, "getAliases");
			Collection<?> names = ids instanceof Map<?, ?> m ? m.values() : List.of();
			ClassLoader loader = loaderFor(payload);
			Class<?> builtIn = load(loader, "net.minecraft.core.registries.BuiltInRegistries");
			Object root = builtIn == null ? null : staticField(builtIn, "REGISTRY");
			Object registry = root == null ? null : invoke(root, "getValue", registryName);
			if (registry == null) {
				probe(() -> "  frozen registry " + registryName + ": " + names.size() + " id(s); NO local registry by that name");
				return;
			}
			Object keySet = invokeNoArg(registry, "keySet");                     // Set<Identifier> — real keys only
			List<Object> notReal = new ArrayList<>();
			if (keySet instanceof Set<?> keys) {
				for (Object name : names) if (!keys.contains(name)) notReal.add(name);
			}
			int localSize = keySet instanceof Set<?> keys ? keys.size() : -1;
			Object byKey = fieldValue(registry, "byKey");
			int byKeySize = byKey instanceof Map<?, ?> m ? m.size() : -1;
			probe(() -> "  frozen registry " + registryName + " [" + registry.getClass().getName() + "]: " + names.size()
					+ " remote id(s) vs " + localSize + " local key(s), MappedRegistry.byKey holds " + byKeySize
					+ "; remote aliases=" + aliases + "; remote names that are not real local keys: " + notReal);
		} catch (RuntimeException e) {
			probe(() -> "  frozen registry probe failed: " + e);
		}
	}

	/**
	 * {@code System.out}, not {@code ForbricLog}: these probes exist to answer "did this branch run at all", and
	 * routing them through a logger makes a silent log pipeline indistinguishable from code that never executed —
	 * exactly the confusion they were added to end. The message is a supplier so nothing is built when debug is off.
	 */
	private static void probe(java.util.function.Supplier<String> message) {
		if (ForbricLog.debugEnabled()) System.out.println("[Forbric/Net] " + message.get());
	}

	private static String simpleName(Object o) {
		return o == null ? "null" : o.getClass().getSimpleName();
	}

	private static String channelSet(Object addon, String getter) {
		Object channels = invokeNoArg(addon, getter);
		return channels == null ? "?" : String.valueOf(channels);
	}

	/**
	 * Called from a mixin on {@code ServerConfigurationPacketListenerImpl.finishCurrentTask}. Fabric and NeoForge
	 * both implement the same common-networking handshake on {@code c:version}/{@code c:register}, but expose
	 * different {@code ConfigurationTask.Type}s. Treat those task ids as aliases on this base.
	 */
	public static boolean finishEquivalentCommonTask(Object listener, Object requestedType) {
		Object currentTask = fieldValue(listener, "currentTask");
		Object currentType = invokeNoArg(currentTask, "type");
		String current = taskId(currentType);
		String requested = taskId(requestedType);
		if (!equivalentCommonTask(current, requested)) return false;

		try {
			Field currentTaskField = findField(listener.getClass(), "currentTask");
			if (currentTaskField == null) return false;
			currentTaskField.setAccessible(true);
			currentTaskField.set(listener, null);
			Method startNextTask = findMethod(listener.getClass(), "startNextTask");
			if (startNextTask == null) return false;
			startNextTask.setAccessible(true);
			startNextTask.invoke(listener);
			ForbricLog.debug("[Forbric] completed equivalent common-networking task " + requested
					+ " while vanilla current task was " + current);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			ForbricLog.warn("[Forbric] could not complete equivalent common-networking task " + requested
					+ " while current task was " + current, e);
			return false;
		}
	}

	private record MirrorRegistration(Object id, Object type, Object codec, Object protocol, Object flow,
			String source) {
	}

	private static void mirrorPayloadRegistries(ClassLoader loader) {
		if (load(loader, NEO_NETWORK_REGISTRY) == null) {
			probe(() -> "mirror pass: NeoForge's NetworkRegistry is not visible from " + loader + "; nothing to mirror into");
			return;
		}

		int mirrored = 0;
		List<MirrorRegistration> local = reflectPacketLocalRegistrations(loader);
		List<MirrorRegistration> fabric = reflectFabricRegistrations(loader);
		for (MirrorRegistration registration : local) {
			if (mirrorPayloadIntoNeo(loader, registration)) mirrored++;
		}
		for (MirrorRegistration registration : fabric) {
			if (mirrorPayloadIntoNeo(loader, registration)) mirrored++;
		}
		int mirroredCount = mirrored;
		probe(() -> "mirror pass on " + loader + ": " + local.size() + " local + " + fabric.size()
				+ " Fabric registration(s) seen, " + mirroredCount + " mirrored into NeoForge");
		if (mirrored > 0) {
			ForbricLog.info("[Forbric] mirrored " + mirrored
					+ " local/Fabric custom payload registration(s) into NeoForge's decode registry");
		}
	}

	private static List<MirrorRegistration> reflectPacketLocalRegistrations(ClassLoader loader) {
		List<MirrorRegistration> out = new ArrayList<>();
		collectPacketRegistrations(loader, CLIENTBOUND_CUSTOM_PAYLOAD_PACKET, "GAMEPLAY_STREAM_CODEC", out);
		collectPacketRegistrations(loader, CLIENTBOUND_CUSTOM_PAYLOAD_PACKET, "CONFIG_STREAM_CODEC", out);
		collectPacketRegistrations(loader, SERVERBOUND_CUSTOM_PAYLOAD_PACKET, "STREAM_CODEC", out);
		collectPacketRegistrations(loader, SERVERBOUND_CUSTOM_PAYLOAD_PACKET, "CONFIG_STREAM_CODEC", out);
		return out;
	}

	private static void collectPacketRegistrations(ClassLoader loader, String packetClassName, String fieldName,
			List<MirrorRegistration> out) {
		Class<?> packetClass = load(loader, packetClassName);
		if (packetClass == null) return;
