package fixture.selftest;

/** Target of a mixin listed only under "client": title() answers "client" on a client and "panel" on a server. */
public class Panel {
	public String title() {
		return "panel";
	}
}
