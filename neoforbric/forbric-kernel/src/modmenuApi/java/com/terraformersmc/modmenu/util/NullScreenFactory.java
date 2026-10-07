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

package com.terraformersmc.modmenu.util;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import net.minecraft.client.gui.screens.Screen;

/**
 * Stand-in for Mod Menu's {@code NullScreenFactory}: what {@code ModMenuApi.getModConfigScreenFactory()} returns by
 * default, and how a reader tells "this mod has no config screen" from a factory. Mod Menu skips an instance of it
 * when it reads the entrypoints; so does the kernel. A mod that overrides the method and falls back to
 * {@code ModMenuApi.super.getModConfigScreenFactory()} -- several do, when their config library is missing -- is saying
 * it has none, and must not get a Config button that opens nothing.
 */
public class NullScreenFactory<S extends Screen> implements ConfigScreenFactory<S> {
	@Override
	public S create(Screen parent) {
		return null;
	}
}
