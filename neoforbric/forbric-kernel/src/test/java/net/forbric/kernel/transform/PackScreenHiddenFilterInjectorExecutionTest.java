/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.api.EnvType;

/**
 * {@link PackScreenHiddenFilterInjector}'s output, run: the resource-pack screen's list leaves out a pack that says it
 * is hidden, and still lists the rest in the order they came.
 *
 * <p>{@code PackScreenHiddenFilterInjectorTest} reads the edit back; this defines it. The stand-ins keep the shape
 * the edit keys on: {@code updateList} hands its stream parameter straight to {@code forEach}, and the entry
 * interface has {@code notHidden()} with a default, overridden by the base class from the pack's own flag.
 */
@ExecutesInjector(PackScreenHiddenFilterInjector.class)
class PackScreenHiddenFilterInjectorExecutionTest {
	private static final String MODEL = "net.minecraft.client.gui.screens.packs.PackSelectionModel";
	private static final String LIST = PackScreenHiddenFilterInjector.LIST.replace('/', '.');

	private static final Map<String, String> STAND_INS = Map.of(
			MODEL, """
					package net.minecraft.client.gui.screens.packs;

					public class PackSelectionModel {
						public interface Entry {
							String getId();

							default boolean notHidden() {
								return true;
							}
						}

						public static class EntryBase implements Entry {
							private final String id;
							private final boolean hidden;

							public EntryBase(String id, boolean hidden) {
								this.id = id;
								this.hidden = hidden;
							}

							@Override
							public String getId() {
								return id;
							}

							@Override
							public boolean notHidden() {
								return !hidden;
							}
						}

						/** An entry that keeps the interface's default, as a mod's own Entry might. */
						public static Entry plain(String id) {
							return () -> id;
						}
					}
					""",
			LIST, """
					package net.minecraft.client.gui.screens.packs;

					import java.util.ArrayList;
					import java.util.List;
					import java.util.stream.Stream;

					public class TransferableSelectionList {
						public final List<String> rows = new ArrayList<>();

						public void updateList(Stream<PackSelectionModel.Entry> entries, PackSelectionModel.EntryBase transferred) {
							rows.clear();
							entries.forEach(entry -> rows.add(entry.getId()));
						}
					}
					""");

	private static List<?> rows(ClassLoader loader) throws Throwable {
		Class<?> base = loader.loadClass(MODEL + "$EntryBase");
		Class<?> model = loader.loadClass(MODEL);
		List<Object> entries = List.of(
				InjectorExecution.construct(base, "vanilla", false),
				InjectorExecution.construct(base, "fabric-api assets", true),
				InjectorExecution.invokeStatic(model, "plain", "a mod's own entry"),
				InjectorExecution.construct(base, "neoforge assets", true),
				InjectorExecution.construct(base, "programmer art", false));
		Object list = InjectorExecution.construct(loader.loadClass(LIST));
		InjectorExecution.invoke(list, "updateList", entries.stream(), null);
		return (List<?>) list.getClass().getField("rows").get(list);
	}

	@Test void hiddenPacksAreNoLongerRowsInTheScreen(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = InjectorExecution.compile(work, STAND_INS);
		byte[] list = original.get(PackScreenHiddenFilterInjector.LIST);
		byte[] filtered = InjectorExecution.transform(new PackScreenHiddenFilterInjector(), LIST, list, EnvType.CLIENT);
		assertNotSame(list, filtered, "the injector did not touch updateList");
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(PackScreenHiddenFilterInjector.LIST, filtered);
		ClassLoader loader = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(filtered, loader));

		assertEquals(List.of("vanilla", "a mod's own entry", "programmer art"), rows(loader));
		assertEquals(List.of("vanilla", "fabric-api assets", "a mod's own entry", "neoforge assets", "programmer art"),
				rows(InjectorExecution.load(original)), "premise: as merged, every asset pack is a row the player cannot remove");
		assertSame(filtered, InjectorExecution.transform(new PackScreenHiddenFilterInjector(), LIST, filtered, EnvType.CLIENT),
				"an already filtered list is left alone");
	}

	@Test void otherClassesAreLeftAlone(@TempDir Path work) throws Exception {
		Map<String, byte[]> original = InjectorExecution.compile(work, STAND_INS);
		byte[] model = original.get(MODEL.replace('.', '/'));
		assertSame(model, InjectorExecution.transform(new PackScreenHiddenFilterInjector(), MODEL, model, EnvType.CLIENT));
	}
}
