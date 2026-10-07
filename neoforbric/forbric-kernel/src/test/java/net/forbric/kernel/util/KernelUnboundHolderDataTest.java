/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * When the "no data" answer for an unbound holder is reported: never while the owning registry is open, once per value
 * with the asking stack once any of its {@code frozen} flags is set, and never at the cost of a throw.
 */
class KernelUnboundHolderDataTest {
	/** Vanilla's {@code MappedRegistry}: one private flag. */
	static class Mapped {
		private boolean frozen;

		@Override public String toString() {
			return "Registry[test]";
		}
	}

	/** MinecraftForge's {@code NamespacedWrapper}: a second {@code frozen} of its own, read by its own validateWrite. */
	static class Wrapper extends Mapped {
		private boolean frozen;
	}

	/** An owner with nothing to judge it by. */
	static class Lookup {
	}

	/** A static {@code frozen} says nothing about one owner. */
	static class StaticFlag {
		@SuppressWarnings("unused") static boolean frozen = true;
	}

	/** A value that cannot even be printed. */
	static class Unprintable {
		@Override public String toString() {
			throw new IllegalStateException("Trying to access unbound value");
		}
	}

	private static void set(Object owner, Class<?> declaring, boolean value) throws Exception {
		var field = declaring.getDeclaredField("frozen");
		field.setAccessible(true);
		field.setBoolean(owner, value);
	}

	private static String capture(Runnable body) {
		PrintStream out = System.out, err = System.err;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		PrintStream sink = new PrintStream(buffer, true, StandardCharsets.UTF_8);
		System.setOut(sink);
		System.setErr(sink);
		try {
			body.run();
		} finally {
			System.setOut(out);
			System.setErr(err);
		}
		return buffer.toString(StandardCharsets.UTF_8);
	}

	@Test void anOpenRegistryIsTheExpectedCaseAndSaysNothing() {
		Mapped open = new Mapped();
		assertEquals(Boolean.FALSE, KernelUnboundHolderData.closed(open));
		String log = capture(() -> KernelUnboundHolderData.unbound(new Object(), open));
		assertFalse(log.contains("WARN"), log);
	}

	@Test void aClosedRegistryIsReportedOncePerValueWithTheStack() throws Exception {
		Mapped closed = new Mapped();
		set(closed, Mapped.class, true);
		Object orphan = new StringBuilder("orphan_ladder"), other = new StringBuilder("other_ladder");

		String first = capture(() -> KernelUnboundHolderData.unbound(orphan, closed));
		assertTrue(first.contains("[Forbric/WARN]") && first.contains("java.lang.StringBuilder 'orphan_ladder'")
				&& first.contains("Registry[test]") && first.contains("Trying to access unbound value")
				&& first.contains("aClosedRegistryIsReportedOncePerValueWithTheStack"), "the value, its owner and who asked:\n" + first);
		assertFalse(capture(() -> KernelUnboundHolderData.unbound(orphan, closed)).contains("WARN"), "once per value");
		assertTrue(capture(() -> KernelUnboundHolderData.unbound(other, closed)).contains("'other_ladder'"), "each value once");
	}

	/** Either flag of a wrapped registry refuses a write, so either set means closed; both clear means open. */
	@Test void everyFrozenFlagOfTheHierarchyCounts() throws Exception {
		Wrapper wrapper = new Wrapper();
		assertEquals(Boolean.FALSE, KernelUnboundHolderData.closed(wrapper));
		set(wrapper, Wrapper.class, true);
		assertEquals(Boolean.TRUE, KernelUnboundHolderData.closed(wrapper), "the wrapper's own flag");
		set(wrapper, Wrapper.class, false);
		set(wrapper, Mapped.class, true);
		assertEquals(Boolean.TRUE, KernelUnboundHolderData.closed(wrapper), "the inherited vanilla flag");
	}

	@Test void anOwnerWithoutAFlagIsNotJudged() {
		assertNull(KernelUnboundHolderData.closed(new Lookup()));
		assertNull(KernelUnboundHolderData.closed(new StaticFlag()));
		assertNull(KernelUnboundHolderData.closed(null));
		String log = capture(() -> {
			KernelUnboundHolderData.unbound(new Object(), new Lookup());
			KernelUnboundHolderData.unbound(new Object(), null);
		});
		assertFalse(log.contains("WARN"), log);
	}

	/**
	 * A defect that leaves many values unbound is reported by its first {@link KernelUnboundHolderData#MAX_WARNED}
	 * values, one line then says more were found, and nothing follows — not a further value, not a repeat. Measured on
	 * its own {@code Reports}, so the count starts from zero whatever else in this JVM was reported.
	 */
	@Test void theWarningsStopAtTheCapWithOneLineSayingSo() throws Exception {
		Mapped closed = new Mapped();
		set(closed, Mapped.class, true);
		KernelUnboundHolderData.Reports reports = new KernelUnboundHolderData.Reports();
		List<Object> values = new ArrayList<>();
		for (int i = 0; i < KernelUnboundHolderData.MAX_WARNED + 2; i++) values.add(new StringBuilder("ladder_" + i + "_"));

		String log = capture(() -> values.forEach(value -> reports.unbound(value, closed)));
		assertEquals(KernelUnboundHolderData.MAX_WARNED, occurrences(log, "a data-map lookup asked about"), "one WARN per value up to the cap");
		assertEquals(1, occurrences(log, "no more are listed"), "then one line saying so");
		for (int i = 0; i < values.size(); i++) {
			assertEquals(i < KernelUnboundHolderData.MAX_WARNED, log.contains("'ladder_" + i + "_'"), "value " + i + " named iff under the cap");
		}
		String after = capture(() -> {
			reports.unbound(new StringBuilder("late_ladder"), closed);
			reports.unbound(values.get(0), closed);
		});
		assertEquals("", after, "nothing after the line that closed the list");
	}

	private static int occurrences(String text, String needle) {
		int count = 0;
		for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) count++;
		return count;
	}

	/** The caller has already decided the answer: nothing in the report may throw into it. */
	@Test void theReportNeverThrows() throws Exception {
		Mapped closed = new Mapped();
		set(closed, Mapped.class, true);
		String log = capture(() -> assertDoesNotThrow(() -> {
			KernelUnboundHolderData.unbound(new Unprintable(), closed);
			KernelUnboundHolderData.unbound(null, closed);
		}));
		assertTrue(log.contains(Unprintable.class.getName() + " '?'"), log);
	}
}
