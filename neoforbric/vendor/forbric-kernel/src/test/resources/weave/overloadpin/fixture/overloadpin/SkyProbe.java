package fixture.overloadpin;

/**
 * The harness's probe, kept apart from {@link SkyPass} on purpose. Mixin selects its configs — and so runs
 * {@link MergePrunePlugin#onLoad} — while transforming the FIRST class the game loader defines; were that SkyPass, its
 * own bytes would already be past the (then empty) pre-Mixin chain. Loading this first means SkyPass is read through
 * the installed chain, as it is in a real boot, where KernelBoot installs the chain before any game class loads.
 */
public class SkyProbe {
	public String render() {
		return new SkyPass().render();
	}
}
