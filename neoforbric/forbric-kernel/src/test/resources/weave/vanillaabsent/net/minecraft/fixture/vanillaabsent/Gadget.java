package net.minecraft.fixture.vanillaabsent;

/**
 * A merged-base class in miniature, in the game's package so the kernel judges it as one. It has no populateReport()
 * and nothing in vanilla's difference table says vanilla had one: the mod's name is not a method of this game at all,
 * as Not Enough Crashes' populateCrashReport is not a method of 26.2's BlockEntity.
 */
public class Gadget {
	public String label() {
		return "gadget";
	}

	public void fillReport(StringBuilder report) {
		report.append(label());
	}
}
