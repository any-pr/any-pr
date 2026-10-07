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

package com.terraformersmc.modmenu.api;

import net.minecraft.network.chat.Component;

/** Stand-in for Mod Menu's {@code UpdateInfo}, for the same reason as {@link UpdateChecker}. See {@link ModMenuApi}. */
public interface UpdateInfo {
	boolean isUpdateAvailable();

	default Component getUpdateMessage() {
		return null;
	}

	String getDownloadLink();

	UpdateChannel getUpdateChannel();
}
