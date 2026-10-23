/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.api.EnvType;

/**
 * {@link UnboundHolderDataInjector} edits the reviewed {@code Holder.Reference} and nothing else: each way a
 * {@code getData} can differ from it is declined whole, the class handed back as it came, and a method that merely
 * starts the way the guard does is not taken for it.
 */
class UnboundHolderDataInjectorShapeTest {
	private static final String KEY = """
			package net.minecraft.resources;

			public final class ResourceKey<T> {
			}
			""";
	private static final String DATA_MAP_TYPE = """
			package net.neoforged.neoforge.registries.datamaps;

			public final class DataMapType<R, T> {
			}
			""";
	private static final String LOOKUP = """
			package net.minecraft.core;

			import net.minecraft.resources.ResourceKey;
			import net.neoforged.neoforge.registries.datamaps.DataMapType;

			public interface HolderLookup {
				interface RegistryLookup<T> extends HolderOwner<T> {
					default <A> A getData(DataMapType<T, A> type, ResourceKey<T> key) {
						return null;
					}
				}
			}
			""";

	/** The merged shape: ask the owner by {@code key()} when it is a registry, else {@code null}. */
	private static final String REVIEWED = """
				if (this.owner instanceof HolderLookup.RegistryLookup<T> lookup) {
					return lookup.getData(type, this.key());
				}
				return null;
			""";

	/** A {@code Holder.Reference} whose fields are {@code fields} and whose {@code getData} body is {@code body}. */
	private static String holder(String fields, String body) {
		return holder(fields, "key", body);
	}

	/** The same, its {@code key()} reading the field {@code keyField}. */
	private static String holder(String fields, String keyField, String body) {
		return """
				package net.minecraft.core;

				import net.minecraft.resources.ResourceKey;
				import net.neoforged.neoforge.registries.datamaps.DataMapType;

				public interface Holder<T> {
					class Reference<T> implements Holder<T> {
						%1$s
						Object fallback;

						public ResourceKey<T> key() {
							if (this.%2$s == null) throw new IllegalStateException("Trying to access unbound value");
							return this.%2$s;
						}

						public <A> A getData(DataMapType<T, A> type) {
							%3$s
						}
					}
				}
				""".formatted(fields, keyField, body);
	}

	private static final String FIELDS = "private HolderOwner<T> owner; private ResourceKey<T> key; private T value;";

	private static Map<String, byte[]> compile(Path work, String holder) throws Exception {
		Map<String, String> sources = new HashMap<>();
		sources.put("net.minecraft.resources.ResourceKey", KEY);
		sources.put("net.neoforged.neoforge.registries.datamaps.DataMapType", DATA_MAP_TYPE);
		sources.put("net.minecraft.core.HolderOwner", "package net.minecraft.core; public interface HolderOwner<T> { }");
		sources.put("net.minecraft.core.HolderLookup", LOOKUP);
		sources.put("net.minecraft.core.Holder", holder);
		return InjectorExecution.compile(work, sources);
	}

	private static byte[] reference(Path work, String fields, String body) throws Exception {
		return compile(work, holder(fields, body)).get(UnboundHolderDataInjector.OWNER);
	}

	private static ClassNode node(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		return node;
	}

	private static MethodNode getData(ClassNode node) {
		return node.methods.stream().filter(m -> m.name.equals("getData")).findFirst().orElseThrow();
	}

	/** Declined: {@code repair} says so, and {@code transform} hands back the very array it was given. */
	private static void assertDeclined(byte[] bytes, String why) {
		assertEquals(-1, UnboundHolderDataInjector.repair(node(bytes)), why);
		assertSame(bytes, InjectorExecution.transform(new UnboundHolderDataInjector(), UnboundHolderDataInjector.TARGET, bytes,
				EnvType.SERVER), why + ": left as merged");
	}

	/** The control every decline below is measured against: the same stand-in, reviewed shape, is edited. */
	@Test void theReviewedShapeIsEdited(@TempDir Path work) throws Exception {
		byte[] bytes = reference(work, FIELDS, REVIEWED);
		ClassNode node = node(bytes);
		assertEquals(1, UnboundHolderDataInjector.repair(node));
		assertTrue(UnboundHolderDataInjector.guarded(getData(node)));
		assertEquals(0, UnboundHolderDataInjector.repair(node), "and is then recognised as edited");
	}

	@Test void twoKeyCallsAreDeclined(@TempDir Path work) throws Exception {
		assertDeclined(reference(work, FIELDS, """
				this.key();
				""" + REVIEWED), "getData calls key() twice");
	}

	@Test void aFirstFrameThatIsNotTheArgumentsIsDeclined(@TempDir Path work) throws Exception {
		// The local makes the `return null` frame an F_APPEND: a jump from the head of the method would not match it.
		assertDeclined(reference(work, FIELDS, """
				String probe = String.valueOf(type);
				""" + REVIEWED), "the null return's frame holds a local");
	}

	@Test void aFallbackOtherThanReturnNullIsDeclined(@TempDir Path work) throws Exception {
		assertDeclined(reference(work, FIELDS, REVIEWED.replace("return null;", "return (A) this.fallback;")),
				"behind the first frame is not `aconst_null; areturn`");
	}

	@Test void missingFieldsAreDeclined(@TempDir Path work) throws Exception {
		assertDeclined(compile(work.resolve("key"), holder("private HolderOwner<T> owner; private ResourceKey<T> resourceKey; private T value;",
				"resourceKey", REVIEWED)).get(UnboundHolderDataInjector.OWNER), "no key field");
		assertDeclined(reference(work.resolve("value"), "private HolderOwner<T> owner; private ResourceKey<T> key; private T object;",
				REVIEWED), "no value field");
		assertDeclined(reference(work.resolve("owner"), "private final HolderOwner<T> holderOwner = null; private ResourceKey<T> key; "
				+ "private T value; private HolderOwner<T> owner() { return holderOwner; }", REVIEWED.replace("this.owner", "this.owner()")),
				"no owner field");
	}

	@Test void noGetDataIsDeclined(@TempDir Path work) throws Exception {
		Map<String, byte[]> classes = compile(work, holder(FIELDS, REVIEWED).replace("getData(DataMapType<T, A> type)", "dataOf(DataMapType<T, A> type)"));
		assertDeclined(classes.get(UnboundHolderDataInjector.OWNER), "no getData(DataMapType)");
	}

	/**
	 * A method that opens with the same {@code key} test as the guard but jumps elsewhere is not "already edited": it is
	 * judged on its shape like any other — here declined, since behind its first frame is a throw.
	 */
	@Test void aLookAlikeHeadIsNotTakenForTheGuard(@TempDir Path work) throws Exception {
		byte[] bytes = reference(work, FIELDS, """
				if (this.key != null && this.owner instanceof HolderLookup.RegistryLookup<T> lookup) {
					return lookup.getData(type, this.key());
				}
				throw new IllegalStateException("unbound");
				""");
		assertFalse(UnboundHolderDataInjector.guarded(getData(node(bytes))), "opens `aload_0; getfield key; ifnull` into a throw");
		assertDeclined(bytes, "a look-alike head");
	}

	/**
	 * One that opens with the key test into its own {@code return null}, the reviewed shape otherwise: it already
	 * answers {@code null} unbound, but says nothing. It is not taken for the guard; the guard is added in front of it.
	 */
	@Test void aKeyTestWithoutTheReportGetsTheGuard(@TempDir Path work) throws Exception {
		byte[] bytes = reference(work, FIELDS, """
				if (this.key != null) {
					if (this.owner instanceof HolderLookup.RegistryLookup<T> lookup) {
						return lookup.getData(type, this.key());
					}
				}
				return null;
				""");
		ClassNode node = node(bytes);
		assertFalse(UnboundHolderDataInjector.guarded(getData(node)));
		assertEquals(1, UnboundHolderDataInjector.repair(node));
		assertTrue(UnboundHolderDataInjector.guarded(getData(node)));
	}

	/** The reviewed stand-in after the edit, and the guard's two jumps: {@code ifnonnull BOUND} and {@code goto NO_DATA}. */
	private record Guarded(ClassNode node, JumpInsnNode bound, JumpInsnNode noData) {
	}

	private static Guarded guardedStandIn(Path work) throws Exception {
		ClassNode node = node(reference(work, FIELDS, REVIEWED));
		assertEquals(1, UnboundHolderDataInjector.repair(node));
		JumpInsnNode bound = null, noData = null;
		for (AbstractInsnNode insn : getData(node).instructions) {
			if (insn.getOpcode() == Opcodes.IFNONNULL && bound == null) bound = (JumpInsnNode) insn;
			if (insn.getOpcode() == Opcodes.GOTO && noData == null) noData = (JumpInsnNode) insn;
		}
		assertNotNull(bound);
		assertNotNull(noData);
		assertTrue(UnboundHolderDataInjector.guarded(getData(node)), "the control: the edit as made is the guard");
		return new Guarded(node, bound, noData);
	}

	/**
	 * All nine instructions of the guard, report call included, but its {@code goto} lands on the bound path instead
	 * of a {@code return null}: an unbound holder would go on to {@code key()} and throw. Not the guard — and not
	 * edited again either, since its first frame no longer stands before a {@code return null}: declined.
	 */
	@Test void aGuardWhoseGotoMissesTheNullReturnIsNotTheGuard(@TempDir Path work) throws Exception {
		Guarded edited = guardedStandIn(work);
		edited.noData().label = edited.bound().label;
		assertFalse(UnboundHolderDataInjector.guarded(getData(edited.node())), "goto lands on the bound path");
		assertEquals(-1, UnboundHolderDataInjector.repair(edited.node()), "declined, not taken as already edited");
	}

	/**
	 * All nine instructions again, the {@code goto} on the {@code return null}, but {@code ifnonnull} lands there too:
	 * every holder, bound or not, would answer "no data" and the data maps would never be read. Not the guard.
	 */
	@Test void aGuardWhoseBoundJumpSkipsTheMethodIsNotTheGuard(@TempDir Path work) throws Exception {
		Guarded edited = guardedStandIn(work);
		edited.bound().label = edited.noData().label;
		assertFalse(UnboundHolderDataInjector.guarded(getData(edited.node())), "ifnonnull lands on the null return, not after the goto");
		assertEquals(-1, UnboundHolderDataInjector.repair(edited.node()), "declined, not taken as already edited");
	}
}
