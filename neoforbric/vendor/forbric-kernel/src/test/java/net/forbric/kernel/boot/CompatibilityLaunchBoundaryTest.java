/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.boot;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import net.forbric.api.CompatibilityFinding;
import net.forbric.api.CompatibilityFindings;
import net.forbric.api.ModCatalog;
import net.forbric.kernel.ui.CompatibilityDecision;

@ResourceLock("ModCatalog")
@ResourceLock("system-properties")
class CompatibilityLaunchBoundaryTest {
	@BeforeEach @AfterEach void reset() {
		CompatibilityDecision.reset(); CompatibilityFindings.reset(); ModCatalog.publish(List.of());
		System.clearProperty(CompatibilityDecision.PROPERTY);
	}

	private static void requireStop() {
		CompatibilityFindings.record(new CompatibilityFinding("initialization:test", "broken", "Initialization", "test",
				CompatibilityFinding.Confidence.CONFIRMED, true, "constructor failed", List.of("actually threw")));
		System.setProperty(CompatibilityDecision.PROPERTY, "strict");
		CompatibilityDecision.requireContinuation(false);
	}

	@Test void typedPolicyStopsRemainRecognizableThroughReflectionAndClassInitialization() throws Throwable {
		var stop = assertThrows(CompatibilityDecision.LaunchStopped.class, CompatibilityLaunchBoundaryTest::requireStop);
		for (Throwable wrapped : List.of(stop, new InvocationTargetException(stop),
				new ExceptionInInitializerError(new InvocationTargetException(stop)))) {
			assertTrue(CompatibilityDecision.isLaunchStop(wrapped));
			assertEquals(78, CompatibilityLaunchBoundary.run(() -> { throw wrapped; }));
		}
	}

	@Test void nonPolicyFailuresStillPropagateEvenAfterARecordedPolicyStop() {
		assertThrows(CompatibilityDecision.LaunchStopped.class, CompatibilityLaunchBoundaryTest::requireStop);
		var ordinary = new ExceptionInInitializerError(new IllegalStateException("Forbric compatibility policy stopped this launch"));
		assertFalse(CompatibilityDecision.isLaunchStop(ordinary), "text resembling a policy message is not evidence");
		assertSame(ordinary, assertThrows(ExceptionInInitializerError.class, () -> CompatibilityLaunchBoundary.run(() -> { throw ordinary; })));
	}

	@Test void aGameMainThatCatchesThePolicyStopCannotReturnASuccessExitStatus() throws Throwable {
		assertEquals(78, CompatibilityLaunchBoundary.run(() -> {
			try { requireStop(); } catch (CompatibilityDecision.LaunchStopped deliberatelyCaughtByGameMain) { }
		}));
		assertEquals(1, CompatibilityFindings.confirmedRequired().size());
	}

	@Test void aBrokenInstallStopsWithItsOwnExitCodeInsteadOfCrashing() throws Throwable {
		String said = KernelLoadReportTest.capture(() -> {
			try {
				assertEquals(CompatibilityLaunchBoundary.INPUTS_REJECTED, CompatibilityLaunchBoundary.run(() -> {
					throw new LaunchInputCheck.Rejected(List.of("runtime jar x.jar contains neither NeoForge nor MinecraftForge"));
				}));
			} catch (Throwable unexpected) { throw new AssertionError(unexpected); }
		});
		assertEquals(2, CompatibilityLaunchBoundary.INPUTS_REJECTED, "the code a launch given no --gameJar already exits with");
		assertTrue(said.contains("the reason and the fix are in logs/latest.log"), said);
		assertFalse(said.contains("[Forbric/Boot] the game stopped during startup"), "a refusal is not a crash: " + said);
		assertFalse(CompatibilityDecision.isLaunchStop(new LaunchInputCheck.Rejected(List.of())),
				"and not a compatibility-policy stop either, which exits 78 and points at a report that was never written");
	}

	/**
	 * Issue #13: the boot's NoClassDefFoundError left {@code main} for the JVM's default handler, which prints to stderr;
	 * launchers show {@code latest.log}, and the player's had five INFO lines. Without log4j on the test classpath,
	 * ForbricLog's ERROR lands on stderr, which is what is captured here; in the game it is a line in latest.log.
	 */
	@Test void anythingElseLeavingTheBootIsLoggedWithItsTraceAndThenRethrownUnchanged() {
		Throwable root = new NoClassDefFoundError("net/neoforged/neoforgespi/language/IModInfo");
		Throwable thrown = new InvocationTargetException(root);
		Throwable[] escaped = new Throwable[1];

		String said = KernelLoadReportTest.capture(() -> escaped[0] = assertThrows(Throwable.class,
				() -> CompatibilityLaunchBoundary.run(() -> { throw thrown; })));

		assertSame(thrown, escaped[0], "logging must not replace the failure the launcher sees");
		assertTrue(said.contains("[Forbric/ERROR] [Forbric/Boot] the game stopped during startup on "
				+ "java.lang.NoClassDefFoundError: net/neoforged/neoforgespi/language/IModInfo"),
				"the headline names the real error, not the reflection wrapper: " + said);
		assertTrue(said.contains("at net.forbric.kernel.boot.CompatibilityLaunchBoundaryTest"), "with its trace: " + said);
	}

	@Test void aPolicyStopIsNotLoggedAsACrash() {
		String said = KernelLoadReportTest.capture(() -> {
			try {
				assertEquals(78, CompatibilityLaunchBoundary.run(CompatibilityLaunchBoundaryTest::requireStop));
			} catch (Throwable unexpected) { throw new AssertionError(unexpected); }
		});
		assertFalse(said.contains("[Forbric/Boot] the game stopped during startup"), said);
	}

	@Test void aHealthyLaunchReturnsNormallyAndACyclicUnrelatedCauseIsNotAPolicyStop() throws Throwable {
		assertEquals(0, CompatibilityLaunchBoundary.run(() -> { }));
		Exception first = new Exception(), second = new Exception(first); first.initCause(second);
		assertFalse(CompatibilityDecision.isLaunchStop(first));
	}
}
