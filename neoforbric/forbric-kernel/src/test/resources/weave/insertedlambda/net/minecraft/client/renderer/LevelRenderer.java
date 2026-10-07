package net.minecraft.client.renderer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4fc;

/**
 * Fixture stand-in for a byte-merged renderer: two bodies named lambda$addMainPass$0, which javac never emits and only a
 * merge leaves behind. The live one is the carrier's, with a pose inserted before the sections; vanilla's, without it,
 * is reached by nothing and is the half KernelBoot's DuplicateLambdaPruneInjector removes before Mixin looks.
 */
public class LevelRenderer {
	public final List<String> trace = new ArrayList<>();

	public void addMainPass(LevelRenderState state, ChunkSectionsToRender sections) {
		lambda$addMainPass$0(state, new Matrix4fc("pose"), sections, 0.25F);
	}

	private void lambda$addMainPass$0(LevelRenderState state, Matrix4fc pose, ChunkSectionsToRender sections, float alpha) {
		sections.renderGroup(trace, "opaque");
		sections.renderGroup(trace, "translucent");
	}

	private void lambda$addMainPass$0(LevelRenderState state, ChunkSectionsToRender sections, float alpha) {
		sections.renderGroup(trace, "everything");
	}
}
