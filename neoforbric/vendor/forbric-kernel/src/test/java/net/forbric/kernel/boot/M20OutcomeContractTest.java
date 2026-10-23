/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.boot;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs gate-m20's own client ask-outcome block against fixture logs, so what the gate accepts is checked without a
 * client. The first case is the evidence the old gate certified as "the boot continued": the notice answered
 * "launch anyway", and the kernel stopped the launch nine lines later.
 */
class M20OutcomeContractTest {
	@TempDir Path root;

	private static final String NOTICE_FORK = "[Forbric/Deps] -Dforbric.dependencyDialog=dryRun — forked the dialog for 1 finding(s) "
			+ "with no display; it answered 0 (launch anyway) without drawing anything\n";
	private static final String CONFIRMATION_FORK = "[Forbric/Deps] -Dforbric.dependencyDialog=dryRun — forked the dialog for 3 "
			+ "finding(s) with no display; it answered 3 (could not show it) without drawing anything\n";
	private static final String HANDED = "[Forbric/Compatibility] the confirmation window could not be shown; 3 required feature "
			+ "loss(es) will be asked about in the game's own window on the title screen, and nothing is approved until the "
			+ "player answers there\n";
	private static final String ASKED = "[Forbric/Compatibility] asking in the game about 3 required feature loss(es) the launch "
			+ "could not ask about in a window\n";
	private static final String LAUNCHING = "[Forbric/Deps] launching anyway with 1 finding(s), at the player's choice\n";
	private static final String STOPPED = "[Forbric/Compatibility] launch stopped: required mod initialization or features are "
			+ "unavailable; continuation was not approved\n[Forbric/Compatibility] launch stopped by compatibility policy; see "
			+ ".forbric-kernel/compatibility-report.json\n";

	@Test
	void theOldGreenRunWhoseLaunchTheKernelStoppedIsRed() throws Exception {
		assertEquals(1, outcome(NOTICE_FORK + LAUNCHING, NOTICE_FORK + LAUNCHING + STOPPED, 3, "78"));
	}

	@Test
	void oneUnshowableConfirmationAskedInTheGameIsGreen() throws Exception {
		String asked = CONFIRMATION_FORK + HANDED + "Sound engine started\n" + ASKED;
		assertEquals(0, outcome(asked, asked, 3, "killed"));
	}

	@Test
	void aRequiredLossTheLaunchContinuedPastWithoutAskingIsRed() throws Exception {
		assertEquals(1, outcome(CONFIRMATION_FORK + "Sound engine started\n", CONFIRMATION_FORK + "Sound engine started\n", 3, "killed"));
		String handedButNeverAsked = CONFIRMATION_FORK + HANDED + "Sound engine started\n";
		assertEquals(1, outcome(handedButNeverAsked, handedButNeverAsked, 3, "killed"), "handed over is not asked");
	}

	/** Issue #57: a window that could not be shown read as the player's refusal, and the launch stopped unasked. */
	@Test
	void aRequiredLossStoppedBecauseNoWindowCouldAskIsRed() throws Exception {
		String old = CONFIRMATION_FORK.replace("answered 3 (could not show it)", "answered 1 (not approved)");
		assertEquals(1, outcome(old, old + STOPPED, 3, "78"));
		assertEquals(1, outcome(CONFIRMATION_FORK + HANDED, CONFIRMATION_FORK + HANDED + STOPPED, 3, "78"));
	}

	@Test
	void twoWindowsAreRedEvenWhenTheGameAsked() throws Exception {
		String two = NOTICE_FORK + CONFIRMATION_FORK + HANDED + ASKED;
		assertEquals(1, outcome(two, two, 3, "killed"));
	}

	@Test
	void aStopWithNoWindowAtAllIsRed() throws Exception {
		assertEquals(1, outcome("", STOPPED, 3, "78"), "the dry run must still fork the one real child");
	}

	@Test
	void withNothingRequiredTheNoticeMustLetTheBootContinue() throws Exception {
		String continued = NOTICE_FORK + LAUNCHING + "Sound engine started\n";
		assertEquals(0, outcome(continued, continued, 0, "killed"));
		assertEquals(1, outcome(NOTICE_FORK + LAUNCHING, NOTICE_FORK + LAUNCHING + STOPPED, 0, "78"),
				"a stop with nothing required is not the continue it claims");
	}

	@Test
	void aCrashReportIsRedWhateverElseHappened() throws Exception {
		String asked = CONFIRMATION_FORK + HANDED + ASKED;
		assertEquals(1, outcome(asked, asked + "---- Minecraft Crash Report ----\n" + "Description: Initializing game\n", 3, "killed"));
	}

	private int outcome(String game, String whole, int required, String exit) throws Exception {
		Path log = root.resolve("gate-m20-client.log");
		Files.writeString(log, whole);
		Files.writeString(Path.of(log + ".game"), game);
		Files.writeString(Path.of(log + ".exit"), exit + "\n");
		Files.writeString(Path.of(log + ".json"), "{\"policy\":\"ASK\",\"schemaVersion\":1,\"confirmedRequired\":" + required
				+ ",\"findings\":[],\"catalogFailures\":[],\"mods\":[]}\n");
		String script = Files.readString(Path.of("run/gate-m20-depdialog.sh"));
		String body = section(script, "M20_HELPERS") + "\n" + section(script, "M20_ASK_OUTCOME");
		ProcessBuilder builder = new ProcessBuilder("bash", "-c", ". run/lib.sh\nCLOG=\"$1\"\n" + body + "\nexit \"$FAIL\"",
				"gate", log.toString());
		Process process = builder.redirectErrorStream(true).redirectOutput(root.resolve("gate-check.log").toFile()).start();
		assertTrue(process.waitFor(30, TimeUnit.SECONDS));
		return process.exitValue();
	}

	private static String section(String script, String name) {
		int begin = script.indexOf("# " + name + "_BEGIN");
		int end = script.indexOf("# " + name + "_END", begin);
		assertTrue(begin >= 0 && end > begin, name + " markers");
		return script.substring(script.indexOf('\n', begin) + 1, end);
	}
}
