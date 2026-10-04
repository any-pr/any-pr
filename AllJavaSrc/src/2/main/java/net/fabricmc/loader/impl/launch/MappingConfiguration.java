/*
 * Copyright 2016 FabricMC
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

package net.fabricmc.loader.impl.launch;

import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class MappingConfiguration {
	private static final boolean FIX_PACKAGE_ACCESS = SystemProperties.isSet(SystemProperties.FIX_PACKAGE_ACCESS);

	// same ns between client and server
	public static final String OFFICIAL_NAMESPACE = "official";
	// separate client/server ns
	public static final String CLIENT_OFFICIAL_NAMESPACE = "clientOfficial";
	public static final String SERVER_OFFICIAL_NAMESPACE = "serverOfficial";

	public static final String INTERMEDIARY_NAMESPACE = "intermediary";
	public static final String NAMED_NAMESPACE = "named";

	private boolean initializedMetadata;
	private boolean initializedMappings;
	private MappingSource mappingSource;

	private String namespace;

	@Nullable
	private String gameId;
	@Nullable
	private String gameVersion;
	@Nullable
	private String mappingName;
	@Nullable
	private List<String> namespaces;
	@Nullable
	private MemoryMappingTree mappings;

	// ========== 强制使用官方命名空间，并且不加载任何映射 ==========
	public String getRuntimeNamespace() {
		return OFFICIAL_NAMESPACE;
	}

	public String getDefaultModDistributionNamespace() {
		return OFFICIAL_NAMESPACE;
	}

	@Nullable
	public String getGameId() {
		return null;
	}

	@Nullable
	public String getGameVersion() {
		return null;
	}

	@Nullable
	public String getMappingName() {
		return null;
	}

	@Nullable
	public List<String> getNamespaces() {
		return namespaces;
	}

	public boolean matches(String gameId, String gameVersion) {
		return true;
	}

	public MappingTree getMappings() {
		if (mappings == null) {
			mappings = new MemoryMappingTree();
			// setSrcNamespace 不抛出 IOException，但可能抛出 IllegalStateException，
			// 这里不会出现，因为这是新对象
			mappings.setSrcNamespace(OFFICIAL_NAMESPACE);
		}
		return mappings;
	}

	public boolean hasAnyMappings() {
		return false;
	}

	public boolean requiresPackageAccessHack() {
		return false;
	}

	private void initializeMappings(boolean metaOnly) {
		// 完全跳过
	}

	private MappingSource getMappingSource() {
		return null;
	}

	// 构造函数
	public MappingConfiguration() {
		mappings = new MemoryMappingTree();
		mappings.setSrcNamespace(OFFICIAL_NAMESPACE);
		namespaces = Collections.singletonList(OFFICIAL_NAMESPACE);
		initializedMetadata = true;
		initializedMappings = true;
	}

	// 保留原有内部类（但不会用到）
	private static final class MappingSource {
		final URL url;
		final Path path;

		MappingSource(URL url, Path path) {
			this.url = url;
			this.path = path;
		}
	}
}