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

package net.forbric.kernel.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * The dialog, as a separate process's {@code main}.
 *
 * <p><b>Why a separate process.</b> On macOS the game JVM is started with {@code -XstartOnFirstThread} (see
 * {@code run/launch-kernel-client.sh}), which GLFW requires and which AWT cannot coexist with: both want thread
 * one.
 *
 * <p>Fabric Loader forks there too, and it is worth being exact about what it keys off, because the obvious
 * reading is wrong. With Minecraft's game provider registered it never inspects the environment at all:
 * {@code MinecraftGameProvider.hasAwtSupport()} is {@code !LoaderUtil.hasMacOs()} and nothing more
 * ({@code invokestatic hasMacOs / ifne 10 / iconst_1 ... iconst_0 / ireturn}), so the decision is
 * {@code os.name} alone. Its scan for an environment key beginning {@code JAVA_STARTED_ON_FIRST_THREAD_} lives
 * in {@code LoaderUtil.hasAwtSupport()}, which {@code FabricGuiEntry.open} reaches only while no provider has
 * been established yet. That environment variable is real — measured here, a JVM started with the flag has
 * {@code JAVA_STARTED_ON_FIRST_THREAD_<pid>} and one started without it does not — it is simply not the trigger
 * on the path that matters. The technique is Fabric's either way, read out of the bytecode rather than copied.
 *
 * <p>Forbric forks ALWAYS, on every platform, rather than only where it must. A dialog costs a JVM start only on
 * the boots that have something to report, which are rare, and in exchange there is ONE code path instead of two.
 * The alternative — in-process where it is safe, forked where it is not — is the shape that has to be right on
 * three operating systems and is only ever exercised on whichever one the author had.
 *
 * <p>The one AWT class the game's own process touches is {@code GraphicsEnvironment.isHeadless()}, in
 * {@link DependencyDialog}'s guard. Measured on a JVM started with {@code -XstartOnFirstThread}: it returns in
 * about 12ms and starts no {@code AWT-} thread, so the guard cannot be the thing that breaks the window it
 * guards. That is a measurement of that one call, not a claim that AWT is never loaded.
 *
 * <p>Exit code IS the answer: {@code 0} continue, {@code 1} quit. Anything else the parent reads as continue,
 * because a dialog that fails must not be able to stop a launch that would otherwise have worked. The crash-suspects
 * offer ({@code --isolation}) adds {@code 2}, start without the suspects; any other answer there, a closed window
 * included, starts the game with every mod, which is what would have happened without the offer. The confirmation
 * ({@code --compatibility}) is the fail-closed one: {@code 0} is the only consent, {@code 4} the player's refusal or a
 * closed window, and anything else -- {@code 3} for a window that could not be shown, or the {@code java} launcher's
 * own {@code 1} when it could not start the child -- nobody having answered, which the parent hands to the game.
 *
 * <h2>What the player is shown</h2>
 *
 * <p>Three layers, in the order a player needs them:
 *
 * <ol>
 *   <li><b>What is wrong</b>, in their own language and in their own words — which mod, and what it wanted.
 *       {@link DialogLang} holds the words.</li>
 *   <li><b>What might fix it.</b> A warning that does not say what to do next leaves the reader with nothing but
 *       the feeling that something is broken. Every suggestion here is derived from what the kernel actually
 *       measured — the required id, the declared range, the version that is installed, the ecosystem the
 *       dependent belongs to — and is worded as a possibility, because that is all any of it is.</li>
 *   <li><b>The technical detail</b>, behind {@code Show details} and hidden by default. Mixin class names,
 *       unresolved anchors and version ranges are what a mod author or a bug report needs, and they are the
 *       reason the previous version of this dialog opened as a wall of text that a player would close without
 *       reading.</li>
 * </ol>
 *
 * <h2>Headless</h2>
 *
 * <p>EVERY line that touches a window — building the components, sizing against the screen, creating the dialog,
 * showing it — is inside one try that answers {@link #CONTINUE}. Nothing about this is decoration: an uncaught
 * {@code HeadlessException} leaves the JVM with exit code 1, the parent reads 1 as {@link #QUIT}, and a client
 * with no display would quit the game on the player's behalf — an inversion of the exact policy this whole
 * feature exists to uphold.
 */
public final class DependencyDialogMain {
	/** The player chose to launch anyway. */
	public static final int CONTINUE = 0;
	/** The player chose to quit and go install something. */
	public static final int QUIT = 1;
	/** The crash-suspects offer only: start without the mods the last crash pointed at. */
	public static final int WITHOUT = 2;
	/**
	 * The confirmation only: the window could not be shown, so nobody answered. Neither consent nor refusal -- the
	 * launch asks again in the game's own window ({@link CompatibilityDecision}), because on a phone launcher or any
	 * other runtime this child cannot draw on, reading "could not ask" as "the player said no" meant that every launch
	 * with a required loss stopped, and the player was never once shown why.
	 */
	public static final int UNSHOWN = 3;
	/**
	 * The confirmation's exit code for the player's refusal -- Quit, or closing the window -- which the parent reads
	 * back as {@link #QUIT}. Not 1: the {@code java} launcher itself exits 1 when it cannot start the child (no main
	 * class, a JVM that will not initialise), and that is nobody having answered, not the player saying no.
	 */
	public static final int REFUSED = 4;

	/**
	 * How many findings the summary names before it hands the rest to the details.
	 *
	 * <p>A pack with forty unmet requirements would otherwise produce a summary that is itself the wall of text
	 * the details button exists to put away.
	 */
	static final int SUMMARY_BULLETS = 6;

	/** The width the wrapped player-facing text is laid out at, in pixels before HiDPI scaling. */
	private static final int TEXT_WIDTH = 640;

	/**
	 * What marks a line as a list item.
	 *
	 * <p>A marker inside the text, read back by {@link #block}, rather than a structure the builders return. The
	 * text is what the tests assert on and what a bug report can be pasted from; the layout is one reading of it.
	 */
	private static final String BULLET = "  • ";

	/** The continuation of a capped list: indented like a bullet, but not one. */
	private static final String MORE = "    ";

	private DependencyDialogMain() {
	}

	/**
	 * Takes Java2D off the Direct3D pipeline on Windows before anything is drawn.
	 *
	 * <p>Measured, not guessed: on the machine that reported this, the Windows look and feel hands Swing an
	 * ordinary palette — panel 240/240/240, text area white, text black — and this dialog sets no colour of its
	 * own, yet it painted itself yellow with blue and red text. Nothing computed those colours; they were painted
	 * wrong, which is a rendering-pipeline fault rather than a theming one.
	 *
	 * <p>The installer already carries this exact workaround, for the same symptom in the other window, so this is
	 * a known hazard on this platform rather than a hunch. Only when the caller has no opinion, and only on
	 * Windows, so it never overrides a deliberate {@code -Dsun.java2d.d3d}.
	 *
	 * <p>The cost is software rendering for one modal warning, which nothing animates.
	 */
	private static void avoidOverlayRenderingCorruption() {
		if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) return;
		if (System.getProperty("sun.java2d.d3d") != null) return;
		System.setProperty("sun.java2d.d3d", "false");
	}

	public static void main(String[] args) {
		avoidOverlayRenderingCorruption();
		if (args.length > 1 && "--compatibility".equals(args[1])) {
			confirmationMain(Path.of(args[0]));
			return;
		}
		if (args.length > 1 && "--isolation".equals(args[1])) {
			isolationMain(Path.of(args[0]));
			return;
		}
		if (args.length < 1) System.exit(CONTINUE);
		List<DependencyReport.Row> rows;
		List<DependencyReport.MixinRow> mixins;
		List<DependencyReport.CompatibilityRow> suspected;
		try {
			rows = DependencyReport.read(Path.of(args[0]));
			mixins = DependencyReport.readMixins(Path.of(args[0]));
			suspected = DependencyReport.readSuspected(Path.of(args[0]));
		} catch (Throwable unreadable) {
			System.exit(CONTINUE);
			return;
		}
		if (rows.isEmpty() && mixins.isEmpty()) System.exit(CONTINUE);

		DialogLang lang = DialogLang.ofSystem();
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Throwable ignored) {
			// The cross-platform look and feel is not worth failing a warning over.
		}

		int answer;
		try {
			answer = askOnEventThread(lang, rows, mixins, suspected);
		} catch (Throwable noDisplay) {
			// The single net. See the class note: anything other than CONTINUE here would be the kernel quitting
			// the game for a player who was never asked.
			System.exit(CONTINUE);
			return;
		}
		System.exit(answer);
	}

	private static void confirmationMain(Path report) {
		// Until the player answers, nobody has: an unreadable report or a window that cannot be shown approves nothing
		// and refuses nothing.
		int answer = UNSHOWN;
		try {
			DependencyReport.Confirmation confirmation = DependencyReport.readConfirmation(report);
			if (confirmation.required().isEmpty()) { System.exit(UNSHOWN); return; }
			DialogLang lang = DialogLang.ofSystem();
			try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
			catch (Exception ignored) { }
			int[] result = { QUIT };
			SwingUtilities.invokeAndWait(() -> result[0] = showCompatibility(lang, confirmation));
			answer = result[0] == CONTINUE ? CONTINUE : REFUSED;
		} catch (Throwable unavailable) {
			// Unlike the legacy dependency notice, no answer is never permission to continue -- and it is not the
			// player's refusal either: UNSHOWN.
		}
		System.exit(answer);
	}

	private static int showCompatibility(DialogLang lang, DependencyReport.Confirmation confirmation) {
		return showContent(lang, confirmationBlocks(lang, confirmation), confirmationDetails(lang, confirmation),
				lang.get("compat.title"), true);
	}

	private static void isolationMain(Path report) {
		int answer = CONTINUE;
		try {
			DependencyReport.Isolation isolation = DependencyReport.readIsolation(report);
			if (isolation.without().isEmpty()) { System.exit(CONTINUE); return; }
			DialogLang lang = DialogLang.ofSystem();
			try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
			catch (Exception ignored) { }
			int[] result = { CONTINUE };
			SwingUtilities.invokeAndWait(() -> result[0] = showContent(lang, isolationBlocks(lang, isolation),
					isolationDetails(lang, isolation), lang.get("isolation.title"),
					content -> isolationPane(lang, isolation, content), value -> isolationAnswerFrom(lang, isolation, value)));
			answer = result[0];
		} catch (Throwable unavailable) {
			// No answer switches nothing off: the game starts with every mod, as it would have without the offer.
		}
		System.exit(answer);
	}

	/**
	 * The offer's text: what the crash pointed at, what starting without it does and how to undo it, then the
	 * caveat — which also says that closing the window changes nothing.
	 */
	static List<String> isolationBlocks(DialogLang lang, DependencyReport.Isolation isolation) {
		StringBuilder summary = new StringBuilder(isolation.kept().isEmpty() ? lang.get("isolation.intro")
				: lang.get("isolation.intro.clash", isolation.kept())).append("\n\n");
		int shown = Math.min(isolation.without().size(), SUMMARY_BULLETS);
		for (int i = 0; i < shown; i++) {
			DependencyReport.IsolationRow row = isolation.without().get(i);
			summary.append(BULLET).append(lang.get("isolation.bullet", row.name(), row.jar())).append('\n');
		}
		if (isolation.without().size() > shown) {
			summary.append(MORE).append(lang.get("summary.more", isolation.without().size() - shown)).append('\n');
		}
		return List.of(summary.toString(), lang.get("isolation.without"), lang.get("isolation.note"));
	}

	static String isolationDetails(DialogLang lang, DependencyReport.Isolation isolation) {
		StringBuilder text = new StringBuilder();
		for (DependencyReport.IsolationRow row : isolation.without()) {
			text.append("  ").append(row.name()).append("  (").append(row.modId()).append(")\n")
					.append("      ").append(row.jar()).append("\n\n");
		}
		return text.append(lang.get("isolation.details.report", isolation.report())).append('\n').toString();
	}

	/**
	 * Start without them, start with everything, quit — in that order. The first is the keyboard default: it is
	 * what the window offers, and it is undone by deleting a line. Quit never is, for the reason {@link #options}
	 * gives.
	 */
	static Object[] isolationOptions(DialogLang lang, DependencyReport.Isolation isolation) {
		List<String> names = new ArrayList<>();
		for (DependencyReport.IsolationRow row : isolation.without()) if (!names.contains(row.name())) names.add(row.name());
		String named = names.size() <= 3 ? String.join(", ", names) : String.join(", ", names.subList(0, 3)) + " …";
		return new Object[] { lang.get("button.isolation.without", named), lang.get("button.isolation.everything"),
				lang.get("button.quit") };
	}

	static JOptionPane isolationPane(DialogLang lang, DependencyReport.Isolation isolation, Component content) {
		Object[] options = isolationOptions(lang, isolation);
		return new JOptionPane(content, JOptionPane.WARNING_MESSAGE, JOptionPane.DEFAULT_OPTION, null, options, options[0]);
