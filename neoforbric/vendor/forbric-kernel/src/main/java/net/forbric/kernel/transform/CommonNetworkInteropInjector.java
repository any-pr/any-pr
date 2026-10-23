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

package net.forbric.kernel.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.forbric.kernel.util.ForbricLog;
import net.forbric.api.Ecosystem;
import net.forbric.api.ForeignType;

/**
 * Arbitrates the common-networking channel that Fabric and NeoForge <em>both</em> claim on a tri-in-one instance.
 *
 * <p>Both ecosystems implement the cross-loader "Common Networking" spec — version + channel negotiation over the
 * {@code c:version}/{@code c:register} wire channels — each with its OWN payload class registered for the same id.
 * On a normal instance only one ecosystem is present, so only one registration exists; on Forbric both do, and the
 * decode registry hands a {@code net.neoforged.…CommonVersionPayload} to Fabric's addon, whose handler casts it to
 * {@code net.fabricmc.…CommonVersionPayload} → {@code ClassCastException} → the client is kicked "The server sent an
 * invalid packet" right after reaching the world.
 *
 * <p>The translation logic lives in {@link net.forbric.kernel.interop.PayloadInterop} — boot-side, purely
 * reflective, and a kernel class since the interop hooks were brought over from the previous-generation loader:
 * given the addon and the incoming payload it runs the negotiation for BOTH stacks
 * — extracting the version, feeding Fabric's {@code onCommonVersionPacket} and NeoForge's {@code checkCommonVersion}
 * — and reports whether it fully handled the packet. This injector installs the two call sites the old loader
 * reached via mixins, but as kernel-native head injections (the kernel authors no mixins of its own):
 * <ul>
 *   <li>{@code AbstractChanneledNetworkAddon.handle(CustomPacketPayload)} — a guest Fabric class the kernel's
 *       transforming loader also defines. If the interop reports the packet handled, return its verdict before
 *       Fabric's {@code receive} can miscast it. This is the one that stops the client CCE.</li>
 *   <li>{@code ServerConfigurationPacketListenerImpl.finishCurrentTask(ConfigurationTask.Type)} — the two stacks
 *       expose different {@code ConfigurationTask.Type}s for the same handshake, so accept either owner at the
 *       task-completion boundary.</li>
 * </ul>
 *
 * <p>The interop methods take {@code Object} parameters, so the injected {@code INVOKESTATIC} can pass {@code this}
 * and the argument as-is — no need for the boot-side hook to name {@code net.minecraft}/{@code net.fabricmc} types
 * (the same widening-reference trick {@link ClientPackHookInjector} relies on).
 */
public final class CommonNetworkInteropInjector implements ClassTransformer {
	private static final String INTEROP = "net/forbric/kernel/interop/PayloadInterop";

	/**
	 * Every Fabric addon class that DECLARES its own {@code handle(CustomPacketPayload)}.
	 *
	 * <p>It was originally just {@code AbstractChanneledNetworkAddon}, on the reasonable assumption that one
	 * injection into the base class covers every addon. It does not. The play addons inherit {@code handle} and so
	 * were covered; both CONFIGURATION addons override it, and an override is not reached by a prologue spliced
	 * into the superclass — so the whole configuration phase ran with no cross-ecosystem translation at all.
	 *
	 * <p>Nothing caught it because the symptom this shim was written for ("invalid packet" right after reaching the
	 * world) is a PLAY-phase symptom, and until gate-m12 no test ever reached the configuration phase over a
	 * socket: singleplayer negotiates in memory, and {@code RegistrySyncManager.configureClient} returns early for
	 * the singleplayer owner. The configuration-phase cost was a server kicking its own client with "This server
	 * requires Fabric Loader and Fabric API installed on your client!" — because the server's
	 * {@code minecraft:register} reached the client as NeoForge's payload type, the client's Fabric addon did not
	 * recognise it, never replied, and Fabric scored the peer NOT_RECEIVED.
	 */
	private static final Set<String> FABRIC_ADDONS = Set.of(
			"net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon",
			"net.fabricmc.fabric.impl.networking.client.ClientConfigurationNetworkAddon",
			"net.fabricmc.fabric.impl.networking.server.ServerConfigurationNetworkAddon");
	private static final String HANDLE = "handle";
	private static final String HANDLE_DESC = "(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)Z";
	private static final String HANDLE_HOOK = "handleFabricChannelRegistrationAddon";
	private static final String HANDLE_HOOK_DESC = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;";

	private static final String CUSTOM_PAYLOAD = "net/minecraft/network/protocol/common/custom/CustomPacketPayload";

	private static final String CLIENT_CONFIG_LISTENER = "net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl";
	/**
	 * NeoForge's channel police. {@code checkPacket} (client and server overloads) refuses any payload whose
	 * channel NeoForge did not negotiate — which is every channel another ecosystem negotiated, so a Fabric mod's
	 * client could not send on its own channel. Foreign payloads are exempted.
	 */
	private static final String NEO_NETWORK_REGISTRY = ForeignType.NETWORK_REGISTRY.binary(Ecosystem.NEOFORGE);
	private static final String CHECK_PACKET = "checkPacket";
	private static final String ON_REGISTER = "onMinecraftRegister";
	private static final String ON_UNREGISTER = "onMinecraftUnregister";
	private static final String REGISTER_DESC = "(Lnet/minecraft/network/Connection;Ljava/util/Set;)V";
	private static final String IS_FORGE_PACKET = "isForgePayloadPacket";
	private static final String IS_FORGE_PACKET_DESC = "(Ljava/lang/Object;)Z";
	private static final String HANDLE_PAYLOAD = "handleCustomPayload";
	private static final String HANDLE_PAYLOAD_DESC = "(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V";
	private static final String NEO_PACKAGE = "net/neoforged/";
	private static final String NEO_SEND_INITIAL_CHANNELS = "sendInitialListeningChannels";

	/**
	 * NeoForge's client-side initialisation of a connection to a NON-NeoForge server, {@code ClientNetworkRegistry
	 * .initializeOtherConnection}. Three call sites in {@code ClientConfigurationPacketListenerImpl} fire per join
	 * (the brand payload on the Netty thread, the enabled-features packet, and the brand payload again on the render
	 * thread after vanilla's re-dispatch) and only {@code handleConfigurationFinished} checks the listener's own
	 * {@code initializedConnection} flag first; the other two run the whole thing again — every NeoForge mod's
	 * default server config rebuilt ("Overwriting non-null config ..." twice per join), the payload filters
	 * re-injected, the register payload re-sent. The two unguarded sites get the same flag check the third has,
	 * so the flag keeps its NeoForge meaning: once per configuration phase, a reconfiguration starts afresh.
	 *
	 * <p><b>NeoForge 26.2.0.88 fixed this upstream and the splice now does nothing, by design.</b> The
	 * {@code initializedConnection} field is gone; {@code initializeOtherConnection} hops to the connection's event
	 * loop and goes through {@code runConnectionInitialization}, which takes a per-connection lock and returns
	 * early when the {@code CONNECTION_INITIALIZED} channel attribute is already set. That is a strictly better
	 * guard than a per-listener boolean, so {@link #guardOtherConnectionInitialisation} finds no field and installs
	 * nothing. The code stays because the carrier is a pin that moves, and re-deriving this from scratch cost a
	 * session once already; {@code CommonNetworkInteropInjectorTest} asserts that on a carrier without the field
	 * NeoForge's own guard is present, so a carrier that drops BOTH goes red instead of quietly regressing.
	 */
	private static final String INITIALIZE_OTHER = "initializeOtherConnection";
	private static final String INITIALIZED_FLAG = "initializedConnection";
	private static final String IS_OTHER = "isOther";

	private static final String SERVER_CONFIG = "net.minecraft.server.network.ServerConfigurationPacketListenerImpl";
	private static final String FINISH_TASK = "finishCurrentTask";
	private static final String FINISH_TASK_DESC = "(Lnet/minecraft/server/network/ConfigurationTask$Type;)V";
	private static final String CONFIG_TASK_TYPE = "net/minecraft/server/network/ConfigurationTask$Type";
	private static final String FINISH_HOOK = "finishEquivalentCommonTask";
	private static final String FINISH_HOOK_DESC = "(Ljava/lang/Object;Ljava/lang/Object;)Z";

	@Override
	public String name() {
		return "forbric-common-network-interop";
	}

	@Override
	public AnchorSet anchors() {
		// Eight target classes and about as many independent repairs behind one `changed` flag, and at least one
		// of them is deliberately inert on the current carrier. So a matched class that comes back unedited is
		// not yet evidence of anything; these need per-repair claims.
		return AnchorSet.scanned("several independent repairs across eight classes, one of them (the client "
				+ "connection-initialisation guard) intentionally inert since NeoForge 26.2.0.88");
	}

	/** Claim ids, one per branch of {@link #transform}; each is reported beside its {@code changed = true}. */
	static final String CLAIM_FABRIC_ADDON = "forbric-common-network-interop#fabricAddonHandle";
	static final String CLAIM_FINISH_TASK = "forbric-common-network-interop#finishCurrentTask";
	static final String CLAIM_CHECK_PACKET = "forbric-common-network-interop#neoCheckPacket";
	static final String CLAIM_GUARD_INITIALISATION = "forbric-common-network-interop#guardOtherConnectionInitialisation";

	/** One claim per branch of {@link #transform}. */
	@Override
	public List<Claim> claims() {
		List<AnchorSet.Anchor> addons = new ArrayList<>();
		for (String addon : FABRIC_ADDONS) {
			addons.add(required(addon, "fabric-api's channel-registration addon miscasts a NeoForge payload before the cross-ecosystem negotiator sees it"));
		}
		return List.of(
				new Claim(CLAIM_FABRIC_ADDON, new AnchorSet(addons, null)),
				new Claim(CLAIM_FINISH_TASK, AnchorSet.of(required(SERVER_CONFIG,
						"Fabric and NeoForge common-networking configuration tasks are not treated as equivalent — one family's configuration never finishes"))),
				new Claim(CLAIM_CHECK_PACKET, AnchorSet.of(required(NEO_NETWORK_REGISTRY,
						"NeoForge's channel check rejects every payload another ecosystem negotiated — Fabric mods are disconnected for unknown channels"))),
				// HEDGE: the merged listener on the current carrier already initialises once, so the guard finds
				// nothing to do (never applied in any gate log); it is kept for a carrier where it does not.
				new Claim(CLAIM_GUARD_INITIALISATION, AnchorSet.of(new AnchorSet.Anchor(CLIENT_CONFIG_LISTENER, AnchorSet.Severity.HEDGE,
						"a non-NeoForge connection is initialised more than once per configuration"))));
	}

	private static AnchorSet.Anchor required(String binaryName, String cost) {
		return new AnchorSet.Anchor(binaryName, AnchorSet.Severity.REQUIRED, cost);
	}

	@Override
	public byte[] transform(String className, byte[] classBytes, TransformContext context) {
		return transform(className, classBytes, context, ClaimReporter.NONE);
	}

	@Override
	public byte[] transform(String className, byte[] classBytes, TransformContext context, ClaimReporter reporter) {
		if (classBytes == null || classBytes.length == 0) return classBytes;
		boolean fabricAddon = FABRIC_ADDONS.contains(className);
		boolean serverConfig = SERVER_CONFIG.equals(className);
		boolean clientConfig = CLIENT_CONFIG_LISTENER.equals(className);
		boolean neoRegistry = NEO_NETWORK_REGISTRY.equals(className);
		if (!fabricAddon && !serverConfig && !clientConfig && !neoRegistry) return classBytes;

		ClassNode node = new ClassNode();
		// EXPAND_FRAMES so every original frame is an absolute F_NEW node; the explicit frames we author at our own
		// branch targets then slot in consistently, and ClassWriter can serialize the StackMapTable WITHOUT
		// COMPUTE_FRAMES — whose getCommonSuperClass would try to load game classes through the wrong loader.
		new ClassReader(classBytes).accept(node, ClassReader.EXPAND_FRAMES);

		boolean changed = false;
		for (MethodNode m : node.methods) {
			if (fabricAddon && m.name.equals(HANDLE) && m.desc.equals(HANDLE_DESC)) {
				m.instructions.insert(handleAddonPrologue(node.name));
				bumpStack(m, 2);
				changed = true;
				reporter.hit(CLAIM_FABRIC_ADDON);
				ForbricLog.info("[Forbric/Net] arbitrating common-networking channel at %s.%s — Fabric addon defers to "
						+ "the cross-ecosystem negotiator before it can miscast a NeoForge payload", className, HANDLE);
			} else if (serverConfig && m.name.equals(FINISH_TASK) && m.desc.equals(FINISH_TASK_DESC)) {
				m.instructions.insert(finishTaskPrologue(node.name));
				bumpStack(m, 2);
				changed = true;
				reporter.hit(CLAIM_FINISH_TASK);
				ForbricLog.info("[Forbric/Net] treating Fabric/NeoForge common-networking tasks as equivalent at %s.%s",
						className, FINISH_TASK);
			} else if (neoRegistry && m.name.equals(CHECK_PACKET) && (m.access & Opcodes.ACC_STATIC) != 0
					&& org.objectweb.asm.Type.getArgumentTypes(m.desc).length == 2) {
				m.instructions.insert(forgePacketExemptionPrologue(m.desc));
				bumpStack(m, 1);
				changed = true;
				reporter.hit(CLAIM_CHECK_PACKET);
				ForbricLog.info("[Forbric/Net] exempting MinecraftForge payloads from NeoForge's channel check at %s.%s%s",
						className, CHECK_PACKET, m.desc);
			}
			if (clientConfig) {
				int guarded = guardOtherConnectionInitialisation(node, m);
				if (guarded > 0) {
					changed = true;
					reporter.hit(CLAIM_GUARD_INITIALISATION);
					ForbricLog.info("[Forbric/Net] %s.%s now initialises a non-NeoForge connection once per configuration "
							+ "phase — NeoForge re-entered ClientNetworkRegistry.initializeOtherConnection from here and "
							+ "rebuilt every mod's default server config each time", className, m.name);
				}
			}
			if (clientConfig && m.name.equals(HANDLE_PAYLOAD) && m.desc.equals(HANDLE_PAYLOAD_DESC)) {
				if (shareMinecraftRegisterWithSuper(m)) {
					changed = true;
					ForbricLog.info("[Forbric/Net] letting minecraft:register reach BOTH stacks at %s.%s, Fabric first — "
							+ "NeoForge's override answered it alone and returned, and Fabric's server treats the first "
							+ "register as the whole declaration", className, HANDLE_PAYLOAD);
				} else {
					ForbricLog.warn("[Forbric/Net] %s.%s no longer swallows minecraft:register the way this fix "
							+ "expects; leaving it alone", className, HANDLE_PAYLOAD);
				}
			}
		}
		if (!changed) return classBytes;

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		return writer.toByteArray();
	}

	/**
	 * {@code Boolean r = interop.handleFabricChannelRegistrationAddon(this, payload); if (r != null) return
	 * r.booleanValue();} — prepended so the negotiator sees the packet before Fabric's own {@code receive} casts it.
	 * At the fall-through label the stack still holds the (null) Boolean and both params are live, so the frame is
	 * locals=[this, CustomPacketPayload] / stack=[Boolean]; POP it and the original body runs at its entry frame.
	 */
	private static InsnList handleAddonPrologue(String owner) {
		InsnList body = new InsnList();
		LabelNode notHandled = new LabelNode();
		body.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this (the addon)
		body.add(new VarInsnNode(Opcodes.ALOAD, 1)); // the payload
		body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, INTEROP, HANDLE_HOOK, HANDLE_HOOK_DESC, false));
		body.add(new InsnNode(Opcodes.DUP));                       // [Boolean, Boolean]
		body.add(new JumpInsnNode(Opcodes.IFNULL, notHandled));    // null -> fall through to original body
		body.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
		body.add(new InsnNode(Opcodes.IRETURN));
		body.add(notHandled);
		body.add(new FrameNode(Opcodes.F_NEW, 2, new Object[] {owner, CUSTOM_PAYLOAD}, 1,
				new Object[] {"java/lang/Boolean"}));
		body.add(new InsnNode(Opcodes.POP));                       // discard the null Boolean, run the original body
		return body;
	}

	/**
	 * {@code if (interop.isForgePayloadPacket(packet)) return;} at the head of a static
	 * {@code checkPacket(Packet, <listener>)V}. Fall-through frame: the two parameters, empty stack — the entry frame.
	 */
	private static InsnList forgePacketExemptionPrologue(String desc) {
		org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(desc);
		InsnList body = new InsnList();
		LabelNode notForge = new LabelNode();
		body.add(new VarInsnNode(Opcodes.ALOAD, 0)); // the packet
		body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, INTEROP, IS_FORGE_PACKET, IS_FORGE_PACKET_DESC, false));
		body.add(new JumpInsnNode(Opcodes.IFEQ, notForge));
