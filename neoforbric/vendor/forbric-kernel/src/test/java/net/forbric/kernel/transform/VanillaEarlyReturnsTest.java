/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * {@link VanillaEarlyReturns} on bodies javac really compiles both ways: {@link Vanilla} written with guard clauses,
 * {@link Folded} the way the decompiler writes them back. A probe call is put before each method's last return —
 * what an {@code @Inject(at = @At("TAIL"))} handler compiles to — and another before every return with its ordinal,
 * what {@code @At(value = "RETURN", ordinal = n)} names. The classes are DEFINED and RUN, so the JVM's own verifier
 * passes on every frame the split writes, and each call is asserted to return the same value, through the same return,
 * with the TAIL probe firing exactly when it fires in vanilla. The unrepaired folded class is run too, and must differ:
 * that is the bug, measured.
 */
class VanillaEarlyReturnsTest {
	/** What a TAIL handler sees: one line per call that reached the method's last return. */
	public static final class Probe {
		public static final List<String> HITS = new ArrayList<>();

		public static void tail() {
			HITS.add("TAIL");
		}

		/** Which return the call left by, counted the way Mixin counts {@code RETURN} ordinals. */
		public static void left(int ordinal) {
			HITS.add("RETURN " + ordinal);
		}
	}

	/** A value a method returns from several returns, the way {@code getArmPose} returns {@code ArmPose}. */
	public enum Pose { EMPTY, ITEM, SPEAR }

	@SuppressWarnings("unused")
	public static final class Vanilla {
		public Vanilla() {
		}

		public Vanilla(Object a, List<String> log) {
			if (a == null) return;
			log.add("ctor");
		}

		public static void guard(Object a, Object b, List<String> log) {
			if (a == null || b == null) return;
			log.add("body");
		}

		public static int ternary(int x) {
			if (x < 0) return -1;
			return x * 2 + 1;
		}

		public static void earlyBlock(boolean a, List<String> log) {
			if (a) {
				log.add("early");
				return;
			}
			log.add("main");
		}

		public static void earlyFallsThrough(boolean a, List<String> log) {
			if (!a) {
				log.add("early");
				return;
			}
			log.add("main");
		}

		public static void wide(long t, double d, Object a, List<String> log) {
			double scaled = d * t;
			if (a == null || scaled < 0) return;
			long sum = t + (long) scaled;
			log.add("sum " + sum);
		}

		public static void inTry(Object a, List<String> log) {
			try {
				if (a == null) return;
				log.add("try " + a.hashCode() / (a.equals("zero") ? 0 : 1));
			} catch (ArithmeticException e) {
				log.add("catch");
			}
		}

		public static void twoEarly(int k, List<String> log) {
			if (k == 1) return;
			log.add("past one");
			if (k == 2) return;
			log.add("past two");
		}

		public static void mixed(int k, List<String> log) {
			if (k == 1) return;
			log.add("a");
			if (k == 2) return;
			log.add("b");
		}

		/**
		 * {@code HumanoidMobRenderer.getArmPose}'s shape (issue #54): two guards return the same constant, so both
		 * early edges carry ONE key, labelled {@code 0,1}.
		 */
		public static Pose armPose(boolean stab, boolean swinging, boolean spear) {
			if (stab && swinging) return Pose.SPEAR;
			if (spear) return Pose.SPEAR;
			return Pose.EMPTY;
		}

		/** {@code AvatarRenderer.getArmPose}'s: the same key on three returns, only the last of them folded. */
		public static Pose handPose(int use, boolean stab, boolean spear) {
			if (use == 0) return Pose.EMPTY;
			if (use == 9) return Pose.SPEAR;
			if (stab) return Pose.SPEAR;
			if (spear) return Pose.SPEAR;
			return Pose.ITEM;
		}

		/** One key on two returns, BOTH folded: each occurrence must still leave by its own return. */
		public static Pose eitherPose(boolean a, boolean b) {
			if (a) return Pose.SPEAR;
			if (b) return Pose.SPEAR;
			return Pose.EMPTY;
		}

		/**
		 * {@code Identifier.equals}' shape: two {@code iconst_0} blocks, one key — the {@code &&} chain's false goes
		 * to return 1, "not a String" to the tail.
		 */
		public static boolean same(Object o, String a) {
			if (o == a) return true;
			if (o instanceof String s) {
				return s.length() == a.length() && s.equals(a);
			}
			return false;
		}

		/** {@code CommandSuggestions.hasAllowedInput}'s: two folded guards, the second one the fall-through. */
		public static boolean allowed(boolean message, boolean messages, boolean command, boolean commands) {
			if (message && !messages) return false;
			if (command && !commands) return false;
			return true;
		}

		/**
		 * {@code EntityFlagsPredicate.matches}': two guards whose last three instructions are the same — what tells
		 * them apart is the call four back, so the census needs a longer lead.
		 */
		public static boolean fits(String s) {
			if (s.hashCode() + 1 > 3) return false;
			if (s.length() + 1 > 3) return false;
			return true;
		}
	}

	@SuppressWarnings("unused")
	public static final class Folded {
		public Folded() {
		}

		public Folded(Object a, List<String> log) {
			if (a != null) {
				log.add("ctor");
			}
		}

		public static void guard(Object a, Object b, List<String> log) {
			if (a != null && b != null) {
				log.add("body");
			}
		}

		public static int ternary(int x) {
			return x < 0 ? -1 : x * 2 + 1;
		}

		public static void earlyBlock(boolean a, List<String> log) {
			if (a) {
				log.add("early");
			} else {
				log.add("main");
			}
		}

		public static void earlyFallsThrough(boolean a, List<String> log) {
			if (a) {
				log.add("main");
			} else {
				log.add("early");
			}
		}

		public static void wide(long t, double d, Object a, List<String> log) {
			double scaled = d * t;
			if (a != null && !(scaled < 0)) {
				long sum = t + (long) scaled;
				log.add("sum " + sum);
			}
		}

		public static void inTry(Object a, List<String> log) {
			try {
				if (a != null) {
					log.add("try " + a.hashCode() / (a.equals("zero") ? 0 : 1));
				}
			} catch (ArithmeticException e) {
				log.add("catch");
			}
		}

		public static void twoEarly(int k, List<String> log) {
			if (k != 1) {
				log.add("past one");
				if (k != 2) {
					log.add("past two");
				}
			}
		}

		/** The first guard survived the round trip, the second was folded: one inline return before the block. */
		public static void mixed(int k, List<String> log) {
			if (k == 1) return;
			log.add("a");
			if (k != 2) {
				log.add("b");
			}
		}

		/** The merged base's {@code getArmPose}: the second guard became a ternary into the tail. */
		public static Pose armPose(boolean stab, boolean swinging, boolean spear) {
			if (stab && swinging) return Pose.SPEAR;
			return spear ? Pose.SPEAR : Pose.EMPTY;
		}

		public static Pose handPose(int use, boolean stab, boolean spear) {
			if (use == 0) return Pose.EMPTY;
			if (use == 9) return Pose.SPEAR;
			if (stab) return Pose.SPEAR;
			return spear ? Pose.SPEAR : Pose.ITEM;
		}

		public static Pose eitherPose(boolean a, boolean b) {
			return a ? Pose.SPEAR : (b ? Pose.SPEAR : Pose.EMPTY);
		}

		/** The recompiled {@code Identifier.equals}: "not a String"'s {@code iconst_0} now comes FIRST. */
		public static boolean same(Object o, String a) {
			if (o == a) return true;
			return !(o instanceof String s) ? false : s.length() == a.length() && s.equals(a);
		}

		/** The recompiled {@code hasAllowedInput}: the second guard's {@code false} falls into the tail. */
		public static boolean allowed(boolean message, boolean messages, boolean command, boolean commands) {
			return message && !messages ? false : !command || commands;
		}

		/** Recompiled the other way round: the second guard's {@code false} comes first. */
		public static boolean fits(String s) {
			return s.hashCode() + 1 <= 3 ? s.length() + 1 <= 3 : false;
		}
	}

	private static final String FOLDED = Type(Folded.class);

	@Test
	void theTailProbeFiresExactlyWhereVanillasDoes() throws Exception {
		Class<?> vanilla = define(probed(read(Vanilla.class)), Vanilla.class.getName());
		Class<?> folded = define(probed(read(Folded.class)), Folded.class.getName());
		ClassNode repairedNode = read(Folded.class);
		int split = VanillaEarlyReturns.restore(repairedNode, rows());
		assertEquals(15, split, "every fixture method is split");
		Class<?> repaired = define(probed(repairedNode), Folded.class.getName());

		List<String> differences = new ArrayList<>();
		for (Object[] call : calls()) {
			String expected = run(vanilla, call);
			assertEquals(expected, run(repaired, call), "repaired " + describe(call));
			String before = run(folded, call);
			if (!expected.equals(before)) differences.add(describe(call));
		}
		// The bug, measured on the same calls: the folded body reaches its tail on the early paths.
