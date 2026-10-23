package fixture.selftest;

/** One target two mods' configs both modify; what entries() returns records the order their mixins applied in. */
public class Ledger {
	public String entries() {
		return "base";
	}
}
