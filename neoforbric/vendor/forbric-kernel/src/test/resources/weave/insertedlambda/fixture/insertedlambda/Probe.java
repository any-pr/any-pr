package fixture.insertedlambda;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.LevelRenderState;

/** Renders one main pass and reports what was drawn, in order. */
public class Probe {
	public String probe() {
		LevelRenderer renderer = new LevelRenderer();
		renderer.addMainPass(new LevelRenderState("state"), new ChunkSectionsToRender("sections"));
		return String.valueOf(renderer.trace);
	}
}
