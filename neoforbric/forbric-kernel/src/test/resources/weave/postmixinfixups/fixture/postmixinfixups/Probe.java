package fixture.postmixinfixups;

import net.minecraft.client.renderer.block.model.BakedQuad;

/** Builds one quad through each constructor and asks the guest's added method about each. */
public class Probe {
	public String probe() {
		BakedQuad narrow = new BakedQuad(new int[] {1}, 0, "north");
		BakedQuad wide = new BakedQuad(new int[] {2}, 0, "up", "baked", "tinted");
		return "narrow=" + ((QuadView) (Object) narrow).getNormalFace() + " wide=" + ((QuadView) (Object) wide).getNormalFace();
	}
}
