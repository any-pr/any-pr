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

package net.forbric.kernel.boot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.forbric.kernel.util.ForbricLog;

/**
 * Drives an unattended client run so a gate can assert on it: enter a world, live in it, leave cleanly, exit.
 *
 * <p>Every client fix in this kernel has been verified by launching the game and reading the log by hand, which
 * means none of them is protected against the next change. The obstacle is that a client does not end on its own
 * — quick-play gets it into a world, and then it sits there. This is the missing half: a tick hook that counts
 * ticks spent actually in a world, then asks the game to disconnect and stop, so a gate script can wait for a
 * definite outcome instead of a timeout.
 *
 * <p>Three markers, in order, and the gate asserts all three because each rules out a different failure. Joining
 * says the world loaded; surviving READY_TICKS says it did not die on the first tick of real simulation, which is
 * where registry and attribute problems land; the clean disconnect says teardown works and — because the launcher
 * deliberately does not kill the process afterwards — leaves vanilla's own shutdown watchdog free to catch a
 * leaked non-daemon thread.
 *
 * <p>Off unless {@code -Dforbric.clientSmoke=true}. Everything is reached reflectively and every failure is
 * swallowed: a diagnostic must never be able to break the thing it is measuring.
 */
public final class KernelClientSmoke {
	public static final String ENABLED = "forbric.clientSmoke";
	private static final String WORLD = "forbric.clientSmokeWorld";
	private static final String READY_TICKS = "forbric.clientSmokeReadyTicks";
	private static final String DISCONNECT_TICKS = "forbric.clientSmokeDisconnectTicks";
	/** {@code true}: after client-ready, drive the player through the movement drill (see {@link #drill}). */
	public static final String DRILL = "forbric.clientSmokeDrill";
	/** {@code true}: end the drill with one deliberately impossible move, so the gate can prove the anti-cheat is watching. */
	public static final String DRILL_CONTROL = "forbric.clientSmokeDrillControl";
	/**
	 * {@code x,y,z;x,y,z;…}: block positions to read back after client-ready and log by registry name. What a gate
	 * uses to see whether the client decodes the server's blocks as the server meant them — a registry-id mismatch
	 * shows up here as the wrong name, while everything else about the session looks fine.
	 */
	public static final String PROBE = "forbric.clientSmokeProbe";
	private static final String PROBE_TICKS = "forbric.clientSmokeProbeTicks";
	/**
	 * {@code registry:namespace:path;…} (registry is {@code item} or {@code block}): entries whose raw ids to log at
	 * three moments — before connecting, in the world, and after the clean disconnect. The three lines are what a
	 * gate uses to see a remap happen AND be undone: the first and last must agree, the middle may differ.
	 */
	public static final String PROBE_IDS = "forbric.clientSmokeProbeIds";
	/**
	 * {@code tick[,tick…]}: world ticks at which to save a screenshot into {@code <gameDir>/screenshots}.
	 *
	 * <p>Every other marker this class produces is a log line, which can only ever say that code RAN. A feature
	 * whose whole output is pixels — a mod's particles, a ragdoll, a shader pass — runs exactly the same when it
	 * draws nothing, so a log-only gate calls that green. This is the seam for asserting on the frame itself.
	 */
	public static final String SCREENSHOTS = "forbric.clientSmokeScreenshots";

	/**
	 * World tick at which to open the unified Mods screen, hold it, and close it again.
	 *
	 * <p>A screen is the one thing here no unit test can prove: its {@code init} and its draw run only when a
	 * player clicks the button, so a mistake in either is a crash in the middle of a frame on someone else's
	 * machine. This opens it on a real client and reads back how many frames it drew.
	 */
	public static final String MODS_SCREEN = "forbric.clientSmokeModsScreen";
	/**
	 * {@code modid[,modid…]}: open each of these mods' config screens the way a player does — select the mod's row in
	 * the unified Mods screen and press its Config button — then log the class of the screen in front and save a
	 * screenshot named {@code forbric-config-<modid>.png}.
	 *
	 * <p>The class name says whose screen it is; the picture says it is that mod's settings and not an empty frame
	 * or a crash screen. Neither is a claim the resolver can make about itself: it can only say it returned an
	 * object. Starts at {@link #CONFIG_SCREENS_AT}; give the run about {@link #CONFIG_SCREEN_HOLD} + 4 ticks per mod.
	 */
	public static final String CONFIG_SCREENS = "forbric.clientSmokeConfigScreens";
	/** World tick at which {@link #CONFIG_SCREENS} starts (default 100). */
	public static final String CONFIG_SCREENS_AT = "forbric.clientSmokeConfigScreensAt";
	/** Ticks a config screen is left up before its screenshot: long enough for its own init and a few frames. */
	private static final int CONFIG_SCREEN_HOLD = 12;
	/** World tick at which vanilla's key binds screen is opened, to see which screen the game ends up showing. */
	public static final String KEY_BINDS_SCREEN = "forbric.clientSmokeKeyBinds";
	/** Take a screenshot of the pause menu with the mods button on it, instead of pressing it. */
	public static final String MODS_BUTTON_SHOT = "forbric.clientSmokeModsButtonShot";
	/**
	 * World tick at which to open a container screen and drive one press, one drag, one release and one wheel
	 * notch through the game's OWN {@code MouseHandler}. 0 (the default) leaves it alone.
	 *
	 * <p>It exists because the screen-mouse bridges cannot be judged from anything the kernel says. A forward
	 * counter proves the kernel forwarded; what was in doubt is whether a traditional-Forge mod's listener runs,
	 * and the only witness to that is the mod itself. Driving {@code MouseHandler.onButton},
	 * {@code handleAccumulatedMovement} and {@code onScroll} — the three private methods the merged base's own
	 * GLFW callbacks call, and the three that hold NeoForge's hooks — makes a mod that listens on the
	 * MinecraftForge side speak, or stay silent, with nothing in between.
	 */
	public static final String SCREEN_MOUSE = "forbric.clientSmokeScreenMouse";

	/**
	 * World tick at which to run the carry drill: hold sneak with empty hands and right-click a chest, then the
	 * floor, then a pig, then the floor again — the gesture Carry On and every mod like it is built on. 0 (the
	 * default) leaves it alone.
	 *
	 * <p>Every input goes through the game's OWN {@code KeyboardHandler.keyPress} and {@code MouseHandler.onButton}
	 * — the private methods GLFW's callbacks call — so the drill covers the whole chain a player's hands start:
	 * the key mapping going down, a mod's client tick noticing it, the mod's packet to the server, the server's
	 * interaction event, and the world changing. Each report reads the WORLD (is the chest still there, is the pig)
	 * and, when Carry On is installed, Carry On's own data on both sides; nothing Forbric says is part of the answer.
	 */
	public static final String CARRY = "forbric.clientSmokeCarry";

	/** World tick at which to equip an elytra and try to glide. */
	public static final String ELYTRA = "forbric.clientSmokeElytra";
	/** Altitude to drop from. Absolute, because the world keeps whatever the last run left behind. */
	public static final String ELYTRA_ALTITUDE = "forbric.clientSmokeElytraAltitude";
	/** Ticks to leave it open. Long enough for frames to be drawn, short enough not to move the disconnect. */
	private static final int MODS_SCREEN_HOLD = 20;

	private static Object lastLevel;
	private static int worldTicks;
	private static boolean joined;
	private static boolean ready;
	private static boolean disconnectRequested;
	private static boolean stopRequested;
	private static int drillTick = -1;
	private static boolean drillDone;
	private static boolean probed;
	private static final java.util.Set<Integer> shotsTaken = new java.util.HashSet<>();
	private static boolean idsLoggedBeforeConnect;

	private KernelClientSmoke() {
	}

	/** Whether the smoke run is armed. Read per call so a test can drive both modes in one JVM. */
	public static boolean enabled() {
		return Boolean.getBoolean(ENABLED) || KernelSoakHooks.enabled();
	}

	/**
	 * One client tick. {@code minecraft} is typed {@code Object} because this class is BOOT-side and cannot name
	 * {@code net.minecraft} types at compile time — the same widening-reference trick the other hooks use.
	 */
	public static void onClientTick(Object minecraft) {
		if (KernelSoakHooks.enabled()) { KernelSoakHooks.onClientTick(minecraft); return; }
		if (minecraft == null || stopRequested || !enabled()) return;
		try {
			tick(minecraft);
		} catch (Throwable t) {
			ForbricLog.debug("[Forbric/ClientSmoke] tick hook failed: %s", String.valueOf(t));
		}
	}

	private static void tick(Object minecraft) {
		if (!connectionProbesArmed) armConnectionProbes(minecraft);
		Object level = fieldValue(minecraft, "level");
		Object player = fieldValue(minecraft, "player");

		if (level == null || player == null) {
			// Out of a world. If we asked to leave one, that request has now been honoured.
			lastLevel = null;
			worldTicks = 0;
			if (!idsLoggedBeforeConnect) {
				idsLoggedBeforeConnect = true;
				probeIds(minecraft, "before connecting");
			}
			if (disconnectRequested) {
				disconnectRequested = false;
				stopRequested = true;
				probeIds(minecraft, "after disconnect");
				ForbricLog.info("[Forbric/ClientSmoke] clean disconnect observed; stopping client");
				invokeNoArg(minecraft, "stop");
			}
			return;
		}

		if (level != lastLevel) {
			lastLevel = level;
			worldTicks = 0;
			joined = false;
			ready = false;
			disconnectRequested = false;
		}

		worldTicks++;
		lastPlayer = player;
		if (!joined) {
			joined = true;
			ForbricLog.info("[Forbric/ClientSmoke] joined world via quick-play: %s",
					System.getProperty(WORLD, "<quick-play>"));
		}
		if (!ready && worldTicks >= Integer.getInteger(READY_TICKS, 60)) {
			ready = true;
			ForbricLog.info("[Forbric/ClientSmoke] client-ready after %d world tick(s)", worldTicks);
			if (connectionProbesArmed) reportClientCommands(minecraft);
			reportWindowTitle(minecraft);
		}
		if (ready && !drillDone && Boolean.getBoolean(DRILL)) drill(minecraft, player);
		if (ready) elytraCheck(minecraft, player);
		if (ready) carryIfDue(minecraft, player);
		if (ready) screenMouseIfDue(minecraft, player);
		if (ready) screenshotIfDue(minecraft);
		if (ready) keyBindsScreenIfDue(minecraft);
		if (ready) modsScreenIfDue(minecraft);
		if (ready) configScreensIfDue(minecraft);
		if (ready) creativeSearchIfDue(minecraft, player);
		if (ready && !tooltipProbed) probeTooltip(level, player);
		if (ready && !probed && worldTicks >= Integer.getInteger(PROBE_TICKS, 160)) {
			probed = true;
			probeBlocks(level);
			probeIds(minecraft, "in world");
		}
		if (!disconnectRequested && worldTicks >= Integer.getInteger(DISCONNECT_TICKS, 120)) {
			disconnectRequested = true;
			ForbricLog.info("[Forbric/ClientSmoke] requesting clean disconnect after %d world tick(s)", worldTicks);
			invokeNoArg(minecraft, "disconnectWithSavingScreen");
		}
	}

	private static boolean keyBindsTried;

	private static boolean connectionProbesArmed;

	/**
	 * Registers one client command through NeoForge's registration event, before any world is joined. What the
	 * game's command tree holds after joining says whether the registration reached the dispatcher the game runs.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void armConnectionProbes(Object minecraft) {
		connectionProbesArmed = true;
		try {
			ClassLoader cl = minecraft.getClass().getClassLoader();
			Class<?> literal = Class.forName("com.mojang.brigadier.builder.LiteralArgumentBuilder", true, cl);
			java.util.function.BiConsumer<Object, String> register = (dispatcher, name) -> {
				try {
					Object node = literal.getMethod("literal", String.class).invoke(null, name);
					dispatcher.getClass().getMethod("register", literal).invoke(dispatcher, node);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			};
			Object neoBus = Class.forName("net.neoforged.neoforge.common.NeoForge", true, cl).getField("EVENT_BUS").get(null);
			Class<?> neoEvent = Class.forName(net.forbric.api.ForeignType.CLIENT_COMMANDS_EVENT.binary(net.forbric.api.Ecosystem.NEOFORGE), true, cl);
			neoBus.getClass().getMethod("addListener", Class.class, java.util.function.Consumer.class).invoke(neoBus, neoEvent,
					(java.util.function.Consumer) e -> register.accept(invoke(e, "getDispatcher"), "forbricsmokeneo"));
		} catch (ClassNotFoundException absent) {
			ForbricLog.debug("[Forbric/ClientSmoke] NeoForge absent on this client — no connection probes");
			connectionProbesArmed = false;
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/ClientSmoke] could not arm the connection probes", t);
			connectionProbesArmed = false;
		}
	}

	private static Object invoke(Object target, String method) {
		try {
			return target.getClass().getMethod(method).invoke(target);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Whether the joined connection's command tree has the probe command. */
	private static void reportClientCommands(Object minecraft) {
		try {
			Object connection = invoke(minecraft, "getConnection");
			Object root = invoke(invoke(connection, "getCommands"), "getRoot");
			java.lang.reflect.Method child = root.getClass().getMethod("getChild", String.class);
			ForbricLog.info("[Forbric/ClientSmoke] client command tree after joining: neo=%s",
					child.invoke(root, "forbricsmokeneo") != null);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/ClientSmoke] could not read the client command tree", t);
		}
	}

	/**
	 * Opens vanilla's key binds screen the way the options menu does and reports what the game shows. A NeoForge mod
	 * that swaps screens on {@code ScreenEvent.Opening} — Controlling replaces this one with its own — is how the
	 * answer differs from what was asked for, so the report is that mod's own behaviour, not a counter of ours.
	 */
	private static void keyBindsScreenIfDue(Object minecraft) {
		int due = Integer.getInteger(KEY_BINDS_SCREEN, 0);
		if (due <= 0 || keyBindsTried || worldTicks < due) return;
		keyBindsTried = true;
		try {
			ClassLoader cl = minecraft.getClass().getClassLoader();
			Class<?> screenType = Class.forName("net.minecraft.client.gui.screens.Screen", false, cl);
			Class<?> optionsType = Class.forName("net.minecraft.client.Options", false, cl);
