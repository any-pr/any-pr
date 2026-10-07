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

package net.forbric.kernel.mixin;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.json.JsonFormat;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import net.forbric.api.ModCatalog;
import net.forbric.api.CompatibilityFinding;
import net.forbric.kernel.boot.ArbitratedAwayClasses;
import net.forbric.kernel.util.ByteScan;
import net.forbric.kernel.util.ForbricLog;

/**
 * Derives, per mixin config, which of its guest mixins must not apply to the merged base — the general form of
 * {@link MergedBaseMixinCompat#SUPPRESSED_MIXINS}'s hand-written entries.
 *
 * <p>A guest Fabric mixin is written against VANILLA bytecode. In the merged base, NeoForge may have
 * restructured the class the mixin targets — a field the mixin {@code @Shadow}s is
 * never assigned, an {@code @Inject} anchor moved, a param was re-typed. Generic erasure lets many such mixins APPLY
 * with no error and then misbehave at runtime (the archetype: {@code fabric-rendering-v1}'s {@code GuiRendererMixin}
 * reads {@code GuiRenderer.pictureInPictureRenderers}, which the NeoForge-won merge never assigns → NPE). Whether a
 * mixin applies cleanly is therefore not a usable signal.
 *
 * <p><b>Provenance is not a usable signal either.</b> This used to drop every mixin whose target matched a
 * hand-curated owned-class/package table. That was wrong at the root: the merged base IS NeoForge's patched
 * Minecraft ({@code MergedBaseBuilder} builds it from NeoForge's patched jar), so "NeoForge owns this class" describes
 * ~93% of the jar. Measured over the 163
 * suppressions that rule actually made, 108 targeted a class byte-identical to NeoForge's own jar, and restoring
 * them costs nothing. The table also could not see the failures that matter: fabric-block-api-v1 redirects
 * {@code BlockState.isAir()} inside {@code LevelChunkSection.setBlockState}, which the merged base calls as
 * {@code isEmpty()} — silently dead, on a class the table never listed.
 *
 * <p>So the question is asked directly instead: {@link MixinFit} resolves every anchor the mixin names — each
 * {@code @Shadow} member, each injector's target method, each {@code @At(target=…)} — against the merged target's
 * real bytecode, and reports whether they still exist. See {@link MixinFit.Result#shouldSuppress()} for why a
 * partially-resolving mixin is kept by default rather than dropped.
 *
 * <p>Two exemptions survive. PURE accessor/invoker mixins are always kept: they inject no behaviour, and other code
 * casts the target to the {@code @Accessor} interface they contribute. And whenever a mixin IS dropped, every mixin
 * depending on an interface it contributed is dropped with it — see {@link MixinFit#contributedInterfaces} — because
 * a lone drop converts the mod's {@code (Bar) foo} casts into {@code ClassCastException}s.
 *
 * <p>This runs at the point {@link ForbricMixinService} rewrites a config's JSON, so it needs no separate mod-jar
 * inventory: the config names its mixin package, and each mixin class is a game resource resolvable through the same
 * loader. Hand entries in {@link MergedBaseMixinCompat} stay authoritative for cases this cannot see (a runtime
 * break with no owned target); this removes the need to hand-list the owned-target ones.
 */
public final class KernelGuestMixinAdapter {

	/**
	 * Guest mixins kept although only some of their handlers bound, this boot.
	 *
	 * <p>Each one is already logged on its own line, and a 3-mod gate produces ninety of them on the client and
	 * thirty on the server — every boot, under a gate that reports green. Individually they are informational;
	 * as a number they are the thing {@code MixinFit}'s own javadoc calls worse than either extreme, because the
	 * mod keeps the handlers that bound and silently loses the rest. Ninety lines nobody totals is not a
	 * measurement, so this is the total.
	 *
	 * <p>Counted, not asserted on here: what a healthy number is depends on the mod set. The gate asserts.
	 */
	private static final java.util.Set<String> PARTIAL =
			java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

	/**
	 * The counted ones that still carry an injector Mixin rejects outright: only with
	 * {@code -Dforbric.guestInjectorPruner.refused=off}, which keeps them as they were. Mixin fails each of them, so the
	 * summary must not say "no error" of them.
	 */
	private static final java.util.Set<String> REJECTING =
			java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

	/** Records one, keyed so the same mixin evaluated twice counts once. */
	static void notePartial(String configName, String mixin) {
		if (configName == null || mixin == null) return;
		PARTIAL.add(configName + ":" + mixin);
	}

	/** Records one that keeps an injector Mixin rejects outright. */
	private static void notePartial(String configName, String mixin, MixinFit.Result kept) {
		notePartial(configName, mixin);
		if (configName != null && mixin != null && !kept.rejected().isEmpty()) REJECTING.add(configName + ":" + mixin);
	}

	/** Every guest mixin that applied only partially so far, sorted. */
	public static java.util.List<String> partiallyApplied() {
		synchronized (PARTIAL) {
			return PARTIAL.stream().sorted().toList();
		}
	}

	/** The one line a gate greps: the count, and what it costs. */
	public static String partialSummary() {
		int rejecting = REJECTING.size();
		String head = "[Forbric/Mixin] " + PARTIAL.size() + " guest mixin(s) apply only partially on the merged base — ";
		if (rejecting == 0) return head + "each keeps the handlers that bound and loses the rest, with no error at either end";
		return head + (PARTIAL.size() - rejecting) + " keep the handlers that bound and lose the rest, with no error at either "
				+ "end; " + rejecting + " keep an injector Mixin rejects outright (-D"
				+ net.forbric.kernel.transform.GuestInjectorPruner.REFUSED_PROPERTY + "=off), which fails that mixin when it is applied";
	}

	private static final String ACCESSOR_DESC = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
	private static final String INVOKER_DESC = "Lorg/spongepowered/asm/mixin/gen/Invoker;";

	/** {@code -Dforbric.guestMixinAdapter=off} turns the derived scan off (leaving only the hand list). */
	static final String PROPERTY = "forbric.guestMixinAdapter";

	public static boolean enabled() {
		return !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "on"));
	}

	private KernelGuestMixinAdapter() {
	}

	/**
	 * The mixin entries in {@code configJson} (as they appear in its {@code mixins}/{@code client}/{@code server}
	 * arrays) that no longer fit the merged base, plus anything transitively broken by dropping them.
	 * {@code resource} resolves a resource path ({@code some/pkg/Name.class}) to its bytes, or null. Best-effort:
	 * any parse/scan failure on one entry skips that entry, never the config.
	 */
	public static List<String> unfitMixins(String configName, byte[] configJson, Function<String, byte[]> resource) {
		return unfitMixins(configName, configJson, resource, null);
	}

	/** {@code -Dforbric.mixinFitSided=off} judges every array on both sides again, as before. */
	static final String SIDED_PROPERTY = "forbric.mixinFitSided";

	/**
	 * The entries Mixin will actually prepare on {@code side}: {@code mixins}, plus {@code client} on the client or
	 * {@code server} on a dedicated server ({@code MixinConfig.prepare} switches on the environment side and never
	 * looks at the other array). {@code null} — no side known — is every array.
	 *
	 * <p>Judging the other side's array was not merely noise. A client-only mod's client mixin, unfit on the merged
	 * game, was recorded as a CONFIRMED required loss on a dedicated server, where Mixin would never have applied
	 * it, and the default STRICT policy stopped the server: fusion (connected textures) in a server's mods folder
	 * was enough. Same rule as {@code CandidateContractScanner}'s.
	 */
	static LinkedHashSet<String> appliedEntries(UnmodifiableConfig config, net.fabricmc.api.EnvType side) {
		LinkedHashSet<String> mixins = new LinkedHashSet<>();
		addMixinEntries(config.get(List.of("mixins")), mixins);
		boolean sided = side != null && !"off".equalsIgnoreCase(System.getProperty(SIDED_PROPERTY, "on"));
		if (!sided || side == net.fabricmc.api.EnvType.CLIENT) addMixinEntries(config.get(List.of("client")), mixins);
		if (!sided || side == net.fabricmc.api.EnvType.SERVER) addMixinEntries(config.get(List.of("server")), mixins);
		return mixins;
	}

	/**
	 * {@link #unfitMixins(String, byte[], Function)} for the entries Mixin prepares on {@code side} only; an entry of
	 * the other side's array is neither judged nor reported — nothing is lost where it never runs.
	 */
	public static List<String> unfitMixins(String configName, byte[] configJson, Function<String, byte[]> resource,
			net.fabricmc.api.EnvType side) {
		return unfitMixins(configName, configJson, resource, side, null, null);
	}

	/**
	 * As above, with {@code raw} serving the merged base's bytes BEFORE the transform chain and {@code base} naming
	 * the members digest of the jar that serves a class, which is what lets an injector target the owning mod's own
	 * platform lacks too be told from one the merge lost ({@link NativeAbsentTargets}). The platform is the config's
	 * owner's ecosystem ({@link MixinConfigOwners#ecosystemOf}), and the owner's declared requirements are what discovery
	 * read from its manifest ({@link net.forbric.api.ModPresence#metadata}), which decide whether it is native to the
	 * game the table describes at all. A config no single mod claims, a null {@code raw} or a null {@code base} asks
	 * nothing, and every such target is a miss as before.
	 */
	public static List<String> unfitMixins(String configName, byte[] configJson, Function<String, byte[]> resource,
			net.fabricmc.api.EnvType side, Function<String, byte[]> raw, Function<String, String> base) {
		if (!enabled()) return List.of();

		UnmodifiableConfig config;
		try (Reader reader = new InputStreamReader(new ByteArrayInputStream(configJson), StandardCharsets.UTF_8)) {
			config = JsonFormat.fancyInstance().createParser().parse(reader);
		} catch (RuntimeException | java.io.IOException notAMixinConfig) {
			return List.of();
		}

		String pkg = asString(config.get(List.of("package")));
		if (pkg == null || pkg.isEmpty()) return List.of();

		LinkedHashSet<String> mixins = appliedEntries(config, side);
		if (mixins.isEmpty()) return List.of();

		String pkgPath = pkg.replace('.', '/');
		// A config that declares a plugin can have switched a mixin off itself, and the plugin is never asked
		// about an entry this method removes. PluginDeclinedMixins holds the attribution back to ask it later.
		String pluginClass = asString(config.get(List.of("plugin")));
		boolean required = Boolean.TRUE.equals(config.get(List.of("required")));
		// The native game is the owning mod's own: vanilla for a Fabric mod, its patched game for a MinecraftForge or
		// NeoForge one, which declares methods vanilla does not and the merge may have lost.
		net.forbric.api.Ecosystem platform = MixinConfigOwners.ecosystemOf(configName);
		NativeAbsentTargets.Context nativeView = raw == null || base == null || platform == null
				? NativeAbsentTargets.Context.NONE
				: new NativeAbsentTargets.Context(raw, declaredDefaultRequire(config), platform, base,
						declaringMod(configName, platform));
		Map<String, byte[]> loaded = new LinkedHashMap<>();
		List<String> suppress = new ArrayList<>();

		net.forbric.api.Ecosystem ecosystem = MixinConfigOwners.ecosystemOf(configName);
		// The mixins the kernel leaves out by name (MergedBaseMixinCompat's hand list, -Dforbric.suppressMixins) never reach
		// Mixin, and reportNamedSuppressions has their row: a verdict line about one, or a place in the PARTIAL count, would
		// describe a mixin that is not there (fabric-loot-api's ReloadableServerRegistriesMixin read PARTIAL, then suppressed).
		List<String> named = ForbricMixinService.suppressedMixinsFor(configName);
		Object defaultRequire = config.get(List.of("injectors", "defaultRequire"));
		int configMinimum = defaultRequire instanceof Number n ? Math.max(0, n.intValue()) : 0;
		for (String mixin : mixins) {
			// Which family's mod wrote it decides what shape it was compiled against (MixinStubRebind).
			MixinStubRebind.noteEcosystem(pkgPath + "/" + mixin.replace('.', '/'), ecosystem, configName);
			byte[] classBytes = resource.apply(pkgPath + "/" + mixin.replace('.', '/') + ".class");
			if (classBytes == null) continue;
			loaded.put(mixin, classBytes);
			if (named.contains(mixin)) continue;
			try {
				if (reportTargetsArbitratedAway(configName, pkg, mixin, pluginClass, classBytes)) continue;
				if (isPureAccessorMixin(classBytes)) {
					// Never suppressed (the cast to its generated interface must keep working), but a member it
					// cannot bind is worth a line here: Mixin's own report is an InvalidAccessorException naming a
					// descriptor and nothing about which mod or why.
					MixinFit.Result accessors = MixinFit.evaluate(classBytes, resource,
							net.forbric.kernel.classloading.DelegationPolicy::alwaysGame);
					if (!accessors.unresolved().isEmpty()) {
						ForbricLog.info("[Forbric/Mixin] guest accessor mixin %s:%s cannot bind — %s (kept; the merge "
								+ "re-typed or removed the member, so the generated accessor will throw when called)",
								MixinConfigOwners.describe(configName), mixin, String.join(", ", accessors.unresolved()));
					}
					continue;
				}
				if (isExplicitlyKept(configName, mixin)) continue;

				// What the mixins Mixin applies first add to the same targets: a @Shadow of one of those members binds,
				// on Fabric and here (moreculling's shadow of the mesh field fabric-renderer-api adds).
				MixinAddedMembers.View added = MixinAddedMembers.before(configName, mixin, resource);
				// Judged as Mixin will receive it: Carpet's anchor adapters run when Mixin loads the class, after this
				// read, so an anchor they move onto the merged game is not missing (CarpetMixinAdapter.asLoaded).
				byte[] judged = ReplacedCallRedirects.asLoaded(CarpetMixinAdapter.asLoaded(classBytes, resource), resource);
				MixinFit.Result fit = MixinFit.evaluate(judged, resource,
						net.forbric.kernel.classloading.DelegationPolicy::alwaysGame, added, nativeView);
				if (judged != classBytes) {
					MixinFit.Result unadapted = MixinFit.evaluate(classBytes, resource,
							net.forbric.kernel.classloading.DelegationPolicy::alwaysGame, added, nativeView);
					if (unadapted.verdict() != fit.verdict() || unadapted.unresolved().size() != fit.unresolved().size()) {
						ForbricLog.info("[Forbric/Mixin] guest mixin %s:%s is judged as its anchor adapter hands it to Mixin — "
								+ "verdict %s→%s (%s)", MixinConfigOwners.describe(configName), mixin, unadapted.verdict(),
								fit.verdict(), fit.reason());
					}
				}
				if (!fit.nativeAbsent().isEmpty()) {
					// Informational, and deliberately nothing more: no finding, no mark on the mod's row. Native Mixin
					// drops this injector on the mod's own platform without a word, so it is no loss of the merge's --
					// and Mixin will drop it here the same way, the rest of the mixin applying around it.
					ForbricLog.info("[Forbric/Mixin] guest mixin %s:%s names %s, which %s lacks too; native Mixin drops "
							+ "that injector without a word (nothing requires it to inject), and so will this boot — not a "
							+ "merged-base loss", MixinConfigOwners.describe(configName), mixin,
							String.join(", ", fit.nativeAbsent()), NativeAbsentTargets.describe(platform));
				}
				if (!fit.shouldSuppress()) {
					// An injector Mixin rejects outright fails this mixin whichever class its other misses are on, so one kept
					// for a miss on another mod's class answers it first, as a PARTIAL one does below; what is left is judged
					// as it stands.
					MixinFit.Result shown = fit;
					if (!fit.foreign().isEmpty()) {
						shown = answerRejections(configName, pkg, mixin, pluginClass, classBytes, required, judged, fit, resource,
								added, configMinimum, false, nativeView);
						if (shown == null) {
							suppress.add(mixin);
							continue;
						}
					}
					if (!shown.foreign().isEmpty()) {
						// A DIFFERENT thing from the line below, and the reason the two are separated. An anchor
