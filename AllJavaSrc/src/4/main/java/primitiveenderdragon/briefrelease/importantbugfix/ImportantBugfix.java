package primitiveenderdragon.briefrelease.importantbugfix;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportantBugfix implements ModInitializer {
	public static final String MOD_ID = "important-bug-fix";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Mixin Mod Fix Bug!");
	}
}
