/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.runtime;

import java.util.List;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.forbric.api.CompatibilityFinding;
import net.forbric.api.ModCatalog;
import net.forbric.kernel.ui.DialogLang;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

/**
 * Native Minecraft confirmation so late findings never fork Swing or exit the JVM -- and the launch's own question when
 * no window could ask it, in that window's words: "Launch anyway" or "Quit".
 */
final class KernelCompatibilityScreen extends ConfirmScreen {
	private final BooleanConsumer answer;

	/**
	 * @param more      how many further findings the next prompt will show; this screen's answer covers none of them
	 * @param forLaunch the launch's question, whose refusal quits the game rather than returning to the title
	 */
	KernelCompatibilityScreen(List<CompatibilityFinding> findings, int more, boolean forLaunch, BooleanConsumer answer) {
		super(answer, Component.literal(DialogLang.ofSystem().get("compat.title")), message(findings, more, forLaunch),
				Component.literal(DialogLang.ofSystem().get(forLaunch ? "button.continue" : "compat.continuePlaying")),
				Component.literal(DialogLang.ofSystem().get(forLaunch ? "button.quit" : "compat.returnTitle")));
		this.answer = answer;
	}

	@Override public void onClose() { answer.accept(false); }
	@Override protected void init() {
		super.init();
		setInitialFocus(noButton);
	}

	static String text(List<CompatibilityFinding> findings, int more) {
		return text(findings, more, false);
	}

	/** Every finding the answer covers is named; the caller pages anything beyond what fits. */
	static String text(List<CompatibilityFinding> findings, int more, boolean forLaunch) {
		DialogLang lang = DialogLang.ofSystem();
		StringBuilder text = new StringBuilder(lang.get("compat.intro")).append("\n\n");
		for (CompatibilityFinding finding : findings) {
			String name = ModCatalog.everything().stream().filter(e -> e.modId().equals(finding.modId()))
					.map(ModCatalog.Entry::name).findFirst().orElse(finding.modId());
			text.append(name).append(": ").append(finding.feature()).append('\n');
		}
		if (more > 0) text.append(lang.get("compat.more", more)).append('\n');
		text.append('\n').append(lang.get("compat.reportDetails"));
		if (forLaunch) text.append('\n').append(lang.get("compat.note"));
		return text.toString();
	}

	private static Component message(List<CompatibilityFinding> findings, int more, boolean forLaunch) {
		return Component.literal(text(findings, more, forLaunch));
	}
}
