package fixture.transferaudit;

import java.lang.reflect.Method;
import java.util.Arrays;

import net.minecraftforge.energy.EnergyStorage;

/**
 * Receives energy into both stores, then reports per class whether the defined class carries the certificate's marker
 * and what the audit says about it, the way the kernel's transfer adapters ask.
 */
public class Probe {
	public String probe() throws ReflectiveOperationException {
		int reviewed = new EnergyStorage(10).receiveEnergy(4, false);
		int unreviewed = new UnreviewedStorage(10).receiveEnergy(6, false);
		return describe(EnergyStorage.class, reviewed) + " " + describe(UnreviewedStorage.class, unreviewed) + " hooks=" + Trace.SEEN;
	}

	private static String describe(Class<?> type, int received) throws ReflectiveOperationException {
		boolean marker = Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> m.getName().equals("forbric$auditedTransferSnapshot"));
		Method declined = Class.forName("net.forbric.kernel.transform.ForgeTransferShapeAudit").getMethod("declined", String.class);
		return type.getSimpleName() + "[received=" + received + " marker=" + marker + " declined=" + declined.invoke(null, type.getName()) + "]";
	}
}
