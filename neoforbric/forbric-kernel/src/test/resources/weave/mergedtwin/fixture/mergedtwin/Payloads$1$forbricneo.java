package fixture.mergedtwin;

/**
 * The renamed twin: the other ecosystem's copy of {@code Payloads$1}, with the same body under the suffix the
 * byte merge gives a class whose name collided. Its call to {@code findCodec} is owned by THIS class.
 */
public final class Payloads$1$forbricneo implements PayloadCodec {
	@Override
	public String encode(String id) {
		return findCodec(id) + ":" + id;
	}

	public String findCodec(String id) {
		return "plain";
	}
}
