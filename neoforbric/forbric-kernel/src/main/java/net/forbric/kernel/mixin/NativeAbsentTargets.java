/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import net.forbric.api.DiscoveredMod;
import net.forbric.api.Ecosystem;
import net.forbric.api.UnifiedDependency;
import net.forbric.api.VersionPredicate;
import net.forbric.kernel.util.ForbricLog;

/**
 * Injector targets the mod's own platform lacks as well as the merged base: the misses native Mixin drops without a
 * word, and which are therefore nothing the merge cost the mod.
 *
 * <p>Not Enough Crashes 4.4.9 for 26.2 is the worked case. Its {@code MixinTileEntity} injects into
 * {@code BlockEntity.populateCrashReport} — the Yarn name of what 26.2 calls {@code fillCrashReportCategory}, in a
 * mod built from Yarn sources for an unobfuscated game. Neither vanilla nor the merged base has that method.
 * {@link MixinFit} found no anchor, called the mixin UNFIT, and the adapter left it out as a CONFIRMED loss the
 * config's {@code required: true} made required — so the default STRICT policy stopped a server that native Fabric
 * Loader 0.19.5 starts with the same jar.
 *
 * <h2>What native Mixin does with an injector whose selector matches nothing</h2>
 *
 * <p>Read from sponge-mixin 0.17.3 (the kernel's, and native NeoForge 26.2.0.88's) and 0.17.4 (Fabric Loader 0.19.5's),
 * which agree here. {@code InjectionInfo.parseRequirements}
 * takes the injector's own {@code require} when it is 0 or more, else — for an injector in no {@code @Group} — the
 * config's {@code injectors.defaultRequire}, which is 0 unless the config says otherwise.
 * {@code TargetSelectors.validate} then throws only when that count is above 0 ("Critical injection failure: … could
 * not find any targets matching …"), or when {@code mixin.debug.countInjections} (or {@code mixin.debug}) is on and
 * {@code expect}, default 1, is above 0. Otherwise nothing is said: {@code InjectionInfo.isValid()} is false,
 * {@code MixinTargetContext.prepareInjections} skips the injector, and the rest of the mixin — its fields, their
 * initializers, its other methods, its interfaces, the handler itself — is merged as usual. A {@code @Group} counts
 * its members together and needs at least one injection, so a grouped injector is never silently empty. The config's
 * own {@code required} flag only decides whether one of those errors is fatal; it makes no injector mandatory.
 * Measured on native Fabric 0.19.5 with Not Enough Crashes alone and {@code -Dmixin.debug.export=true}: the server
 * reaches Done, Mixin logs nothing about the injector, and the woven {@code BlockEntity} carries {@code noNBT}, its
 * constructor initializer, the lambda and the merged handler, with no call to the handler anywhere.
 *
 * <p>Only a selector that is a plain method name, optionally with a descriptor and with the target itself as its
 * owner, is a name lookup that silently finds nothing ({@link #plainSelector}). The rest of the grammar means
 * something else: a leading {@code @} is a registered dynamic selector (MixinSquared's {@code @MixinSquared:Handler}
 * resolves to another mixin's merged handler), a trailing {@code +} or {@code {n,}} is a quantifier whose minimum
 * {@code TargetSelectors.findRootTargets} enforces whatever {@code require} says, a dotted name splits into an owner
 * and a name, and a descriptor that is not one fails validation. Those are never answered here.
 *
 * <h2>Whose native: the mod's own platform</h2>
 *
 * <p>A Fabric mod's native game is vanilla 26.2. A NeoForge mod's is its own platform's patched
 * game, which declares methods vanilla does not — NeoForge's {@code EnderDragon.getParts()} — and some of those the
 * merge dropped or retyped. For such a mod "vanilla
 * lacks it too" proves nothing: natively the injector applies, and here it cannot, which is the merge's loss. So the
 * question is asked of the owning mod's platform ({@link MixinConfigOwners#ecosystemOf}), and a config no single mod
 * claims is not asked at all. The patched jars are the whole of what those platforms declare in vanilla's packages:
 * neither runtime ships a class there, and their own transformers add no method there (NeoForge 26.2.0.88's coremods
 * jar holds
 * {@code MethodRedirector}, {@code ReplaceFieldWithGetterAccess} and {@code ReplaceFieldComparisonWithInstanceOf},
 * which rewrite instructions only).
 *
 * <h2>Which misses the platform shares</h2>
 *
 * <p>The kernel sees only the merged base at run time, and neither platform jar is shipped with it. What is
 * shipped is the difference: {@value #TABLE}, which NativeOnlyMethodsCensusTest re-derives from vanilla 26.2's own jar,
 * the staged NeoForge patched jar and the staged merged base, and pins. Measured, every class the
 * two declare in vanilla's packages is in the merged base, and only 565 of vanilla's methods and 14 of
 * NeoForge's are not (most of them lambdas and anonymous classes the merge renumbered), so a
 * platform's declared methods are the merged class's own minus what the merge added, plus that platform's rows for
 * that class. A method the merged class does not declare and no row of the platform names is a method the platform
 * does not declare either.
 *
 * <p>"The merged class's own" means the merged base's RAW bytes, before the kernel's transform chain: the chain
 * removes some methods (orphaned lambda twins, interface-default shadowing stubs), and a method only the chain took
 * away may well be the platform's — the census never saw that removal. So a method the raw class still declares is
 * never called absent; the miss stays the merge's, as before.
 *
 * <p>The rows hold only for the merged base they were derived from, and a base that lost a method the staged one kept
 * would have no row for it — the direction that hides a loss. So the table carries that base's members digest
 * ({@link #membersDigest}: every class in vanilla's packages with its methods' names and descriptors, bodies left out,
 * so a rebuild that changes only code still matches), and nothing is answered for a class served by a jar whose digest
 * differs, or by no jar the kernel can read; such a miss counts as the merge's, as before, and one warning names the
 * mismatch.
 *
 * <p>Only classes in vanilla's own packages are asked, and only those the raw view can see and the table does not list
 * as the merged base's alone for that platform (a class the platform lacks fails natively for a different reason:
 * Mixin cannot find the target and skips the whole mixin). Another mod's class is never asked: {@link MixinFit} judges
 * those as foreign.
 *
 * <h2>Minecraft's own libraries</h2>
 *
 * <p>{@code com/mojang/} is also where Minecraft's libraries live — brigadier, DataFixerUpper, authlib and five more —
 * and the kernel loads those from their own jars, beside the merged base, so a mod can mixin into them as it can
 * natively. Their raw bytes are what every platform loads: vanilla 26.2 lists them in its version JSON, and
 * NeoForge 26.2.0.88's launcher profile inherits that list and adds no
 * {@code com.mojang} library (measured on their installers), and no platform jar or merged base ships one of their
 * classes. So the table also records each of those jars' members digest ({@code library} lines, re-derived from the
 * version JSON), and a class served by one of exactly those jars is answered from its raw bytes, as the merged base's
 * classes are. A library jar of another version, or a mod's own copy, has another digest and is not answered for.
 *
 * <h2>Whose native: the version the mod asks for</h2>
 *
 * <p>The rows describe one game per platform: vanilla 26.2 and NeoForge
 * 26.2.0.88's, which the table's {@code platform} lines record ({@code minecraft=}, {@code neoforge=}).
 * A mod whose mandatory {@code minecraft} range, or {@code neoforge} range for its platform, excludes that
 * version is not native to that game: its own loader would refuse it there, and the newer game it was built for may
 * well declare the method this one lacks. Such a mod is not answered for — the miss is the merge's, as before — and
 * one line names the requirement. Nor is a mod whose declared requirements the kernel cannot see (the config's owner
 * has no metadata in {@link net.forbric.api.ModPresence}), or whose range cannot be read: being asked is the claim that
 * would hide a loss, so it needs a requirement that is shown to admit the table's game.
 *
 * <p>What this changes is only the judgement. Such an injector is neither a resolved nor a missing anchor; the mixin
 * is judged on the rest, and when nothing else is missing it goes to Mixin whole, which drops the injector exactly as
 * native does. {@link FinalMixinApplications} then reads the injector's original minimum, 0, and finds nothing to
 * report. {@code -Dforbric.mixinFit.nativeAbsent=off} counts every such target as a miss again, as before.
 */
public final class NativeAbsentTargets {
	/** The shipped table; NativeOnlyMethodsCensusTest pins it to the staged jars. */
	static final String TABLE = "/net/forbric/kernel/mixin/native-only-methods.txt";

	/** {@code -Dforbric.mixinFit.nativeAbsent=off}: a target the platform lacks too is a missing anchor again. */
	static final String PROPERTY = "forbric.mixinFit.nativeAbsent";

	/**
	 * {@code -Dforbric.mixinFit.nativeAbsent.base=<digest>}: the members digest the table is trusted for instead of the
	 * one it records. For a fixture game whose classes the table has no rows for (the weave tests), or a base someone
	 * has checked against the staged one by hand; never needed on an installed game.
	 */
	static final String BASE_PROPERTY = "forbric.mixinFit.nativeAbsent.base";

	/** The packages vanilla's jar puts its classes in; the census asserts it ships none elsewhere. */
	static final List<String> VANILLA_PACKAGES = List.of("net/minecraft/", "com/mojang/");

	/** Whether {@code internalName} is in {@link #VANILLA_PACKAGES}: the only classes this ever answers for. */
	static boolean inVanillaPackages(String internalName) {
		if (internalName == null) return false;
		for (String pkg : VANILLA_PACKAGES) if (internalName.startsWith(pkg)) return true;
		return false;
	}

	/** {@code base <digest>}: the members digest of the merged base the rows were derived from. */
	static final String BASE = "base ";

	/**
	 * {@code library <digest> <maven name>}: the members digest of one of Minecraft's own library jars that ships classes
	 * in vanilla's packages, which every platform loads as it is.
	 */
	static final String LIBRARY = "library ";

	/**
	 * {@code platform <id> <requirement>=<version>...}: the table speaks for this platform, whose game is the one those
	 * versions name — {@code minecraft=} always, and {@code neoforge=} for that platform. A platform
	 * without the line is never answered for.
	 */
	static final String PLATFORM = "platform ";

	/** {@code <id> merged-only <owner>}: a class in vanilla's packages the merged base has and that platform does not. */
	static final String MERGED_ONLY = "merged-only ";

	/** The requirement id every platform's game answers to. */
	static final String MINECRAFT = "minecraft";

	/**
	 * What one evaluation needs to ask.
	 *
	 * @param raw            the merged base's bytes before the transform chain, by resource path
	 * @param defaultRequire the config's {@code injectors.defaultRequire} as the mod wrote it — negative when that
	 *                       cannot be known (a config that inherits it from a {@code parent})
	 * @param platform       the owning mod's ecosystem, whose game is the native one; null when no single mod owns the
	 *                       config, which asks nothing
	 * @param base           the members digest of the jar serving a class, by internal name; null for no jar
	 * @param mod            what discovery read from the owning mod's manifest — its id and declared requirements;
	 *                       null when that is not known, which asks nothing
	 */
	public record Context(Function<String, byte[]> raw, int defaultRequire, Ecosystem platform,
			Function<String, String> base, DiscoveredMod mod) {
		/** Asks nothing: every caller that predates this. */
		public static final Context NONE = new Context(null, -1, null, null, null);
	}

	/**
	 * One platform's rows.
	 *
	 * @param methods    owner → {@code name + descriptor} of every method the platform declares there and the merged
	 *                   class does not
	 * @param mergedOnly classes in vanilla's packages the merged base has and the platform does not
	 * @param versions   requirement id → the version of the game the rows describe, from the {@code platform} line
	 */
	record Rows(Map<String, Set<String>> methods, Set<String> mergedOnly, Map<String, String> versions) {
		/** Whether a row names a method {@code name} on {@code owner} — of {@code desc}, or of any when it is null. */
		boolean lists(String owner, String name, String desc) {
			Set<String> declared = methods.get(owner);
			if (declared == null) return false;
			if (desc != null) return declared.contains(name + desc);
			for (String method : declared) if (method.startsWith(name + "(")) return true;
			return false;
		}
	}

	/**
	 * @param base      the members digest the rows were derived against, or null when the table records none
	 * @param libraries the members digests of Minecraft's own library jars, each answered for as it is
	 * @param platforms the platforms the table speaks for, each with its rows
	 */
	record Table(String base, Set<String> libraries, Map<Ecosystem, Rows> platforms) {
		static final Table EMPTY = new Table(null, Set.of(), Map.of());

		/** The census's lines, comments and blanks skipped; a line of no known shape is ignored. */
		static Table parse(List<String> lines) {
			String base = null;
			Set<String> libraries = new HashSet<>();
			Map<Ecosystem, Map<String, String>> declared = new EnumMap<>(Ecosystem.class);
			Map<Ecosystem, Map<String, Set<String>>> methods = new EnumMap<>(Ecosystem.class);
			Map<Ecosystem, Set<String>> mergedOnly = new EnumMap<>(Ecosystem.class);
			for (String raw : lines) {
				String line = raw.strip();
				if (line.isEmpty() || line.startsWith("#")) continue;
				if (line.startsWith(BASE)) {
					base = line.substring(BASE.length()).strip();
					continue;
				}
				if (line.startsWith(LIBRARY)) {
					String[] words = line.substring(LIBRARY.length()).strip().split("\\s+");
					if (!words[0].isEmpty()) libraries.add(words[0]);
					continue;
				}
				if (line.startsWith(PLATFORM)) {
					String[] words = line.substring(PLATFORM.length()).strip().split("\\s+");
					Ecosystem platform = platformNamed(words[0]);
					if (platform == null) continue;
					Map<String, String> versions = new HashMap<>();
					for (int i = 1; i < words.length; i++) {
						int eq = words[i].indexOf('=');
						if (eq > 0 && eq < words[i].length() - 1) versions.put(words[i].substring(0, eq), words[i].substring(eq + 1));
					}
					declared.put(platform, Map.copyOf(versions));
					continue;
				}
				int space = line.indexOf(' ');
				Ecosystem platform = space <= 0 ? null : platformNamed(line.substring(0, space));
				if (platform == null) continue;
				String row = line.substring(space + 1).strip();
				if (row.startsWith(MERGED_ONLY)) {
					mergedOnly.computeIfAbsent(platform, k -> new HashSet<>()).add(row.substring(MERGED_ONLY.length()).strip());
					continue;
				}
				int hash = row.indexOf('#');
				if (hash <= 0 || row.indexOf('(', hash) < 0) continue;
				methods.computeIfAbsent(platform, k -> new HashMap<>())
						.computeIfAbsent(row.substring(0, hash), k -> new HashSet<>()).add(row.substring(hash + 1));
			}
			Map<Ecosystem, Rows> platforms = new EnumMap<>(Ecosystem.class);
			for (Map.Entry<Ecosystem, Map<String, String>> platform : declared.entrySet()) {
				Map<String, Set<String>> rows = new HashMap<>();
				methods.getOrDefault(platform.getKey(), Map.of()).forEach((owner, set) -> rows.put(owner, Set.copyOf(set)));
				platforms.put(platform.getKey(), new Rows(Map.copyOf(rows),
						Set.copyOf(mergedOnly.getOrDefault(platform.getKey(), Set.of())), platform.getValue()));
			}
			return new Table(base == null || base.isEmpty() ? null : base, Set.copyOf(libraries),
					Collections.unmodifiableMap(platforms));
		}

		/** That platform's rows, or null when the table does not speak for it. */
		Rows of(Ecosystem platform) {
			return platform == null ? null : platforms.get(platform);
		}
	}

	private static volatile Table shipped;

	/** The digests a mismatch was already reported for, so a boot says it once. */
	private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
