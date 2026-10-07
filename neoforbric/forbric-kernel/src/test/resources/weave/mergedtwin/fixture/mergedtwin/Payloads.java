package fixture.mergedtwin;

/**
 * A fake game class whose anonymous codec exists twice in the merged base, the shape of CustomPacketPayload$1.
 *
 * <p>{@code VANILLA} is javac's {@code Payloads$1}: the only name a mod compiled against the vanilla game knows.
 * {@link #live()} is what the merged code that actually runs hands out — the copy the byte merge had to rename.
 */
public final class Payloads {
	public static final PayloadCodec VANILLA = new PayloadCodec() {
		@Override
		public String encode(String id) {
			return findCodec(id) + ":" + id;
		}

		public String findCodec(String id) {
			return "plain";
		}
	};

	private Payloads() {
	}

	public static PayloadCodec live() {
		return new Payloads$1$forbricneo();
	}
}
