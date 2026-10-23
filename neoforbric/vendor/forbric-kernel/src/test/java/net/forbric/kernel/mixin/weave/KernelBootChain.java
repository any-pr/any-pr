package net.forbric.kernel.mixin.weave;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import net.fabricmc.api.EnvType;
import net.forbric.kernel.classloading.ForbricClassLoader;
import net.forbric.kernel.transform.ClassTransformer;
import net.forbric.kernel.transform.TransformChain;
import net.forbric.kernel.transform.TransformContext;
import net.forbric.kernel.transform.TransformPhase;

/**
 * The pre-Mixin transform chain of a weave run, with each transformer registered exactly as KernelBoot registers it:
 * the same phase, sort index, enabling switch and relative order, read from KernelBoot's own bytecode rather than
 * restated here. A scenario therefore cannot weave through a chain the game never runs, and a change to how
 * KernelBoot registers a transformer reaches every scenario that names it.
 *
 * <p>Only a registration the harness can reproduce exactly is accepted: {@code chain.register(PHASE, new X()[, n])},
 * reached unconditionally or behind {@code X.enabled()} alone. Constructor arguments, any other condition (a side, a
 * resource probe, an interop flag) or a second registration site fail the run with the reason, never a guess.
 */
final class KernelBootChain {
	static final String KERNEL_BOOT = "net/forbric/kernel/boot/KernelBoot";
	private static final String CHAIN = "net/forbric/kernel/transform/TransformChain";
	private static final String PHASE = "net/forbric/kernel/transform/TransformPhase";

	private KernelBootChain() {
	}

	/**
	 * How KernelBoot registers one transformer.
	 *
	 * @param gated    registered only while the transformer's own {@code enabled()} answers true
	 * @param position where KernelBoot registers it, which is the tie-break TransformChain uses within a sort index
	 */
	record Registration(String transformer, TransformPhase phase, int sortIndex, boolean gated, int line, long position) {
		String describe() {
			return phase + " " + transformer.substring(transformer.lastIndexOf('.') + 1)
					+ (sortIndex == 0 ? "" : " sort " + sortIndex) + (gated ? " (behind its enabled())" : "")
					+ " — KernelBoot.java:" + line;
		}
	}

	/** Builds the chain from {@code transformers} and installs it on {@code loader}, as KernelBoot does before Mixin starts. */
	static void install(ForbricClassLoader loader, EnvType side, List<String> transformers) throws ReflectiveOperationException, IOException {
		List<Registration> registrations = new ArrayList<>();
		for (String transformer : transformers) registrations.add(registration(transformer));
		registrations.sort(Comparator.comparingLong(Registration::position));

		TransformChain chain = new TransformChain();
		List<String> installed = new ArrayList<>();
		for (Registration r : registrations) {
			Class<?> type = Class.forName(r.transformer());
			if (r.gated() && !enabled(type)) {
				System.out.println("[WeaveHarness] pre-Mixin chain: " + type.getSimpleName()
						+ ".enabled() is false, so KernelBoot would not register it");
				continue;
			}
			Constructor<?> constructor = type.getDeclaredConstructor();
			constructor.setAccessible(true);
			chain.register(r.phase(), (ClassTransformer) constructor.newInstance(), r.sortIndex());
			installed.add(r.describe());
		}
		TransformContext context = new TransformContext(side, false, "named");
		loader.setTransformer((name, bytes) -> chain.applyBeforeMixin(name, bytes, context));
		System.out.println("[WeaveHarness] pre-Mixin chain installed: " + String.join("; ", installed));
	}

	private static boolean enabled(Class<?> type) throws ReflectiveOperationException {
		Method enabled = type.getDeclaredMethod("enabled");
		enabled.setAccessible(true);
		return (Boolean) enabled.invoke(null);
	}

	/** Where and how KernelBoot registers {@code transformer} (a binary class name); throws when it cannot be reproduced. */
	static Registration registration(String transformer) throws IOException {
		String internal = transformer.replace('.', '/');
		ClassNode boot = read(KERNEL_BOOT);
		List<Registration> found = new ArrayList<>();
		for (int m = 0; m < boot.methods.size(); m++) {
			MethodNode method = boot.methods.get(m);
			AbstractInsnNode[] insns = method.instructions.toArray();
			for (int i = 0; i < insns.length; i++) {
				if (insns[i].getOpcode() == Opcodes.NEW && ((TypeInsnNode) insns[i]).desc.equals(internal)) {
					found.add(read(transformer, method, insns, i, ((long) m << 32) | i));
				}
			}
		}
		if (found.isEmpty()) {
			throw new IllegalArgumentException("KernelBoot never constructs " + transformer
					+ ", so the weave harness cannot register it the way a boot does");
		}
		if (found.size() > 1) {
			throw new IllegalArgumentException("KernelBoot constructs " + transformer + " at " + found.size()
					+ " sites; the weave harness cannot tell which registration a scenario means");
		}
		return found.get(0);
	}

	private static Registration read(String transformer, MethodNode method, AbstractInsnNode[] insns, int at, long position) {
		String internal = transformer.replace('.', '/');
		int line = lineOf(insns, at);
		String where = transformer + " (KernelBoot.java:" + line + ")";

		AbstractInsnNode phaseInsn = previous(insns, at);
		if (!(phaseInsn instanceof FieldInsnNode phase) || phase.getOpcode() != Opcodes.GETSTATIC || !phase.owner.equals(PHASE)) {
			throw new IllegalArgumentException(where + " is not registered as chain.register(PHASE, new X(...)); the "
					+ "weave harness reproduces only that shape");
		}
		int ctor = at;
		while (++ctor < insns.length && !(insns[ctor] instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
				&& call.owner.equals(internal) && call.name.equals("<init>"))) {
		}
		if (ctor == insns.length || !((MethodInsnNode) insns[ctor]).desc.equals("()V")) {
			throw new IllegalArgumentException(where + " is constructed with arguments KernelBoot supplies from its own "
					+ "state; the weave harness constructs only no-argument transformers");
		}
		int sortIndex = 0;
		AbstractInsnNode after = next(insns, ctor);
		Integer constant = intConstant(after);
		if (constant != null) {
			sortIndex = constant;
			after = next(insns, indexOf(insns, after));
		}
		if (!(after instanceof MethodInsnNode register) || !register.owner.equals(CHAIN) || !register.name.equals("register")) {
			throw new IllegalArgumentException(where + " is not handed straight to TransformChain.register");
		}

		List<AbstractInsnNode> guards = guards(method.instructions, insns, at);
		boolean gated = false;
		if (guards.size() == 1 && guards.get(0).getOpcode() == Opcodes.IFEQ
				&& previous(insns, indexOf(insns, guards.get(0))) instanceof MethodInsnNode check
				&& check.getOpcode() == Opcodes.INVOKESTATIC && check.owner.equals(internal)
				&& check.name.equals("enabled") && check.desc.equals("()Z")) {
			gated = true;
		} else if (!guards.isEmpty()) {
			List<String> lines = new ArrayList<>();
			for (AbstractInsnNode guard : guards) lines.add("KernelBoot.java:" + lineOf(insns, indexOf(insns, guard)));
			throw new IllegalArgumentException(where + " is registered only under a condition other than its own "
					+ "enabled() (" + String.join(", ", lines) + "); the weave harness cannot reproduce it faithfully");
		}
		return new Registration(transformer, TransformPhase.valueOf(phase.name), sortIndex, gated, line, position);
	}

	/**
	 * The branches that can step over instruction {@code at}: a forward jump or switch from before it to after it (an
	 * {@code if} or an {@code else} it sits in), or a backward jump from after it to before it (a loop).
	 */
	private static List<AbstractInsnNode> guards(InsnList list, AbstractInsnNode[] insns, int at) {
		List<AbstractInsnNode> guards = new ArrayList<>();
		for (int i = 0; i < insns.length; i++) {
			List<LabelNode> targets = new ArrayList<>();
			if (insns[i] instanceof JumpInsnNode jump) targets.add(jump.label);
			else if (insns[i] instanceof TableSwitchInsnNode table) {
				targets.add(table.dflt);
				targets.addAll(table.labels);
			} else if (insns[i] instanceof LookupSwitchInsnNode lookup) {
				targets.add(lookup.dflt);
				targets.addAll(lookup.labels);
			}
			for (LabelNode target : targets) {
				int to = list.indexOf(target);
				if ((i < at && to > at) || (i > at && to < at)) {
					guards.add(insns[i]);
					break;
				}
			}
		}
		return guards;
	}

	private static Integer intConstant(AbstractInsnNode insn) {
		if (insn == null) return null;
		int op = insn.getOpcode();
		if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) return op - Opcodes.ICONST_0;
		if (insn instanceof IntInsnNode push && (op == Opcodes.BIPUSH || op == Opcodes.SIPUSH)) return push.operand;
		if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Integer value) return value;
		return null;
	}

	private static AbstractInsnNode previous(AbstractInsnNode[] insns, int at) {
		for (int i = at - 1; i >= 0; i--) if (insns[i].getOpcode() >= 0) return insns[i];
		return null;
	}

	private static AbstractInsnNode next(AbstractInsnNode[] insns, int at) {
		for (int i = at + 1; i < insns.length; i++) if (insns[i].getOpcode() >= 0) return insns[i];
		return null;
	}

	private static int indexOf(AbstractInsnNode[] insns, AbstractInsnNode insn) {
		for (int i = 0; i < insns.length; i++) if (insns[i] == insn) return i;
		return -1;
	}

	private static int lineOf(AbstractInsnNode[] insns, int at) {
		for (int i = at; i >= 0; i--) if (insns[i] instanceof LineNumberNode line) return line.line;
		return -1;
	}

	private static ClassNode read(String internalName) throws IOException {
		try (InputStream in = KernelBootChain.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
			if (in == null) throw new IOException(internalName + " is not on the weave harness's classpath");
			ClassNode node = new ClassNode();
			new ClassReader(in).accept(node, 0);
			return node;
		}
	}
}
