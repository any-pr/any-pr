package net.fabricmc.fabric.api.client.screen.v1;

import java.util.IdentityHashMap;
import java.util.Map;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Hand-written stand-in for fabric-screen-api's per-screen draw events. */
public final class ScreenEvents {
	private static final Map<Screen, Event<BeforeExtract>> BEFORE = new IdentityHashMap<>();
	private static final Map<Screen, Event<AfterExtract>> AFTER = new IdentityHashMap<>();

	private ScreenEvents() {
	}

	public static Event<BeforeExtract> beforeExtract(Screen screen) {
		return BEFORE.computeIfAbsent(screen, s -> new Event<>(listeners -> (sc, g, x, y, t) ->
				listeners.forEach(listener -> listener.beforeExtract(sc, g, x, y, t))));
	}

	public static Event<AfterExtract> afterExtract(Screen screen) {
		return AFTER.computeIfAbsent(screen, s -> new Event<>(listeners -> (sc, g, x, y, t) ->
				listeners.forEach(listener -> listener.afterExtract(sc, g, x, y, t))));
	}

	public interface BeforeExtract {
		void beforeExtract(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);
	}

	public interface AfterExtract {
		void afterExtract(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);
	}
}
