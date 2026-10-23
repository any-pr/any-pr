package fixture.barrelroll;

import net.minecraft.client.Camera;

/** Aligns one camera per view and reports the roll that reached its rotation and which roll hooks ran. */
public class Probe {
	public String probe() {
		StringBuilder out = new StringBuilder();
		String[] names = {"minecart", "ordinary", "mirrored", "sleeping"};
		for (int mode : new int[] {Camera.ORDINARY, Camera.MIRRORED, Camera.SLEEPING, Camera.MINECART}) {
			Flight.RAN.clear();
			Camera camera = new Camera();
			camera.mode = mode;
			camera.eventRoll = 5.0F;
			camera.alignWithEntity(0.5F);
			out.append(out.length() == 0 ? "" : " ").append(names[mode]).append('=').append(camera.roll()).append(Flight.RAN);
		}
		return out.toString();
	}
}
