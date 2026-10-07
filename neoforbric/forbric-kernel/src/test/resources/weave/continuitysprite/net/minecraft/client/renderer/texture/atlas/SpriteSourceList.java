package net.minecraft.client.renderer.texture.atlas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * A stand-in for the merged base's SpriteSourceList: the carrier's list(ResourceManager, Set) carries the body, with
 * the loader map in local 3 and one ImmutableList.builder() call, and vanilla's list(ResourceManager) is left as a
 * stub that delegates to it.
 */
public class SpriteSourceList {
	private final List<String> sources;

	public SpriteSourceList(List<String> sources) {
		this.sources = sources;
	}

	public List<String> list(ResourceManager resourceManager) {
		return list(resourceManager, Set.of());
	}

	public List<String> list(ResourceManager resourceManager, Set<String> additionalMetadata) {
		Map<String, String> sprites = new HashMap<>();
		sources.forEach(source -> sprites.put(source, "sprite:" + source));
		ImmutableList.Builder<String> result = ImmutableList.builder();
		result.addAll(sprites.values());
		return result.build().asList();
	}
}
