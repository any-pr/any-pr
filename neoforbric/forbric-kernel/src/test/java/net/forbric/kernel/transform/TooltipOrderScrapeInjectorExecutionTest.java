/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.api.EnvType;

/**
 * {@link TooltipOrderScrapeInjector}'s output, run: the edited {@code ItemStack} loads and builds the same tooltip as
 * before, and a scrape of {@code addDetailsToTooltip} through the class's own loader (what fabric-item-api's
 * tooltip-order registry does at class initialisation) finds vanilla's component order, where as merged it finds
 * nothing and the registry dies with "Found no component types".
 *
 * <p>The stand-in {@code ItemStack} has NeoForge's shape: {@code addDetailsToTooltip} only dispatches, and vanilla's
 * body, which reads each {@code DataComponents} type in order and adds the attribute tooltips, lives under
 * {@code addDetailsToTooltipComponents}. Its {@code DataComponents} also declares {@code ATTRIBUTE_MODIFIERS}, as vanilla's
 * does: the edited head reads that field as the attribute call's marker, so running it links against it. The scrape below
 * is this test's own reading of the rule the registry follows.
 */
@ExecutesInjector(TooltipOrderScrapeInjector.class)
class TooltipOrderScrapeInjectorExecutionTest {
	private static final String STACK = TooltipOrderScrapeInjector.ITEM_STACK;
	private static final List<String> COMPONENTS = IntStream.range(0, 20).mapToObj(i -> "COMPONENT_" + i).toList();

	private static Map<String, String> standIns() {
		String fields = COMPONENTS.stream().map(name -> "\tpublic static final DataComponentType " + name
				+ " = new DataComponentType(\"" + name.toLowerCase() + "\");").collect(Collectors.joining("\n"));
		StringBuilder body = new StringBuilder();
		for (int i = 0; i < COMPONENTS.size(); i++) {
			body.append("\t\taddToTooltip(DataComponents.").append(COMPONENTS.get(i)).append(", lines);\n");
			if (i == 9) body.append("\t\taddAttributeTooltips(lines, display, player);\n");
		}
		return Map.of(
				"net.minecraft.core.component.DataComponentType",
				"package net.minecraft.core.component; public record DataComponentType(String name) { }",
				"net.minecraft.core.component.DataComponents",
				"package net.minecraft.core.component;\npublic final class DataComponents {\n" + fields
						+ "\n\tpublic static final DataComponentType ATTRIBUTE_MODIFIERS = new DataComponentType(\"attribute_modifiers\");\n}\n",
				"net.minecraft.world.item.Item", "package net.minecraft.world.item; public class Item { public interface TooltipContext { } }",
				"net.minecraft.world.item.component.TooltipDisplay", "package net.minecraft.world.item.component; public class TooltipDisplay { }",
				"net.minecraft.world.entity.player.Player", "package net.minecraft.world.entity.player; public class Player { }",
				"net.minecraft.world.item.TooltipFlag", "package net.minecraft.world.item; public interface TooltipFlag { }",
				STACK, """
						package net.minecraft.world.item;

						import java.util.function.Consumer;
						import net.minecraft.core.component.DataComponentType;
						import net.minecraft.core.component.DataComponents;
						import net.minecraft.world.entity.player.Player;
						import net.minecraft.world.item.component.TooltipDisplay;

						public class ItemStack {
							/** NeoForge's dispatcher: no component read of its own. */
							public void addDetailsToTooltip(Item.TooltipContext context, TooltipDisplay display, Player player, TooltipFlag flag,
									Consumer<String> lines) {
								lines.accept("name");
								addDetailsToTooltipComponents(context, display, player, flag, lines);
							}

							private void addDetailsToTooltipComponents(Item.TooltipContext context, TooltipDisplay display, Player player,
									TooltipFlag flag, Consumer<String> lines) {
						%s	}

							private void addToTooltip(DataComponentType type, Consumer<String> lines) {
								lines.accept(type.name());
							}

							private void addAttributeTooltips(Consumer<String> lines, TooltipDisplay display, Player player) {
								lines.accept("attributes");
							}
						}
						""".formatted(body));
	}

	/** The registry's rule: each DataComponents type the method reads, in order, once; the attribute call is its marker. */
	private static List<String> scrape(ClassLoader loader) throws Exception {
		ClassNode node = new ClassNode();
		try (InputStream in = loader.getResourceAsStream(TooltipOrderScrapeInjector.ITEM_STACK_INTERNAL + ".class")) {
			new ClassReader(in.readAllBytes()).accept(node, 0);
		}
		MethodNode method = node.methods.stream().filter(m -> m.name.equals(TooltipOrderScrapeInjector.METHOD)).findFirst().orElseThrow();
		Set<String> order = new LinkedHashSet<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
					&& field.owner.equals(TooltipOrderScrapeInjector.DATA_COMPONENTS)) order.add(field.name);
			if (insn instanceof MethodInsnNode call && call.name.equals(TooltipOrderScrapeInjector.ATTRIBUTE_TOOLTIPS)) {
				order.add(TooltipOrderScrapeInjector.ATTRIBUTE_MODIFIERS);
			}
		}
		if (order.isEmpty()) throw new IllegalStateException("Found no component types");
		return List.copyOf(order);
	}

	private static List<String> tooltip(ClassLoader loader) throws Throwable {
		List<String> lines = new ArrayList<>();
		Object stack = InjectorExecution.construct(loader.loadClass(STACK));
		InjectorExecution.invoke(stack, "addDetailsToTooltip", null, InjectorExecution.construct(loader.loadClass(
				"net.minecraft.world.item.component.TooltipDisplay")), null, null, (Consumer<String>) lines::add);
		return lines;
	}

	@Test void theRegistryFindsVanillasOrderAndTheTooltipIsUnchanged(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = InjectorExecution.compile(work, standIns());
		String internal = TooltipOrderScrapeInjector.ITEM_STACK_INTERNAL;
		byte[] scrapeable = InjectorExecution.transform(new TooltipOrderScrapeInjector(), STACK, original.get(internal), EnvType.CLIENT);
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(internal, scrapeable);
		ClassLoader loader = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(scrapeable, loader));

		List<String> expected = new ArrayList<>(COMPONENTS.subList(0, 10));
		expected.add(TooltipOrderScrapeInjector.ATTRIBUTE_MODIFIERS);
		expected.addAll(COMPONENTS.subList(10, 20));
		assertEquals(expected, scrape(loader), "vanilla's component order, with the attribute marker where vanilla adds them");
		ClassLoader merged = InjectorExecution.load(original);
		assertEquals(tooltip(merged), tooltip(loader), "the edit is read-only to the tooltip: the same lines in the same order");
		assertEquals("Found no component types", assertThrows(IllegalStateException.class, () -> scrape(merged)).getMessage(),
				"premise: as merged, the registry's class initialiser throws");
		assertSame(scrapeable, InjectorExecution.transform(new TooltipOrderScrapeInjector(), STACK, scrapeable, EnvType.CLIENT),
				"a dispatcher that already lists the order is left alone");
	}
}
