package fixture.mergedtwin.mod;

import fixture.mergedtwin.PayloadCodec;
import fixture.mergedtwin.Payloads;

/** The mod's own code path: encode through both halves, then cast the codec the game really uses. */
public class Probe {
	public String run() {
		System.out.println("[MergedTwin] vanilla encode=" + Payloads.VANILLA.encode("hello"));
		PayloadCodec live = Payloads.live();
		System.out.println("[MergedTwin] live encode=" + live.encode("hello"));
		// Bad Packets' shape: a second piece of the mod trusts that the live codec carries its interface.
		return ((ChannelHolder) live).mergedtwin$channel();
	}
}
