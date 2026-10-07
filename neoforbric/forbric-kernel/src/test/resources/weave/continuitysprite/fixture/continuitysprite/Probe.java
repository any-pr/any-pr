package fixture.continuitysprite;

import java.util.List;

import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;

/**
 * Lists one atlas through vanilla's entry point, in a pack that has an emissive texture for "glow". The loaders the
 * list returns say which of the mod's hooks ran: the extra source its constructor hook adds, and the emissive loader its
 * list hook adds to the loader map.
 */
public class Probe {
	public String probe() {
		SpriteSourceList atlas = new SpriteSourceList(List.of("stone", "glow"));
		return String.join(", ", atlas.list(texture -> texture.equals("glow_e")).stream().sorted().toList());
	}
}
