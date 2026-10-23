package net.minecraft.client.renderer.chunk;

import java.util.List;

/** Fixture stand-in: draws one layer group of the frame's sections. */
public record ChunkSectionsToRender(String name) {
	public void renderGroup(List<String> trace, String group) {
		trace.add(group);
	}

	@Override
	public String toString() {
		return name;
	}
}
