package fixture.fabricsectioncompiler;

import java.util.List;

import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;

/** Compiles one section through the overload the merged game calls, and reports the quads it holds. */
public class Probe {
	public String run() {
		SectionCompiler.Results results = new SectionCompiler().compile(new SectionPos(0, 4, 0),
				new RenderSectionRegion(List.of("stone", "ctm_glass")), new VertexSorting(), new SectionBufferBuilderPack(),
				List.of("fluid"));
		return "quads=" + results.quads;
	}
}
