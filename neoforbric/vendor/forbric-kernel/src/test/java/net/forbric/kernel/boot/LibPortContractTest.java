package net.forbric.kernel.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives {@code run/lib.sh}'s {@code port_was_free}, alone and through both ways {@code await_server} returns.
 *
 * <p>A server that loses its port still prints "Stopping server" and "All dimensions are saved", so a gate's
 * shutdown checks pass and it goes red on "Done" instead, reading as the kernel failing to boot. The fixture below
 * is abridged from a real one: gate-m1 run with its port held by another process. What has to hold is that every
 * gate waiting on its server through {@code await_server} hears about the port — on the path where the server is
 * already gone when the wait starts as much as on the one where it is still shutting down.
 */
class LibPortContractTest {
	private static final Path LIB = Path.of("run/lib.sh");
	private static final String LOST = """
			[19:28:49] [Server thread/INFO]: Starting minecraft server version 26.2
			[19:28:49] [Server thread/INFO]: Starting Minecraft server on *:25790
			[19:28:49] [Server thread/WARN]: **** FAILED TO BIND TO PORT!
			[19:28:49] [Server thread/WARN]: Perhaps a server is already running on that port?
			[19:28:49] [Server thread/ERROR]: Encountered an unexpected exception
			[19:28:49] [Server thread/FATAL]: Preparing crash report with UUID bd805bbe-d7c6-41fa-a9a7-cf5642cc1ea6
			[19:28:49] [Server thread/INFO]: Stopping server
			[19:28:49] [Server thread/INFO]: All dimensions are saved
			""";
	private static final String HEALTHY = """
			[12:00:00] [Server thread/INFO]: Starting Minecraft server on *:25700
			[12:00:04] [Server thread/INFO]: Done (3.210s)! For help, type "help"
			[12:00:09] [Server thread/INFO]: Stopping server
			""";

	@TempDir Path temporary;

	@Test void aLostPortFailsAndSaysWhichOne() throws Exception {
		Result r = run("port_was_free \"" + log(LOST) + "\"");
		assertEquals(1, r.exit(), r.out());
		assertTrue(r.out().contains("FAIL the server never got its port (*:25790)"), r.out());
	}

	@Test void aLostPortWithNoStartLineStillFails() throws Exception {
		Result r = run("port_was_free \"" + log("[19:28:49] [Server thread/WARN]: **** FAILED TO BIND TO PORT!\n") + "\"");
		assertEquals(1, r.exit(), r.out());
		assertTrue(r.out().contains("the log does not say which"), r.out());
	}

	@Test void aServerThatGotItsPortAddsNothing() throws Exception {
		// Silent, like await_server's leaked-thread check: forty-odd gates run this, and a PASS line in each of them
		// would say nothing the gate's own "server reached Done" does not.
		Result r = run("port_was_free \"" + log(HEALTHY) + "\"");
		assertEquals(0, r.exit(), r.out());
		assertEquals("", r.out());
	}

	@Test void aMissingLogIsLeftToTheChecksThatReadIt() throws Exception {
		// Every gate's own check/check_absent already fails a log the run never wrote; a second FAIL saying "no
		// port problem found" in a file that does not exist would only be noise.
		Result r = run("port_was_free \"" + temporary.resolve("never-written.log") + "\"");
		assertEquals(0, r.exit(), r.out());
		assertEquals("", r.out());
	}

	@Test void awaitServerNamesTheLostPortWhenTheServerIsAlreadyGone() throws Exception {
		// A gate that does something else between starting its server and waiting for it can find the server already
		// gone: one that cannot bind exits seconds after it starts. await_server's early return was the one path with
		// no check after it.
		Result r = run("true & pid=$!\nwait \"$pid\"\nawait_server \"$pid\" \"" + log(LOST) + "\" 5 2");
		assertEquals(1, r.exit(), r.out());
		assertTrue(r.out().contains("never got its port (*:25790)"), r.out());
	}

	@Test void awaitServerNamesTheLostPortWhenItWaitsForTheStop() throws Exception {
		Result r = run("sleep 2 & pid=$!\nawait_server \"$pid\" \"" + log(LOST) + "\" 10 5");
		assertEquals(1, r.exit(), r.out());
		assertTrue(r.out().contains("never got its port (*:25790)"), r.out());
		assertFalse(r.out().contains("still alive"), "the server left inside the grace window: " + r.out());
	}

	@Test void awaitServerIsQuietAboutAServerThatGotItsPort() throws Exception {
		Result r = run("sleep 1 & pid=$!\nawait_server \"$pid\" \"" + log(HEALTHY) + "\" 10 5");
		assertEquals(0, r.exit(), r.out());
		assertEquals("", r.out());
	}

	private Path log(String contents) throws Exception {
		Path p = temporary.resolve("server.log");
		Files.writeString(p, contents, StandardCharsets.UTF_8);
		return p;
	}

	private Result run(String call) throws Exception {
		String script = ". \"" + LIB.toAbsolutePath() + "\"\nFAIL=0\n" + call + "\nexit \"$FAIL\"\n";
		Path runner = temporary.resolve("runner.sh");
		Files.writeString(runner, script, StandardCharsets.UTF_8);
		ProcessBuilder pb = new ProcessBuilder(List.of("bash", runner.toString()));
		pb.directory(Path.of("").toAbsolutePath().toFile());
		// To a file, not a pipe: reading a pipe to its end blocks until the script exits, so a hang would never reach
		// the timeout below.
		Path output = temporary.resolve("runner.out");
		pb.redirectErrorStream(true);
		pb.redirectOutput(output.toFile());
		Process p = pb.start();
		if (!p.waitFor(60, TimeUnit.SECONDS)) {
			p.descendants().forEach(ProcessHandle::destroyForcibly);
			p.destroyForcibly();
			throw new IllegalStateException("lib.sh did not finish: " + Files.readString(output, StandardCharsets.UTF_8));
		}
		return new Result(p.exitValue(), Files.readString(output, StandardCharsets.UTF_8));
	}

	private record Result(int exit, String out) {
	}
}
