/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.boot;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.fabricmc.api.EnvType;
import net.forbric.api.*;
import net.forbric.kernel.discovery.ForbricModDiscoverer;
import net.forbric.kernel.discovery.ModAnnotationScanner;
import net.forbric.kernel.fabric.FabricModMetadataParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Reads only evidence strong enough to constrain candidate selection; arbitrary class references are not requirements. */
final class CandidateContractScanner {
	private enum Match { YES, NO, UNKNOWN }
	/**
	 * {@code unsupported} names an entrypoint form the closure cannot follow; it is then unproved, never skipped.
	 * {@code desc} null means the one public method of that name. {@code alwaysRuns} false (an event listener whose
	 * event may never fire) keeps every contract it finds soft and does not report what it could not follow.
	 */
	private record Entry(String owner, String method, String unsupported, String desc, boolean alwaysRuns) {
		Entry(String owner, String method, String unsupported) { this(owner, method, unsupported, null, true); }
	}
	/** {@code merged}: Mixin code runs inside its target, where access wideners and subclass access apply. */
	private record MemberUse(boolean field, int opcode, boolean interfaceOwner, String caller, boolean merged) {
		MemberUse(boolean field, int opcode, boolean interfaceOwner, String caller) { this(field, opcode, interfaceOwner, caller, false); }
		boolean staticUse() { return opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC; }
		boolean writesField() { return opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC; }
	}
	/** One way a Mixin member can be located in its target: {@code desc} null matches any descriptor. */
	record Selector(String name, String desc) { }
	/**
	 * What a Mixin needs its target to declare itself (Mixin searches the target class, not its supertypes).
	 * {@code isStatic} null means the modifier is not compared (injector targets); {@code necessary} false means
	 * Mixin carries on without it (an injector nobody requires to match).
	 */
	private record MixinMember(boolean field, List<Selector> alternatives, Boolean isStatic, String kind, boolean necessary) { }
	/** Every declared Mixin target, and the members each Mixin (by class) merges into it. */
	private record Declared(Set<String> targets, Map<String, Map<String, Set<String>>> added) { }
	private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;", OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;",
			ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;", INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
	/** Injectors whose {@code method} selectors name methods of the target (Mixin and MixinExtras). */
	private static final Set<String> INJECTORS = Set.of("Lorg/spongepowered/asm/mixin/injection/Inject;",
			"Lorg/spongepowered/asm/mixin/injection/Redirect;", "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyArgs;", "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyConstant;", "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
			"Lcom/llamalad7/mixinextras/injector/ModifyReceiver;", "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
			"Lcom/llamalad7/mixinextras/injector/WrapWithCondition;", "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
			"Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;", "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;");
	private record Metadata(List<UnifiedDependency> dependencies, Map<String, String> provides,
			List<String> mixins, List<Entry> entries, List<Exclusion> exclusions) { }
	/** A declared "cannot run with": {@code constraint} null means the range could not be read. */
	private record Exclusion(String modId, String constraint, boolean hard) { }
	private static final Set<String> LISTENERS = Set.of("Lnet/neoforged/bus/api/SubscribeEvent;");
	/** Mod-bus lifecycle events FML posts on every launch of that side; a null side means both. */
	private static final Map<String, Optional<EnvType>> LIFECYCLE = lifecycleEvents();
	private static Map<String, Optional<EnvType>> lifecycleEvents() {
		Map<String, Optional<EnvType>> events = new HashMap<>();
		for (String pkg : List.of("net/neoforged/fml/event/lifecycle/")) {
			for (String common : List.of("FMLCommonSetupEvent", "FMLLoadCompleteEvent", "InterModEnqueueEvent", "InterModProcessEvent")) events.put(pkg + common, Optional.empty());
			events.put(pkg + "FMLClientSetupEvent", Optional.of(EnvType.CLIENT));
			events.put(pkg + "FMLDedicatedServerSetupEvent", Optional.of(EnvType.SERVER));
		}
		return Map.copyOf(events);
	}
	private static final Set<String> PLATFORM = Set.of("java", "minecraft", "forge", "neoforge", "fabricloader", "fabric", "fml", "mixinextras");
	static final int HELPER_DEPTH_LIMIT = 32;
	static final int HELPER_NODE_LIMIT = 256;
	static final int HELPER_INSTRUCTION_LIMIT = 32768;

	private CandidateContractScanner() { }

	static List<JointCandidateSelector.Rule> scan(List<DuplicateModArbiter.Claim> claims, EnvType side) {
		Map<Path, Set<String>> owners = new LinkedHashMap<>();
		for (var claim : claims) owners.put(JointCandidateSelector.path(claim), Set.copyOf(claim.modIds()));
		return scan(claims, side, false, owners);
	}

	/** The complete graph supplies each physical jar separately; a parent must not claim a losing child's classes. */
	static List<JointCandidateSelector.Rule> scanPhysical(List<DuplicateModArbiter.Claim> claims, EnvType side,
			Map<Path, Set<String>> symbolOwners) {
		return scan(claims, side, true, symbolOwners);
	}

	private static List<JointCandidateSelector.Rule> scan(List<DuplicateModArbiter.Claim> claims, EnvType side,
			boolean physicalOnly, Map<Path, Set<String>> symbolOwners) {
		Map<Path, Inventory> inventories = new LinkedHashMap<>();
		Map<Path, Metadata> metadata = new LinkedHashMap<>();
		List<JointCandidateSelector.Rule> rules = new ArrayList<>();
		for (var claim : claims) {
			Path path = JointCandidateSelector.path(claim);
			try {
				Inventory inventory = new Inventory(path, physicalOnly);
				inventories.put(path, inventory);
				metadata.put(path, readMetadata(claim, inventory, side));
			} catch (Exception unreadable) {
				Map<String, String> knownProvides = new LinkedHashMap<>();
				for (String id : claim.modIds()) knownProvides.put(JointCandidateSelector.key(id), claim.versionOf(id));
				metadata.put(path, new Metadata(List.of(), knownProvides, List.of(), List.of(), List.of()));
				rules.add(new JointCandidateSelector.Rule("metadata", path, Set.of(), Set.of(path), true,
						"candidate contracts could not be fully read: " + unreadable.getClass().getSimpleName()));
			}
		}
		// Candidate bytecode is pre-Mixin. Even an optional/plugin-controlled declaration can add the very
		// member an entrypoint will call; its declaration proves uncertainty, not that the member stays absent.
		Set<String> transformedTargets = new HashSet<>();
		Map<String, Map<String, Set<String>>> mixinAdded = new HashMap<>();
		for (var entry : metadata.entrySet()) {
			Declared declared = declaredMixins(entry.getValue(), inventories.get(entry.getKey()), side);
			transformedTargets.addAll(declared.targets());
			declared.added().forEach((target, members) -> members.forEach((member, by) ->
					mixinAdded.computeIfAbsent(target, k -> new HashMap<>()).computeIfAbsent(member, k -> new HashSet<>()).addAll(by)));
		}
		for (var claim : claims) {
			Path source = JointCandidateSelector.path(claim);
			Metadata mod = metadata.get(source); Inventory inventory = inventories.get(source);
			if (mod == null || inventory == null) continue;
			List<UnifiedDependency> mandatory = mod.dependencies().stream().filter(UnifiedDependency::isMandatory)
					.filter(d -> d.appliesOn(side == EnvType.SERVER ? Side.DEDICATED_SERVER : Side.CLIENT)).toList();
			List<UnifiedDependency> symbolDependencies = new ArrayList<>();
			for (UnifiedDependency dependency : mandatory) {
				if (PLATFORM.contains(dependency.getModId())) continue;
				String key = providedKey(dependency.getModId(), metadata);
				// DependencyAudit owns a missing installation and a version nobody installed: it warns, offers its
				// dialog and loads the mod anyway. Arbitration only decides between installed candidates, so a
				// requirement that no candidate can meet is not a choice here and must not become one (gate-m20).
				if (key == null) continue;
				symbolDependencies.add(new UnifiedDependency(key, dependency.getVersionConstraint(), true));
				Set<Path> providers = new LinkedHashSet<>(), unknown = new LinkedHashSet<>();
				for (var candidate : metadata.entrySet()) {
					String version = candidate.getValue().provides().get(key);
					if (version == null) continue;
					if (VersionPredicate.matchesStrictly(dependency.getVersionConstraint(), version)) providers.add(candidate.getKey());
					else if (VersionPredicate.matches(dependency.getVersionConstraint(), version)) unknown.add(candidate.getKey());
				}
				if (providers.isEmpty() && unknown.isEmpty()) continue;
				rules.add(new JointCandidateSelector.Rule("dependency:" + dependency.getModId(), source, providers, unknown, true,
						"requires " + dependency.getModId() + " " + dependency.getVersionConstraint()));
			}
			// Negative constraints are dependency constraints too (PLAN.md:62): prefer the build the mod can run with.
			for (Exclusion exclusion : mod.exclusions()) {
				if (PLATFORM.contains(exclusion.modId())) continue;
				String key = providedKey(exclusion.modId(), metadata); if (key == null) continue;
				Set<Path> excluded = new LinkedHashSet<>(), maybe = new LinkedHashSet<>();
				for (var candidate : metadata.entrySet()) {
					String version = candidate.getValue().provides().get(key);
					if (version == null || candidate.getKey().equals(source)) continue;
					if (exclusion.constraint() != null && VersionPredicate.matchesStrictly(exclusion.constraint(), version)) excluded.add(candidate.getKey());
					else if (exclusion.constraint() == null || VersionPredicate.matches(exclusion.constraint(), version)) maybe.add(candidate.getKey());
				}
				if (excluded.isEmpty() && maybe.isEmpty()) continue;
				String range = exclusion.constraint() == null ? "(unreadable range)" : exclusion.constraint();
				rules.add(new JointCandidateSelector.Rule((exclusion.hard() ? "breaks:" : "conflicts:") + exclusion.modId(), source, excluded, maybe,
						exclusion.hard(), (exclusion.hard() ? "declares it cannot run with " : "declares it conflicts with ") + exclusion.modId() + " " + range, true));
			}
			if (physicalOnly) for (String own : symbolOwners.getOrDefault(source, Set.of())) symbolDependencies.add(new UnifiedDependency(own, "*", true));
			for (String config : mod.mixins()) scanMixins(source, config, inventory, symbolDependencies, symbolOwners, inventories, side,
					transformedTargets, mixinAdded, rules);
			var calls = new EntrypointCalls(source, inventory, symbolDependencies, symbolOwners, inventories, transformedTargets, rules);
			for (Entry entry : mod.entries()) calls.scan(entry);
		}
		return List.copyOf(rules);
	}

	/**
	 * The provides key an installed candidate answers {@code id} under, or null when none does. Same order as
	 * DependencyAudit: the exact id (and every provides alias) first, then {@link ModIds#collapsed} only when
	 * exactly one installed mod collapses to it. Several jars of that ONE mod (its builds for each ecosystem) are
	 * still one mod; two different mods collapsing to the same key decline, exactly as the audit does.
	 */
	private static String providedKey(String id, Map<Path, Metadata> metadata) {
		String exact = JointCandidateSelector.key(id);
		Set<String> keys = new TreeSet<>();
		for (Metadata candidate : metadata.values()) keys.addAll(candidate.provides().keySet());
		if (keys.contains(exact)) return exact;
		if (!ModIds.enabled()) return null;
		String wanted = ModIds.collapsed(id);
		if (wanted == null || wanted.isEmpty()) return null;
		// Keyed by who provides it: one mod reached under its id and a provides alias is still one candidate set.
		Map<Set<Path>, String> spelled = new LinkedHashMap<>();
		for (String key : keys) {
			if (!wanted.equals(ModIds.collapsed(key))) continue;
			Set<Path> providers = new HashSet<>();
			for (var candidate : metadata.entrySet()) if (candidate.getValue().provides().containsKey(key)) providers.add(candidate.getKey());
			spelled.putIfAbsent(providers, key);
		}
		return spelled.size() == 1 ? spelled.values().iterator().next() : null;
	}

	private static Metadata readMetadata(DuplicateModArbiter.Claim claim, Inventory jar, EnvType side) throws Exception {
		List<UnifiedDependency> dependencies = new ArrayList<>(); Map<String, String> provides = new LinkedHashMap<>();
		for (String id : claim.modIds()) provides.put(JointCandidateSelector.key(id), claim.versionOf(id));
		List<String> mixins = new ArrayList<>(); List<Entry> entries = new ArrayList<>(); List<Exclusion> exclusions = new ArrayList<>();
		if (claim.ecosystem() == null) return new Metadata(List.of(), Map.of(), List.of(), List.of(), List.of());
		if (claim.ecosystem() == Ecosystem.FABRIC) {
			byte[] manifest = jar.read("fabric.mod.json");
			if (manifest == null) throw new IOException("no Fabric metadata");
			var mod = FabricModMetadataParser.read(new ByteArrayInputStream(manifest));
			dependencies.addAll(KernelFabricEcosystem.unifiedDependencies(mod));
			for (var dependency : mod.getDependencies()) {
				var kind = dependency.getKind();
				if (kind == net.fabricmc.loader.api.metadata.ModDependency.Kind.BREAKS || kind == net.fabricmc.loader.api.metadata.ModDependency.Kind.CONFLICTS)
					exclusions.add(new Exclusion(dependency.getModId(), KernelFabricEcosystem.constraintOf(dependency), !kind.isSoft()));
			}
			for (String alias : mod.getProvides()) provides.put(JointCandidateSelector.key(alias), mod.getVersion().getFriendlyString());
			for (var config : mod.getMixinConfigs()) if (side == null || config.environment().matches(side)) mixins.add(config.config());
			Map<String, String> phases = new LinkedHashMap<>(Map.of("preLaunch", "onPreLaunch", "main", "onInitialize"));
			phases.put(side == EnvType.SERVER ? "server" : "client", side == EnvType.SERVER ? "onInitializeServer" : "onInitializeClient");
			for (var phase : phases.entrySet()) for (var entry : mod.getEntrypoints().getOrDefault(phase.getKey(), List.of())) {
				// These all run (KernelFabricLoader resolves "Cls::member" and hands other adapters to their
				// language adapter), so none may be dropped silently. The default and Kotlin adapters both call the
				// named method, or the phase method on the class/object; another adapter is explicitly unproved.
				String value = entry.value(); int member = value.indexOf("::");
				String owner = (member < 0 ? value : value.substring(0, member)).replace('.', '/');
				if (!entry.isDefaultAdapter() && !"kotlin".equals(entry.adapter())) {
					entries.add(new Entry(owner, null, "language adapter '" + entry.adapter() + "'")); continue;
				}
				entries.add(new Entry(owner, member < 0 ? phase.getValue() : value.substring(member + 2), null));
			}
		} else {
			for (DiscoveredMod mod : new ForbricModDiscoverer().discoverJar(claim.jar())) {
				if (mod.getEcosystem() != claim.ecosystem() || !claim.modIds().contains(mod.getId())) continue;
				dependencies.addAll(mod.getDependencies()); mixins.addAll(mod.getMixinConfigs());
			}
			exclusions.addAll(forgeExclusions(claim, jar, side));
			for (var entry : ModAnnotationScanner.scan(claim.jar())) {
				if (entry.family != claim.ecosystem() || !claim.modIds().contains(entry.modId)) continue;
				if (!entry.dists.isEmpty() && !entry.dists.contains(side == EnvType.SERVER ? "DEDICATED_SERVER" : "CLIENT")) continue;
				entries.add(new Entry(entry.className.replace('.', '/'), "<init>", null));
			}
			entries.addAll(subscriberEntries(claim, jar, side));
		}
		return new Metadata(List.copyOf(dependencies), Map.copyOf(provides), List.copyOf(mixins), List.copyOf(entries), List.copyOf(exclusions));
	}

	/**
	 * The static listeners of a Forge-family mod's {@code @EventBusSubscriber} classes. One for an FML lifecycle
	 * event of this side runs on every launch, so it is held to entrypoint rules; any other may never fire.
	 */
	private static List<Entry> subscriberEntries(DuplicateModArbiter.Claim claim, Inventory jar, EnvType side) throws IOException {
		String annotation = "Lnet/neoforged/fml/common/EventBusSubscriber;";
		String dist = side == EnvType.SERVER ? "DEDICATED_SERVER" : "CLIENT";
		List<Entry> entries = new ArrayList<>();
		for (ClassNode node : jar.classesMentioning("EventBusSubscriber")) {
			AnnotationNode subscriber = find(annotations(node.visibleAnnotations, node.invisibleAnnotations), annotation);
			if (subscriber == null) continue;
			if (value(subscriber, "modid") instanceof String owner && !owner.isEmpty() && !claim.modIds().contains(owner)) continue;
			if (side != null && value(subscriber, "value") instanceof List<?> dists && !dists.isEmpty()
					&& dists.stream().noneMatch(d -> d instanceof String[] e && e.length == 2 && e[1].equals(dist))) continue;
			for (MethodNode method : node.methods) {
				if ((method.access & Opcodes.ACC_STATIC) == 0 || annotations(method.visibleAnnotations, method.invisibleAnnotations)
						.stream().noneMatch(a -> LISTENERS.contains(a.desc))) continue;
				Type[] arguments = Type.getArgumentTypes(method.desc);
				Optional<EnvType> lifecycle = arguments.length == 1 && arguments[0].getSort() == Type.OBJECT ? LIFECYCLE.get(arguments[0].getInternalName()) : null;
				boolean always = lifecycle != null && (lifecycle.isEmpty() || lifecycle.get() == side);
				entries.add(new Entry(node.name, method.name, null, method.desc, always));
			}
		}
		return entries;
	}

	/**
	 * NeoForge {@code type="incompatible"} (hard) and {@code "discouraged"} (soft) entries, read from the toml here
	 * because the shared parser models only positive dependencies and reads both as optional ones.
	 */
	private static List<Exclusion> forgeExclusions(DuplicateModArbiter.Claim claim, Inventory jar, EnvType side) throws IOException {
		byte[] toml = jar.read(claim.ecosystem() == Ecosystem.NEOFORGE ? "META-INF/neoforge.mods.toml" : "META-INF/mods.toml");
		if (toml == null) return List.of();
		com.electronwill.nightconfig.core.UnmodifiableConfig config;
		try { config = new com.electronwill.nightconfig.toml.TomlParser().parse(new StringReader(new String(toml, java.nio.charset.StandardCharsets.UTF_8))); }
		catch (RuntimeException malformed) { return List.of(); }
		List<Exclusion> exclusions = new ArrayList<>();
		for (String id : claim.modIds()) {
			Object declared = config.get(List.of("dependencies", id));
			if (!(declared instanceof List<?> entries)) continue;
			for (Object entry : entries) {
				if (!(entry instanceof com.electronwill.nightconfig.core.UnmodifiableConfig dependency)) continue;
				String type = dependency.getOrElse("type", ""), modId = dependency.getOrElse("modId", "");
				boolean hard = "incompatible".equalsIgnoreCase(type.trim());
				if (!hard && !"discouraged".equalsIgnoreCase(type.trim()) || modId.isBlank()) continue;
				if (!UnifiedDependency.SideScope.parse(dependency.getOrElse("side", (String) null)).includes(side == EnvType.SERVER ? Side.DEDICATED_SERVER : Side.CLIENT)) continue;
				String constraint;
				try { constraint = net.forbric.kernel.metadata.forge.ForgeVersionRangeTranslator.toFabricPredicate(dependency.getOrElse("versionRange", (String) null)); }
				catch (IllegalArgumentException malformed) { constraint = null; }
				exclusions.add(new Exclusion(modId, constraint, hard));
			}
		}
		return exclusions;
	}

	private static void scanMixins(Path source, String name, Inventory jar, List<UnifiedDependency> dependencies,
			Map<Path, Set<String>> symbolOwners, Map<Path, Inventory> inventories, EnvType side, Set<String> transformedTargets,
			Map<String, Map<String, Set<String>>> mixinAdded, List<JointCandidateSelector.Rule> rules) {
