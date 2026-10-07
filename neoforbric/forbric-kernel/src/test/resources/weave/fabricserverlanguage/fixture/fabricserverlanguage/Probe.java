package fixture.fabricserverlanguage;

import java.util.Map;
import java.util.TreeMap;

import net.minecraft.locale.Language;

/** Reads the default language file through the two-argument entry point, as the server's language loader does. */
public class Probe {
	public String run() {
		Map<String, String> read = new TreeMap<>();
		Language.parseTranslations(read::put, "/assets/minecraft/lang/en_us.json");
		return read.toString();
	}
}
