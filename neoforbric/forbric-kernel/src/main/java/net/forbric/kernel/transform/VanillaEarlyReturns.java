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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableAnnotationNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.forbric.kernel.util.ForbricLog;

/**
 * Gives a merged method back the early {@code return}s vanilla compiled, so that a mixin's {@code @At("TAIL")} runs on
 * the paths it runs on in vanilla, and on no others.
 *
 * <p>Mixin's TAIL is a method's LAST return instruction. Both carriers' bases come out of a decompile-recompile
 * pipeline, and the decompiler turns a guard clause inside out: vanilla's
 * {@code if (player == null || this.level == null) return; ...body...} comes back as
 * {@code if (player != null && this.level != null) { ...body... }}. The early return is gone, and the paths that took it
 * now jump to the one return at the end — the one TAIL names. On vanilla, and so on Fabric, a TAIL handler runs only
 * after the body ran; on the merged game it also runs when the guard bailed out. Issue #31: TaCZ's
 * {@code Camera.update} TAIL handler asks {@code getCameraEntityPartialTicks}, which reads {@code this.level}, and
 * every frame of the title screen died on it — the body it was written to follow had returned early because there is
 * no level.
 *
 * <p>Measured against 26.2: 3633 methods whose last return is reached by more control-flow edges in the merged base
 * than in vanilla — 1319 of them {@code void}. A sweep of 1100 local mod jars found 70 Fabric TAIL injectors on them.
 * No ecosystem names any of these methods differently, so nothing in {@code merge-conflicts.txt} ever showed them.
 *
 * <p><b>How the paths are told apart.</b> An edge into a return is keyed by how it got there: the condition under which
 * it is taken (a conditional jump's opcode, the inverse of the one it falls past, or "unconditional") and the
 * normalised instructions of the basic block it leaves (operands by name, constants by value, local slots past the
 * parameters erased — the recompiler renumbers those). The decompiler inverts conditions and swaps fall-through for
 * jumps, but it does not change what a block computes, so the same key finds the same path in both bodies. The census
 * ({@code VanillaEarlyReturnsCensusTest}) keys vanilla's edges and writes, per affected method, the vanilla return
 * each key went to; {@code vanilla-early-returns.txt} ships that. At run time this class keys the merged body the same
 * way and gives an edge its own return only when its key occurs as many times in both bodies and vanilla sent that
 * occurrence to an early return. Several occurrences of one key that vanilla sent to different returns — a block that
 * is only {@code iconst_0} — are told apart by what leads into them, not by the order the recompiler happened to emit
 * them in. Anything it cannot pair keeps going to the tail: a missed split leaves today's behaviour, a wrong one would
 * hide a path from a handler, so ambiguity always loses.
 *
 * <p><b>What changes.</b> Never what the method computes: a retargeted edge reaches a return of the same opcode with
 * the same stack, one block earlier. Each vanilla early return that was folded becomes one block
 * ({@code label; full frame; return}) placed immediately before the tail, in vanilla's return order, so the tail
 * stays last — TAIL — and, where every early return was folded, {@code @At("RETURN", ordinal = n)} counts the way
 * vanilla's does. The blocks carry a full copy of the tail's frame (every edge into them was an edge into the tail
 * before), the tail's own frame is rewritten as full so the compressed frame after it still decodes, and try-catch
 * and local-variable ranges that ended at the tail are cut to end before the blocks, so the new code is covered by
 * exactly the ranges the tail is.
 *
 * <p><b>Who sees it.</b> TAIL now means what vanilla means, which is what a Fabric mod was compiled against. A
 * NeoForge or MinecraftForge mod was compiled against the folded body, where TAIL ran on every path;
 * {@code MixinNativeTail} rewrites those mods' TAIL to the returns that were the tail before, from
 * {@link #splitOf}, so their handlers keep running exactly where they always ran.
 *
 * <p>{@code -Dforbric.vanillaEarlyReturns=off} leaves every body as merged (and the TAIL rewrite stands down with it).
 */
public final class VanillaEarlyReturns implements ClassTransformer {
	public static final String PROPERTY = "forbric.vanillaEarlyReturns";
	static final String TABLE = "/net/forbric/kernel/transform/vanilla-early-returns.txt";
	/** How many normalised instructions of the leaving block go into a key: 8 moves at least one edge in 3245 of 3633. */
	static final int CONTEXT = 8;
	/**
	 * The fewest normalised instructions of each block leading INTO an edge's block that tell two occurrences of one
	 * key apart; the census takes more, up to {@link #CONTEXT}, only where vanilla needs more to tell its own apart.
	 * Short on purpose: the recompiler moves block boundaries (a pattern binding gains a {@code goto}), and the last few
	 * instructions before a branch are what survives that.
	 */
	static final int LEAD = 3;
	/** The label of an edge vanilla sends to its last return. */
	static final String TAIL = "T";
	/** Joins a label to its occurrence's lead in the table: {@code 1@3:0a1b2c3d}, the lead's length, then its hash. */
	static final char LEAD_MARK = '@';

	private static final String CAMERA = "net/minecraft/client/Camera";

	/** What a split left behind, for {@code MixinNativeTail}: returns before the new blocks, and how many blocks. */
	public record Split(int inlineReturns, int blocks) {
	}

	private static final Map<String, Split> SPLITS = new ConcurrentHashMap<>();
	private static volatile Map<String, Map<String, Map<String, List<String>>>> table;

	public static boolean enabled() {
		return !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "on"));
	}

	/** The split this boot made in {@code owner.name+desc} (internal owner), or null when it made none. */
	public static Split splitOf(String owner, String name, String desc) {
		return SPLITS.get(owner + "#" + name + desc);
	}

	/** Every method this boot split, by owner, as {@code name+desc}. */
	public static Set<String> splitMethods(String owner) {
		Set<String> out = new HashSet<>();
		String prefix = owner + "#";
		for (String key : SPLITS.keySet()) if (key.startsWith(prefix)) out.add(key.substring(prefix.length()));
		return out;
	}

	@Override
	public String name() {
		return "forbric:vanilla-early-returns";
	}

	@Override
	public AnchorSet anchors() {
		if (!enabled()) return AnchorSet.scanned("switched off by -D" + PROPERTY);
		return AnchorSet.of(new AnchorSet.Anchor(CAMERA.replace('/', '.'), AnchorSet.Severity.REQUIRED,
				"Camera.update's guard is folded into its tail — a Fabric TAIL handler there runs before any level "
						+ "exists and the title screen crashes (issue #31); and every other method in the shipped table "
						+ "keeps the same fold"));
	}

	@Override
	public byte[] transform(String className, byte[] classBytes, TransformContext context) {
		if (!enabled() || classBytes == null || classBytes.length == 0) return classBytes;
		String internal = className.replace('.', '/');
		Map<String, Map<String, List<String>>> rows = table().get(internal);
		if (rows == null) return classBytes;
		try {
			ClassNode node = new ClassNode();
			new ClassReader(classBytes).accept(node, 0);
			int methods = restore(node, rows);
			if (methods == 0) return classBytes;
			ClassWriter writer = new ClassWriter(0);
			node.accept(writer);
			ForbricLog.debug("[Forbric/EarlyReturns] %s: %d method(s) return early again where vanilla does", className, methods);
			return writer.toByteArray();
		} catch (RuntimeException e) {
			ForbricLog.warn("[Forbric/EarlyReturns] could not restore " + className + "'s early returns; left as merged", e);
			return classBytes;
		}
	}

	/** Splits every method of {@code node} that {@code rows} names; returns how many it changed. */
	static int restore(ClassNode node, Map<String, Map<String, List<String>>> rows) {
		int changed = 0;
		for (MethodNode method : node.methods) {
			Map<String, List<String>> vanilla = rows.get(method.name + method.desc);
			if (vanilla == null || method.instructions.size() == 0) continue;
			AbstractInsnNode tail = lastReturn(method);
			if (tail == null) continue;
			List<Edge> edges = edges(method);
			Map<Edge, Integer> decisions = decide(method, edges, tail, vanilla);
			if (decisions.isEmpty()) continue;
			int inline = returns(method).size() - 1;
			int blocks = split(node.name, method, tail, edges, decisions);
			if (blocks == 0) continue;
			SPLITS.put(node.name + "#" + method.name + method.desc, new Split(inline, blocks));
			changed++;
		}
		return changed;
	}

	// --- the shipped table ---

	static Map<String, Map<String, Map<String, List<String>>>> table() {
		Map<String, Map<String, Map<String, List<String>>>> loaded = table;
		if (loaded != null) return loaded;
		synchronized (VanillaEarlyReturns.class) {
			if (table == null) table = read();
			return table;
		}
	}

	private static Map<String, Map<String, Map<String, List<String>>>> read() {
		Map<String, Map<String, Map<String, List<String>>>> out = new HashMap<>();
		try (InputStream in = VanillaEarlyReturns.class.getResourceAsStream(TABLE)) {
			if (in == null) {
				ForbricLog.warn("[Forbric/EarlyReturns] %s is missing — no merged method gets vanilla's early returns back", TABLE);
				return out;
			}
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
				Row row = Row.parse(line);
				if (row != null) out.computeIfAbsent(row.owner(), o -> new HashMap<>()).put(row.method(), row.keys());
			}
		} catch (IOException unreadable) {
			ForbricLog.warn("[Forbric/EarlyReturns] could not read " + TABLE, unreadable);
		}
		int methods = out.values().stream().mapToInt(Map::size).sum();
		ForbricLog.info("[Forbric/EarlyReturns] %d merged methods in %d classes get vanilla's early returns back as they load, "
				+ "so a mixin's TAIL runs where it runs on vanilla (-D%s=off keeps the merged shape)", methods, out.size(), PROPERTY);
		return out;
	}

	/** One line: {@code owner#name+desc key=label,label ...}, labels being vanilla return indices or {@value #TAIL}. */
	record Row(String owner, String method, Map<String, List<String>> keys) {
		static Row parse(String line) {
			line = line.strip();
			if (line.isEmpty() || line.startsWith("#")) return null;
			String[] parts = line.split(" ");
			int hash = parts[0].indexOf('#');
			if (hash <= 0 || parts.length < 2) return null;
			Map<String, List<String>> keys = new LinkedHashMap<>();
			for (int i = 1; i < parts.length; i++) {
				int eq = parts[i].indexOf('=');
				if (eq <= 0) return null;
				keys.put(parts[i].substring(0, eq), List.of(parts[i].substring(eq + 1).split(",")));
			}
			return new Row(parts[0].substring(0, hash), parts[0].substring(hash + 1), keys);
		}

		String format() {
			StringJoiner line = new StringJoiner(" ");
			line.add(owner + "#" + method);
			keys.forEach((key, labels) -> line.add(key + "=" + String.join(",", labels)));
			return line.toString();
		}
	}

	// --- edges into returns, and their keys ---

	enum Kind { JUMP, GOTO, FALL, SWITCH }

	/**
	 * One way control reaches a return. {@code source} is the jump or switch that takes it, or the return itself for
	 * a fall-through; {@code entry} is the switch entry ({@code -1} for its default).
	 */
	record Edge(Kind kind, AbstractInsnNode source, int entry, String key, AbstractInsnNode target) {
		@Override
		public boolean equals(Object other) {
			return this == other;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(this);
		}
	}

	/** Every edge into every return of {@code method}, in instruction order. */
	static List<Edge> edges(MethodNode method) {
		Set<LabelNode> entries = entries(method);
		int fixed = fixedSlots(method);
		List<Edge> out = new ArrayList<>();
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof JumpInsnNode jump && jump.getOpcode() != Opcodes.JSR) {
				AbstractInsnNode target = resolve(jump.label);
				if (!isReturn(target)) continue;
				if (jump.getOpcode() != Opcodes.GOTO) {
					out.add(new Edge(Kind.JUMP, jump, 0, hash(jump.getOpcode() + "|" + context(jump, entries, fixed)), target));
				} else {
					// A GOTO only other jumps reach is a link in their chain; they are counted at their sources.
					AbstractInsnNode before = previousReal(jump);
					if (before != null && !endsFlow(before)) {
						out.add(new Edge(Kind.GOTO, jump, 0, hash("U|" + context(jump, entries, fixed)), target));
