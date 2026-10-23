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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The words of {@link DependencyDialogMain}, in the player's own language.
 *
 * <h2>Why the strings are Java and not a resource bundle</h2>
 *
 * <p>Because of how the dialog child is launched. {@link DependencyDialog} runs it with
 * {@code -cp <the code source of DependencyDialog>} — the ONE entry the child needs. In a shipped build that code
 * source is {@code forbric-kernel.jar}, which carries classes and resources together, and a bundle would be
 * found. Under Gradle it is {@code build/classes/java/main}, and processed resources live in the sibling
 * {@code build/resources/main} — a directory that is NOT on the child's classpath. A {@code .properties} table
 * would therefore load in production and silently fall back to English in the one test that drives the real fork,
 * which is the test that exists to prove the child works. Compiled constants cannot have that gap: if the class
 * loads at all, its strings are there.
 *
 * <h2>Fallback</h2>
 *
 * <p>English is the base and every other table is checked against it — {@link #missingKeys()} is what a test
 * asserts on, so a key added here in English and forgotten in Japanese is a failing build rather than an English
 * sentence in the middle of a Japanese dialog. At runtime a missing key still falls back to English, because a
 * dialog that throws is a dialog the player never sees.
 *
 * <h2>Substitution</h2>
 *
 * <p>{@code {0}}…{@code {9}}, substituted by {@link #get}. Deliberately NOT {@code MessageFormat}: its patterns
 * treat {@code '} as an escape, so "Biomes O' Plenty" in a French or English pattern would quietly eat the rest
 * of the sentence. Nothing here needs plural or number formatting that would justify that hazard.
 */
public final class DialogLang {
	/** {@code -Dforbric.dialogLanguage=ja} forces one, for a developer or a gate. Unset: the system language. */
	public static final String SWITCH = "forbric.dialogLanguage";

	private final String tag;
	private final Map<String, String> strings;

	private DialogLang(String tag, Map<String, String> strings) {
		this.tag = tag;
		this.strings = strings;
	}

	/** The language tag this table is filed under, e.g. {@code zh_cn}. */
	public String tag() {
		return tag;
	}

	/**
	 * The string for {@code key}, with {@code {0}}… replaced by {@code args}.
	 *
	 * <p>Falls back to English, then to the key itself. A dialog whose job is to explain a problem must not be
	 * able to become a second problem.
	 */
	public String get(String key, Object... args) {
		String raw = strings.get(key);
		if (raw == null) raw = EN.strings.get(key);
		if (raw == null) return key;
		return substitute(raw, args);
	}

	/**
	 * One pass over the pattern, never over what an argument put there.
	 *
	 * <p>The obvious loop — {@code replace("{0}", a).replace("{1}", b)} — rescans the string it has already
	 * rewritten, so an argument whose own text contains a later placeholder has that placeholder filled in.
	 * Mod display names come out of a third party's manifest and are not ours to trust: a mod calling itself
	 * "Cool {3} Mod" would be shown to the player under a name no jar in their folder carries, which is the one
	 * identifier this dialog exists to hand them. Scanning the pattern once and emitting arguments as literals
	 * makes that unreachable rather than unlikely.
	 */
	/**
	 * One pass over the pattern, never over what an argument put there.
	 *
	 * <p>The obvious loop — {@code replace("{0}", a).replace("{1}", b)} — rescans the string it has already
	 * rewritten, so an argument whose own text contains a later placeholder has that placeholder filled in. Mod
	 * display names come out of a third party's manifest and are not ours to trust: a mod calling itself
	 * "Cool {3} Mod" would be shown to the player under a name no jar in their folder carries, which is the one
	 * identifier this dialog exists to hand them. Scanning the pattern once and emitting arguments as literals
	 * makes that unreachable rather than unlikely.
	 */
	static String substitute(String raw, Object... args) {
		if (args == null || args.length == 0 || raw.indexOf('{') < 0) return raw;
		StringBuilder out = new StringBuilder(raw.length() + 32);
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '{' && i + 2 < raw.length() && raw.charAt(i + 2) == '}'
					&& Character.isDigit(raw.charAt(i + 1))) {
				int index = raw.charAt(i + 1) - '0';
				if (index < args.length) {
					out.append(args[index] == null ? "?" : args[index].toString());
					i += 2;
					continue;
				}
			}
			out.append(c);
		}
		return out.toString();
	}

	/** Every table, English first. A test walks this. */
	public static List<DialogLang> all() {
		return List.of(EN, ZH_CN, ZH_TW, JA, KO, RU, DE, FR, ES, PT_BR);
	}

	/** The keys English has and this table does not. Empty is the only acceptable answer; see the class note. */
	public List<String> missingKeys() {
		List<String> missing = new java.util.ArrayList<>();
		for (String key : EN.strings.keySet()) {
			if (!strings.containsKey(key)) missing.add(key);
		}
		return missing;
	}

	/** The keys this table has that English does not — a typo in a key is otherwise invisible. */
	public List<String> strayKeys() {
		List<String> stray = new java.util.ArrayList<>();
		for (String key : strings.keySet()) {
			if (!EN.strings.containsKey(key)) stray.add(key);
		}
		return stray;
	}

	/** The raw value, with no English fallback — so a test can tell "translated" from "fell through". */
	String raw(String key) {
		return strings.get(key);
	}

	/** Every key this table declares, in declaration order. English's is the canonical set. */
	List<String> keys() {
		return List.copyOf(strings.keySet());
	}

	/**
	 * The table for this run: {@link #SWITCH} if it names one, otherwise the system language, otherwise English.
	 *
	 * <p>Read in the CHILD process. {@link DependencyDialog} forwards the property when the parent has one set,
	 * because a child JVM does not inherit its parent's {@code -D} flags — only the OS locale, which is the
	 * common case and needs no forwarding at all.
	 */
	public static DialogLang ofSystem() {
		String forced = System.getProperty(SWITCH);
		if (forced != null && !forced.isBlank()) {
			DialogLang named = byTag(forced.trim().toLowerCase(Locale.ROOT).replace('-', '_'));
			if (named != null) return named;
		}
		return of(Locale.getDefault());
	}

	/**
	 * The closest table to {@code locale}.
	 *
	 * <p>Region matters for exactly one language here — a Traditional-Chinese reader handed Simplified text is
	 * being handed the wrong language, not a dialect of their own — so {@code zh} splits on region and everything
	 * else resolves on the language alone. Portuguese resolves to the Brazilian table from any region, because
	 * one Portuguese is better than English for a Portuguese reader and pt-BR is the one that exists.
	 */
	public static DialogLang of(Locale locale) {
		if (locale == null) return EN;
		String language = locale.getLanguage().toLowerCase(Locale.ROOT);
		if ("zh".equals(language)) {
			String region = locale.getCountry() == null ? "" : locale.getCountry().toUpperCase(Locale.ROOT);
			String script = locale.getScript() == null ? "" : locale.getScript();
			if ("Hant".equalsIgnoreCase(script)) return ZH_TW;
			return switch (region) {
				case "TW", "HK", "MO" -> ZH_TW;
				default -> ZH_CN;
			};
		}
		DialogLang byLanguage = byTag(language);
		if (byLanguage != null) return byLanguage;
		return switch (language) {
			case "pt" -> PT_BR;
			default -> EN;
		};
	}

	private static DialogLang byTag(String tag) {
		for (DialogLang lang : all()) {
			if (lang.tag.equals(tag)) return lang;
		}
		// "pt" for pt_br, "zh" handled above. A bare language that prefixes exactly one table resolves to it.
		DialogLang only = null;
		for (DialogLang lang : all()) {
			if (lang.tag.startsWith(tag + "_")) {
				if (only != null) return null;
				only = lang;
			}
		}
		return only;
	}

	private static Map<String, String> table(String... pairs) {
		Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
		return map;
	}

	// ---------------------------------------------------------------------------------------------------------
	// English. The base: every other table is diffed against this one, and a key missing anywhere else falls
	// back to it rather than to nothing.
	// ---------------------------------------------------------------------------------------------------------
	public static final DialogLang EN = new DialogLang("en", table(
		"compat.continuePlaying", "Continue playing",
		"compat.returnTitle", "Return to title",
		"compat.reportDetails", "Details are available on the Mods screen and in the compatibility report.",
		"compat.title", "Required mod features are unavailable",
		"compat.intro", "Forbric confirmed that these required features cannot work in this instance:",
		"compat.note", "You can continue for this launch, or quit to change the mod set. Closing this window does not approve continuing.",
		"details.required.header", "Required features that cannot work",
		"details.suspected.header", "Possible problems, not confirmed (no decision needed)",
		"compat.more", "{0} more will be shown after this.",
			"title.deps", "Forbric — a mod is missing something it requires",
			"title.mixins", "Forbric — two mods do not fit each other",
			"title.both", "Forbric — some mods are missing requirements, and some do not fit each other",
			"title.deps.many", "Forbric — some mods are missing things they require",
			"title.mixins.many", "Forbric — some mods do not fit each other",
			"button.continue", "Launch anyway",
			"button.quit", "Quit",
			"button.details.show", "Show details",
			"button.details.hide", "Hide details",
			"summary.deps.one", "One mod is missing something it requires:",
			"summary.deps.many", "{0} mods are missing something they require:",
			"summary.mixins.one", "One mod could not attach to another mod it was built for:",
			"summary.mixins.many", "{0} mods could not attach to other mods they were built for:",
			"summary.more", "…and {0} more. The full list is in the details.",
			"bullet.absent", "{0} needs {1}, which is not installed",
			"bullet.version", "{0} needs {1} {2}, and you have {3}",
			"bullet.mixin", "{0} could not attach to the mod it was built for",
			"fix.header", "What might fix it:",
			"fix.install", "Install {0}. {1} is a {2} mod, so the {2} build is the safest one to get — on Forbric "
					+ "a build for another loader can satisfy it too.",
			"fix.version", "Change {0} to a version that matches {1}. You have {2}.",
			"fix.mixin", "Both mods are installed and neither is missing anything — only their builds do not "
					+ "match. A version of {0} released around the same time as the mod it attaches to may fix it.",
			"fix.remove", "Or take {0} out of your mods folder. Forbric keeps loading everything else, so the "
					+ "rest of your mods still work.",
			"note.deps", "Forbric will launch anyway if you ask it to. A mod whose requirement is unmet usually "
					+ "fails much later, in an error that names neither mod — an empty world, a missing block, or "
					+ "a crash while creating a world — so it is worth fixing before you play.",
			"note.mixins", "Nothing reports this as a missing dependency, because it is not one: both mods are "
					+ "installed and each is inside the version range the other asks for. The two builds simply "
					+ "do not fit.",
			"details.deps.header", "Unmet requirements",
			"details.mixins.header", "Mods that could not attach",
			"details.by", "{0}  ({1}, {2})",
			"details.needs.absent", "needs {0} {1}  —  NOT INSTALLED",
			"details.needs.version", "needs {0} {1}  —  installed: {2}",
			"details.search", "search: {0}",
			"details.mixin", "{0}  —  {1}",
			"details.anchors", "could not find {0}",
			"isolation.title", "Forbric — the game crashed last time",
			"isolation.intro", "The game crashed the last time it ran, and the crash points at these mods:",
			"isolation.intro.clash", "The game crashed the last time it ran because these mods clash with {0}. Forbric "
					+ "can keep {0} and start without:",
			"isolation.bullet", "{0}  ({1})",
			"isolation.without", "Starting without them writes their file names into forbric-disabled.txt, next to "
					+ "your mods folder, and Forbric does not load them. The files stay where they are. To turn one back on, "
					+ "delete its line from forbric-disabled.txt.",
			"isolation.note", "This is a guess: the crash points at these mods, which does not prove they are at "
					+ "fault. Closing this window starts the game with every mod, as before.",
			"isolation.details.report", "The crash report is crash-reports/{0}; the analysis is "
					+ ".forbric-kernel/crash-analysis.txt.",
			"button.isolation.without", "Start without {0}",
			"button.isolation.everything", "Start with everything",
			"details.log", "The same findings are in logs/latest.log, under [Forbric/Deps]."));

	// ---------------------------------------------------------------------------------------------------------
	// Simplified Chinese.
	// ---------------------------------------------------------------------------------------------------------
	public static final DialogLang ZH_CN = new DialogLang("zh_cn", table(
		"compat.continuePlaying", "继续游戏",
		"compat.returnTitle", "返回标题界面",
		"compat.reportDetails", "详细原因见 Mods 界面和兼容性报告。",
		"compat.title", "部分 mod 的必要功能无法运行",
		"compat.intro", "Forbric 已确认以下必要功能在当前实例中无法运行：",
		"compat.note", "你可以选择本次继续启动，或退出后调整 mod。关闭此窗口不会视为同意继续。",
		"details.required.header", "无法运行的必要功能",
		"details.suspected.header", "可能的问题（未确认，不需要你做选择）",
		"compat.more", "之后还会显示另外 {0} 项。",
			"title.deps", "Forbric —— 有 mod 缺少它需要的前置",
