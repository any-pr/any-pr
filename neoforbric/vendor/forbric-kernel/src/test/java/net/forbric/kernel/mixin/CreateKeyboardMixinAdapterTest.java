/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.mixin;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CreateKeyboardMixinAdapterTest {
	@TempDir Path root;
	@Test void releasedNativeBodiesDispatchReleasePressAndRepeatExactlyOnce() throws Exception {
		var node = CreateGuestMixinFixture.mixin(CreateKeyboardMixinAdapter.MIXIN);
		assertEquals(2, CreateKeyboardMixinAdapter.adapt(node, CarpetMixinAdapterTest::target));
		CarpetMixinAdapterTest.verify(node);
		assertEquals(0, CreateKeyboardMixinAdapter.adapt(node, CarpetMixinAdapterTest::target));
		Map<String, String> sources = Map.of(
				CreateKeyboardMixinAdapter.MIXIN + ".java", "package com.zurrtum.create.client.mixin; public class KeyboardHandlerMixin { public java.util.List<Boolean> calls=new java.util.ArrayList<>(); public Object event; private void onKey(net.minecraft.client.input.KeyEvent e,boolean p){event=e;calls.add(p);} }",
				"net/minecraft/client/input/KeyEvent.java", "package net.minecraft.client.input; public class KeyEvent {}",
				"org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java", "package org.spongepowered.asm.mixin.injection.callback; public class CallbackInfo {}");
		try (var loader = CreateGuestMixinFixture.executable(root, node, sources, m -> m.desc.equals(CreateKeyboardMixinAdapter.HANDLER))) {
			Class<?> type = loader.loadClass(node.name.replace('/', '.')), eventType = loader.loadClass("net.minecraft.client.input.KeyEvent"), callbackType = loader.loadClass("org.spongepowered.asm.mixin.injection.callback.CallbackInfo");
			Object receiver = type.getConstructor().newInstance(), event = eventType.getConstructor().newInstance();
			var released = type.getMethod("onKeyReleased", long.class, int.class, eventType, callbackType);
			var pressed = type.getMethod("onKey", long.class, int.class, eventType, callbackType);
			for (int action : new int[]{0, 1, 2}) {
				((List<?>) type.getField("calls").get(receiver)).clear();
				released.invoke(receiver, 0L, action, event, null); pressed.invoke(receiver, 0L, action, event, null);
				assertEquals(List.of(action != 0), type.getField("calls").get(receiver));
				assertSame(event, type.getField("event").get(receiver));
			}
		}
	}
	@Test void vanillaKeepsTheOriginalReturnSelectors() throws Exception {
		var node = CreateGuestMixinFixture.mixin(CreateKeyboardMixinAdapter.MIXIN); byte[] before = CarpetMixinAdapterTest.bytes(node);
		assertEquals(0, CreateKeyboardMixinAdapter.adapt(node, name -> {try {return StagedFabricMixinFixture.game(name, true);} catch(Exception e){throw new AssertionError(e);}}));
		assertArrayEquals(before, CarpetMixinAdapterTest.bytes(node));
	}
}
