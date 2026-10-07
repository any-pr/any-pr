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

package net.forbric.api;

import java.util.Locale;

/**
 * The mod ecosystems Forbric runs side by side, as ONE name.
 *
 * <p>This is the first type of the unified Forbric API: the vocabulary the kernel's own services and the
 * compatibility layers all speak. It exists because the kernel had grown <em>several</em> different answers to
 * "which ecosystem is this" — {@code metadata.ModEcosystem}, {@code discovery.ModAnnotationScanner.Family} and
 * {@code classloading.LoaderProbePolicy.Family} among them — with a hand-written translator between two of them.
 * Several vocabularies is how a hub decays back into pairwise accommodation: code that cannot NAME the other
 * ecosystem uniformly ends up branching on its class names instead.
 *
 * <h2>Why the constants are spelled this way</h2>
 *
 * <p>{@code boot.Main} writes {@link #name()} straight into the {@code --scan} report, and {@code run/diff-oracle.sh}
 * pins {@code "FABRIC"}/{@code "NEOFORGE"} in a python ground-truth extractor that is deliberately INDEPENDENT of
 * kernel code. Renaming the constant to match prose would have meant editing the thing whose whole value is that
 * it was written separately. So the prose name lives in {@link #displayName()} instead. Traditional MinecraftForge
 * is no longer a supported ecosystem: a jar whose only manifest is {@code META-INF/mods.toml} is reported as
 * unsupported and skipped.
 */
public enum Ecosystem {
	FABRIC("Fabric"),
	NEOFORGE("NeoForge");

	private final String displayName;

	Ecosystem(String displayName) {
		this.displayName = displayName;
	}

	/** How this ecosystem is written in prose and in user-facing log lines. */
	public String displayName() {
		return displayName;
	}

	/**
	 * Whether this is the FML-descended ecosystem.
	 *
	 * <p>NeoForge descends from FML: it has {@code @Mod}, a mod container, an event bus and a {@code ModList}, and a
	 * great deal of kernel code is correct for it and for neither of the other.
	 */
	public boolean isForgeFamily() {
		return this == NEOFORGE;
	}

	/** The lowercase id used in manifests and in the {@code forbric:ecosystem} custom value. */
	public String familyId() {
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * The spelling this ecosystem is written as in user-facing config.
	 */
	public String configId() {
		return familyId();
	}

	/**
	 * Parses a user- or manifest-supplied ecosystem name, or returns {@code null} if it names none of them.
	 *
	 * <p>Lenient on purpose: {@code "neoforge"} and {@code "fabric"} are the names a person reaches for. A name
	 * that names neither — including {@code "minecraftforge"}, which names an ecosystem this loader no longer
	 * runs — returns null and the caller reports it as unknown.
	 */
	public static Ecosystem parse(String raw) {
		if (raw == null) return null;
		String s = raw.trim().toLowerCase(Locale.ROOT);

		return switch (s) {
			case "fabric" -> FABRIC;
			case "neoforge" -> NEOFORGE;
			default -> null;
		};
	}
}
