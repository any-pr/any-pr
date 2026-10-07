/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.boot;

import java.nio.file.Path;
import java.util.*;
import net.forbric.api.Ecosystem;
import net.forbric.api.VersionPredicate;
import net.forbric.kernel.metadata.forge.ForgeVersionRangeTranslator;
import org.sat4j.core.VecInt;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.specs.*;

/** Whole-instance Boolean model: nested candidates exist only through selected parents. */
final class ReachableCandidateSelector {
	/**
	 * Work bounds only, never a clock. Conflicts per satisfiability check and model size are the same on a fast
	 * Mac and a loaded Windows box, so the same mods folder always selects the same jars.
	 */
	static final int CONFLICT_BUDGET = 200_000;
	static final int VARIABLE_LIMIT = 500_000;
	private final NestedCandidateInventory graph;
	private final List<JointCandidateSelector.Rule> rules;
	private final Map<Path, Integer> variables = new LinkedHashMap<>();
	private final Map<String, List<Path>> identities = new TreeMap<>();
	private final Set<String> rootIds = new TreeSet<>();
	private final Map<String, Map<Path, Set<String>>> artifacts = new TreeMap<>();
	private final Map<Path, List<Path>> parents = new HashMap<>();
	private final Map<Path, Integer> depth = new HashMap<>();
	private final Map<String, Ecosystem> overrides;
	private final List<Ecosystem> rootPreference, nestedPreference;
	private final int checkLimit;
	private long checks;
	/** The most recent model the solver returned; it answers every later trial it already satisfies. */
	private boolean[] lastModel;

	private ReachableCandidateSelector(NestedCandidateInventory graph, List<JointCandidateSelector.Rule> contracts,
			List<Ecosystem> rootPreference, List<Ecosystem> nestedPreference, Map<String, Ecosystem> overrides, int checkLimit) {
		this.graph = graph; this.rootPreference = rootPreference; this.nestedPreference = nestedPreference;
		Map<String, Ecosystem> pins = new LinkedHashMap<>(); overrides.forEach((id, family) -> pins.put(JointCandidateSelector.key(id), family));
		this.overrides = pins; this.checkLimit = Math.max(1, checkLimit);
		for (var node : graph.nodes().values()) {
			variables.put(node.path(), variables.size() + 1);
			if (node.excluded() || node.claim() == null) continue;
			for (String raw : node.claim().modIds()) {
				String id = JointCandidateSelector.key(raw);
				identities.computeIfAbsent(id, ignored -> new ArrayList<>()).add(node.path());
				if (node.root()) rootIds.add(id);
			}
		}
		for (var edge : graph.edges()) {
			parents.computeIfAbsent(edge.child(), ignored -> new ArrayList<>()).add(edge.parent());
			if (edge.coordinate() == null || graph.nodes().get(edge.child()).excluded()) continue;
			String id = artifactId(edge.coordinate().id());
			artifacts.computeIfAbsent(id, ignored -> new LinkedHashMap<>()).computeIfAbsent(edge.child(), ignored -> new LinkedHashSet<>()).add(edge.coordinate().version());
			List<Path> candidates = identities.computeIfAbsent(id, ignored -> new ArrayList<>());
			if (!candidates.contains(edge.child())) candidates.add(edge.child());
		}
		// Shallowest nesting level of each node, so a parent's identity is always decided before its children's.
		Map<Path, List<Path>> children = new HashMap<>();
		for (var edge : graph.edges()) children.computeIfAbsent(edge.parent(), ignored -> new ArrayList<>()).add(edge.child());
		Deque<Path> frontier = new ArrayDeque<>();
		for (var node : graph.nodes().values()) if (node.root()) { depth.put(node.path(), 0); frontier.add(node.path()); }
		while (!frontier.isEmpty()) {
			Path parent = frontier.removeFirst();
			for (Path child : children.getOrDefault(parent, List.of())) if (!depth.containsKey(child)) {
				depth.put(child, depth.get(parent) + 1); frontier.add(child);
			}
		}
		List<JointCandidateSelector.Rule> expanded = new ArrayList<>(contracts);
		for (var edge : graph.edges()) {
			if (edge.coordinate() == null || graph.nodes().get(edge.child()).excluded()) continue;
			Set<Path> providers = new LinkedHashSet<>(), unknown = new LinkedHashSet<>();
			String range = edge.coordinate().range();
			String predicate;
			boolean malformed = false;
			// Hand-written metadata.json can carry "[1.0" or "[]". The legacy extractor never parsed ranges and
			// loaded such packs; one bad constraint must leave its choice unproved, not abort the whole boot.
			try { predicate = ForgeVersionRangeTranslator.toFabricPredicate(range); }
			catch (IllegalArgumentException unparseable) { predicate = "*"; malformed = true; }
			boolean unreadable = malformed || "*".equals(predicate) && range != null && !range.isBlank()
					&& !Set.of("*", "(,)", "[,)", "(,]", "[,]").contains(range);
			String translated = predicate; boolean open = unreadable;
			Map<Path, Set<String>> sameArtifact = artifacts.get(artifactId(edge.coordinate().id()));
			for (Path candidate : edgeProviders(edge)) {
				// The coordinate's own artifact is judged by the version the parent's metadata recorded for it;
				// another build of the same mod (a top-level copy, another ecosystem's platform artifact, a
				// Fabric JiJ child that carries no JarJar metadata at all) by the version that build declares.
				Set<String> versions = sameArtifact.containsKey(candidate) ? sameArtifact.get(candidate)
						: Set.of(modVersion(candidate, graph.nodes().get(edge.child()).claim().modIds().getFirst()));
				boolean all = !open && versions.stream().allMatch(v -> VersionPredicate.matchesStrictly(translated, v));
				boolean any = open || versions.stream().anyMatch(v -> VersionPredicate.matches(translated, v));
				if (all) providers.add(candidate); else if (any) unknown.add(candidate);
			}
			expanded.add(new JointCandidateSelector.Rule("jarjar:" + edge.entry(), edge.parent(), providers, unknown, true,
					(malformed ? "malformed JarJar version range, left unproved: " : "") + "requires bundled artifact "
							+ edge.coordinate().id() + " " + range));
		}
		this.rules = List.copyOf(expanded);
	}

	static JointCandidateSelector.Result solve(NestedCandidateInventory graph, List<JointCandidateSelector.Rule> contracts,
			List<Ecosystem> roots, List<Ecosystem> nested, Map<String, Ecosystem> overrides, int limit) {
		ReachableCandidateSelector model = new ReachableCandidateSelector(graph, contracts, roots, nested, overrides, limit);
		Search search = model.search();
		Set<Path> selected = search.selected();
		List<JointCandidateSelector.Rule> failed = new ArrayList<>(), uncertain = new ArrayList<>(), unavoidable = new ArrayList<>();
		for (var rule : model.rules) {
			if (rule.provedBy(selected)) continue;
			if (search.unavoidable().contains(rule)) unavoidable.add(rule);
			else if (rule.hard() && rule.brokenBy(selected)) failed.add(rule);
			else uncertain.add(rule);
		}
		for (var issue : graph.issues()) if (selected.contains(issue.source())) uncertain.add(new JointCandidateSelector.Rule(
				"nested-scan:" + issue.detail(), issue.source(), Set.of(), Set.of(), true, issue.detail()));
		JointCandidateSelector.Status status = search.status();
		if (status == JointCandidateSelector.Status.SOLVED && uncertain.stream().anyMatch(JointCandidateSelector.Rule::hard)) status = JointCandidateSelector.Status.UNPROVED;
		return new JointCandidateSelector.Result(status, Set.copyOf(selected), List.copyOf(failed), List.copyOf(uncertain), model.checks,
				List.copyOf(unavoidable), Map.copyOf(search.refused()), Map.copyOf(search.impossible()));
	}

	private record Search(Set<Path> selected, JointCandidateSelector.Status status, Set<JointCandidateSelector.Rule> unavoidable,
			Map<String, Ecosystem> refused, Map<String, Ecosystem> impossible) { }

	/**
	 * Structure first (reachability, one build per id, roots present, bundling edges), then the user's pins, then
	 * every hard contract, each behind its own activation literal. A pin or contract that cannot join what was
	 * already accepted is relaxed ALONE; everything else stays in force, so one unsatisfiable rule no longer
	 * hands every other contested id back to the bare ecosystem preference. A relaxed item that no selection at
	 * all could meet (a pin for an ecosystem with no candidate, a contract no installed build provides) is not a
	 * combination problem and is reported apart from the real conflicts.
	 */
	private Search search() {
		List<JointCandidateSelector.Rule> hard = new ArrayList<>();
		for (var rule : rules) {
			if (!rule.hard() || !variables.containsKey(rule.consumer())) continue;
			// Always met: a rule the consumer provides itself, or an exclusion with nothing PROVED to be excluded.
			if (rule.excludes() ? rule.providers().stream().noneMatch(variables::containsKey) : rule.possible().contains(rule.consumer())) continue;
			hard.add(rule);
		}
		int next = variables.size();
		Map<String, Integer> pinLiterals = new LinkedHashMap<>();
		// A pin naming an ecosystem that has no usable build of the id cannot be a choice at all; the old per-id
		// pick warned and kept the automatic answer, and so does this. Every other pin is asserted.
		Map<String, Ecosystem> impossible = new LinkedHashMap<>();
		for (var pin : overrides.entrySet()) {
			if (identities.getOrDefault(pin.getKey(), List.of()).stream().anyMatch(p -> family(p) == pin.getValue())) pinLiterals.put(pin.getKey(), ++next);
			else impossible.put(pin.getKey(), pin.getValue());
		}
		Map<JointCandidateSelector.Rule, Integer> ruleLiterals = new LinkedHashMap<>();
		for (var rule : hard) ruleLiterals.put(rule, ++next);
		// A second literal per contract that some build PROVABLY meets while others only might (an inaccessible
		// pre-transform member, a declared Mixin target): the proved reading is preferred, never required.
		Map<JointCandidateSelector.Rule, Integer> proofLiterals = new LinkedHashMap<>();
		for (var rule : hard) if (!rule.excludes() && !rule.providers().isEmpty() && !rule.uncertainProviders().isEmpty()) proofLiterals.put(rule, ++next);
		if (next > VARIABLE_LIMIT) return new Search(fallback(), JointCandidateSelector.Status.SEARCH_LIMIT, Set.of(), Map.of(), impossible);
		ISolver solver = SolverFactory.newDefault(); solver.newVar(next);
		// A conflict count, not a clock: the same mods folder selects the same jars on every machine.
		solver.setTimeoutOnConflicts(CONFLICT_BUDGET);
		lastModel = null;
		try {
			structure(solver);
			for (var pin : pinLiterals.entrySet()) {
				List<Integer> pinned = new ArrayList<>(List.of(-pin.getValue()));
				for (Path candidate : identities.getOrDefault(pin.getKey(), List.of())) if (family(candidate) == overrides.get(pin.getKey())) pinned.add(variable(candidate));
				clause(solver, pinned);
			}
			for (var rule : ruleLiterals.entrySet()) {
				if (rule.getKey().excludes()) {
					// Consumer and a build proved to be in its excluded range are never selected together.
					for (Path excluded : rule.getKey().providers()) if (variables.containsKey(excluded))
						clause(solver, -rule.getValue(), -variable(rule.getKey().consumer()), -variable(excluded));
					continue;
				}
				List<Integer> required = new ArrayList<>(List.of(-rule.getValue(), -variable(rule.getKey().consumer())));
				for (Path provider : rule.getKey().possible()) if (variables.containsKey(provider)) required.add(variable(provider));
				clause(solver, required);
			}
			for (var rule : proofLiterals.entrySet()) {
				List<Integer> proved = new ArrayList<>(List.of(-rule.getValue(), -variable(rule.getKey().consumer())));
				for (Path provider : rule.getKey().providers()) if (variables.containsKey(provider)) proved.add(variable(provider));
				clause(solver, proved);
			}
		} catch (ContradictionException contradiction) {
			return new Search(fallback(), JointCandidateSelector.Status.UNSATISFIABLE, Set.of(), Map.of(), impossible);
		}
		VecInt assumptions = new VecInt();
		Set<Integer> relaxed = new LinkedHashSet<>();
		Set<Path> selected = null;
		boolean pinsSettled = false;
		// A pin that cannot join the structure or an earlier pin is refused however far the search gets.
		Map<String, Ecosystem> refused = new LinkedHashMap<>();
		try {
			if (!satisfiable(solver, assumptions)) return new Search(fallback(), JointCandidateSelector.Status.UNSATISFIABLE, Set.of(), Map.of(), impossible);
			// The user's choice first (PLAN: 用户指定优先), then the contracts, in scan order.
			accept(solver, assumptions, List.copyOf(pinLiterals.values()), relaxed);
			for (var pin : pinLiterals.entrySet()) if (relaxed.contains(pin.getValue())) refused.put(pin.getKey(), overrides.get(pin.getKey()));
			pinsSettled = true;
			accept(solver, assumptions, List.copyOf(ruleLiterals.values()), relaxed);
			// PLAN: the ecosystem preference applies only among candidates that satisfy the constraints, and an
			// unproved candidate has not been shown to. It still wins wherever no proved one is feasible.
			List<Integer> proofs = new ArrayList<>();
			for (var rule : proofLiterals.entrySet()) if (!relaxed.contains(ruleLiterals.get(rule.getKey()))) proofs.add(rule.getValue());
			accept(solver, assumptions, proofs, new HashSet<>());
			for (String id : decisionOrder()) {
				List<Path> candidates = new ArrayList<>(identities.get(id)); candidates.sort(candidateOrder(rootIds.contains(id), id));
				if (!rootIds.contains(id)) {
					VecInt absent = copy(assumptions); for (Path candidate : candidates) absent.push(-variable(candidate));
					if (satisfiable(solver, absent)) { assumptions = absent; continue; }
				}
				for (Path candidate : candidates) {
					VecInt attempt = copy(assumptions); attempt.push(variable(candidate));
					if (satisfiable(solver, attempt)) { assumptions = attempt; break; }
				}
			}
			if (!satisfiable(solver, assumptions)) return new Search(fallback(), JointCandidateSelector.Status.UNSATISFIABLE, Set.of(), Map.of(), impossible);
			selected = fromModel();
			// A pin whose build exists but cannot be combined with an earlier pin or with what the bundling
			// structure requires is the player's to resolve: a real conflict.
			boolean conflict = !refused.isEmpty();
			Set<JointCandidateSelector.Rule> unavoidable = new LinkedHashSet<>();
			for (var rule : ruleLiterals.entrySet()) if (relaxed.contains(rule.getValue())) {
				if (satisfiable(solver, new VecInt(new int[] {rule.getValue()}))) conflict = true; else unavoidable.add(rule.getKey());
			}
			return new Search(selected, conflict ? JointCandidateSelector.Status.UNSATISFIABLE : JointCandidateSelector.Status.SOLVED,
					unavoidable, refused, impossible);
		} catch (TimeoutException bounded) {
			// The last model always satisfies everything accepted so far (the structure, the settled pins, the
			// contracts and choices taken before the bound). Before the pins are settled it may be the first,
			// purely structural model, which never saw a pin: the fallback then keeps every pinned build instead.
			// Ids the search had not reached yet keep that model's choice, which is why this is never SOLVED.
			return new Search(selected != null ? selected : lastModel != null && pinsSettled ? fromModel() : fallback(),
					JointCandidateSelector.Status.SEARCH_LIMIT, Set.of(), pinsSettled ? refused : Map.of(), impossible);
		}
	}

	/**
	 * Accepts {@code literals} greedily in order: each is kept iff it is consistent with everything kept before it.
	 * Halving only saves solver calls (a consistent block is kept whole); the result equals the one-by-one pass.
	 */
	private void accept(ISolver solver, VecInt assumptions, List<Integer> literals, Set<Integer> relaxed) throws TimeoutException {
		if (literals.isEmpty()) return;
		VecInt attempt = copy(assumptions); for (int literal : literals) attempt.push(literal);
		if (satisfiable(solver, attempt)) { for (int literal : literals) assumptions.push(literal); return; }
		if (literals.size() == 1) { relaxed.add(literals.getFirst()); assumptions.push(-literals.getFirst()); return; }
		int half = literals.size() / 2;
		accept(solver, assumptions, literals.subList(0, half), relaxed);
		accept(solver, assumptions, literals.subList(half, literals.size()), relaxed);
	}

	private void structure(ISolver solver) throws ContradictionException {
		for (var node : graph.nodes().values()) {
			if (node.excluded()) { clause(solver, -variable(node.path())); continue; }
			if (node.root() && node.claim() != null && node.claim().modIds().isEmpty()) {
				// An installed runtime bundle has no identity group to require its root; its declared
				// libraries are nevertheless requested by the user just like a mod's bundled libraries.
				clause(solver, variable(node.path()));
			}
			if (!node.root()) {
				List<Integer> reachable = new ArrayList<>(List.of(-variable(node.path())));
				for (Path parent : parents.getOrDefault(node.path(), List.of())) reachable.add(variable(parent));
				clause(solver, reachable);
			}
		}
		for (var group : identities.entrySet()) {
			List<Path> candidates = group.getValue();
			for (int a = 0; a < candidates.size(); a++) for (int b = a + 1; b < candidates.size(); b++) {
				if (!graph.payloadRelated(candidates.get(a), candidates.get(b))) clause(solver, -variable(candidates.get(a)), -variable(candidates.get(b)));
			}
			if (rootIds.contains(group.getKey())) clause(solver, candidates.stream().map(this::variable).toList());
		}
		for (var edge : graph.edges()) {
			var child = graph.nodes().get(edge.child()); if (child.excluded()) continue;
			if (edge.payload() || (child.claim() == null && edge.coordinate() == null)) {
				clause(solver, -variable(edge.parent()), variable(edge.child()));
			} else if (edge.coordinate() != null) {
				List<Integer> required = new ArrayList<>(List.of(-variable(edge.parent())));
				for (Path provider : edgeProviders(edge)) required.add(variable(provider));
				clause(solver, required);
			} else if (child.claim() != null) {
				for (String id : child.claim().modIds()) {
					List<Integer> required = new ArrayList<>(List.of(-variable(edge.parent())));
					for (Path provider : identities.get(JointCandidateSelector.key(id))) required.add(variable(provider));
					clause(solver, required);
				}
			}
		}
	}

	private boolean satisfiable(ISolver solver, VecInt assumptions) throws TimeoutException {
		// The last model already answers any trial it satisfies; only a real question costs a solver call.
		if (lastModel != null) {
			boolean known = true;
			for (int i = 0; i < assumptions.size() && known; i++) { int literal = assumptions.get(i); known = lastModel[Math.abs(literal)] == literal > 0; }
			if (known) return true;
		}
		if (++checks > checkLimit) throw new TimeoutException("bounded candidate search");
		if (!solver.isSatisfiable(assumptions)) return false;
		int[] values = solver.model(); int size = solver.nVars();
		for (int literal : values) size = Math.max(size, Math.abs(literal));
		boolean[] model = new boolean[size + 1];
