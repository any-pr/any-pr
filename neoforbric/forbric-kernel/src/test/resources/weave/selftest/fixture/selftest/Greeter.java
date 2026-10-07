package fixture.selftest;

/** The weave harness's own target: returns "plain" unless the real Mixin pipeline wove GreeterMixin into it. */
public class Greeter {
	public String greet() {
		return "plain";
	}
}
