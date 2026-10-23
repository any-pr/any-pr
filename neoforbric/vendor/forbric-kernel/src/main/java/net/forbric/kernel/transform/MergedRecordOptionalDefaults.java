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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.forbric.kernel.util.ForbricLog;

/**
 * Gives a merged record's {@code Optional} component {@code Optional.empty()} in the constructors that never set it.
 *
 * <p>A record both Forge families extended keeps both extensions in the merged base, each with the constructor that
 * family wrote — and each of those constructors assigns only its own family's components. {@code ServerStatus} is
 * the one where that bites: vanilla's five-argument constructor delegates to NeoForge's, which sets
 * {@code isModded} but leaves MinecraftForge's {@code Optional<ServerStatusPing> forgeData} null, while the merged
 * {@code CODEC} encodes {@code forgeData} with {@code optionalFieldOf} — which cannot take a null. The game itself
 * builds its status with MinecraftForge's constructor, so nothing notices until a mod rebuilds the status with
 * vanilla's: LPLM does, at {@code buildServerStatus}'s return, and the server tick loop died in
 * {@code resetStatusCache} with "Cannot invoke Optional.isPresent() because input is null".
 *
 * <p>An {@code Optional} is never legitimately null, so the default is not a guess: the constructor that leaves the
 * field unset now sets it to {@code Optional.empty()} straight after {@code Record.<init>}, which is what a status
 * built by a family with no ping data carries. Constructors that delegate ({@code this(...)}) are left alone — the
 * one they reach sets it. {@code MergedRecordOptionalDefaultsCensusTest} proves {@link #TARGETS} names every such
 * record on the staged merged base. {@code -Dforbric.mergedRecordDefaults=off}.
 */
public final class MergedRecordOptionalDefaults implements ClassTransformer {
	public static final String PROPERTY = "forbric.mergedRecordDefaults";
	static final List<String> TARGETS = List.of("net.minecraft.network.protocol.status.ServerStatus");
	private static final String OPTIONAL = "Ljava/util/Optional;";

	public static boolean enabled() {
		return !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "on"));
	}

	@Override
	public String name() {
		return "forbric-merged-record-optional-defaults";
	}

	@Override
	public AnchorSet anchors() {
		if (!enabled()) return AnchorSet.scanned("switched off by -D" + PROPERTY);
		List<AnchorSet.Anchor> anchors = new ArrayList<>();
		for (String target : TARGETS) {
			anchors.add(new AnchorSet.Anchor(target, AnchorSet.Severity.REQUIRED,
					"a record built with the other family's constructor carries a null Optional its codec cannot encode "
							+ "(a mod rebuilding the server status stops the server tick loop)"));
		}
		return AnchorSet.of(anchors.toArray(new AnchorSet.Anchor[0]));
	}

	@Override
	public byte[] transform(String className, byte[] bytes, TransformContext context) {
		if (!enabled() || bytes == null || bytes.length == 0 || !TARGETS.contains(className)) return bytes;
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		List<String> repaired = repair(node);
		if (repaired.isEmpty()) return bytes;
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		ForbricLog.info("[Forbric/MergedRecord] %s: %s now Optional.empty() where the constructor never set it, instead "
				+ "of a null its codec cannot encode", className.substring(className.lastIndexOf('.') + 1), repaired);
		return writer.toByteArray();
	}

	/** The {@code Optional} components each record constructor leaves unset, as {@code "field in <desc>"}. */
	static List<String> unsetOptionals(ClassNode record) {
		List<String> out = new ArrayList<>();
		if (!"java/lang/Record".equals(record.superName)) return out;
		for (MethodNode ctor : record.methods) {
			if (!ctor.name.equals("<init>") || superCall(ctor, record) == null) continue;
			for (String field : missing(record, ctor)) out.add(field + " in " + ctor.desc);
		}
		return out;
	}

	/** Sets each unset {@code Optional} component to {@code Optional.empty()}; the names it set, empty if none. */
	static List<String> repair(ClassNode record) {
		List<String> repaired = new ArrayList<>();
		if (!"java/lang/Record".equals(record.superName)) return repaired;
		for (MethodNode ctor : record.methods) {
			if (!ctor.name.equals("<init>")) continue;
			MethodInsnNode sup = superCall(ctor, record);
			if (sup == null) continue;
			InsnList defaults = new InsnList();
			for (String field : missing(record, ctor)) {
				defaults.add(new VarInsnNode(Opcodes.ALOAD, 0));
				defaults.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Optional", "empty", "()Ljava/util/Optional;", false));
				defaults.add(new FieldInsnNode(Opcodes.PUTFIELD, record.name, field, OPTIONAL));
				if (!repaired.contains(field)) repaired.add(field);
			}
			if (defaults.size() > 0) ctor.instructions.insert(sup, defaults);
		}
		return repaired;
	}

	/** The {@code Record.<init>} call of a constructor that does not delegate to another of its own. */
	private static MethodInsnNode superCall(MethodNode ctor, ClassNode record) {
		for (AbstractInsnNode insn : ctor.instructions) {
			if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL && call.name.equals("<init>")) {
				return call.owner.equals("java/lang/Record") ? call : null;
			}
		}
		return null;
	}

	private static List<String> missing(ClassNode record, MethodNode ctor) {
		Set<String> assigned = new HashSet<>();
		for (AbstractInsnNode insn : ctor.instructions) {
			if (insn instanceof FieldInsnNode put && put.getOpcode() == Opcodes.PUTFIELD && put.owner.equals(record.name)) {
				assigned.add(put.name);
			}
		}
		List<String> out = new ArrayList<>();
		for (FieldNode field : record.fields) {
			if ((field.access & Opcodes.ACC_STATIC) == 0 && field.desc.equals(OPTIONAL) && !assigned.contains(field.name)) {
				out.add(field.name);
			}
		}
		return out;
	}
}
