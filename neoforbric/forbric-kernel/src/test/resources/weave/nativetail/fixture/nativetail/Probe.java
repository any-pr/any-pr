package fixture.nativetail;

import com.mojang.blaze3d.IndexType;

/** Takes both of least's paths — the one vanilla returns early from (INT), then the tail (SHORT) — and reports. */
public class Probe {
	public String probe() {
		IndexType large = IndexType.least(1 << 20);
		IndexType small = IndexType.least(16);
		return "least(1048576)=" + large + " least(16)=" + small + " tail saw " + Seen.all();
	}
}
