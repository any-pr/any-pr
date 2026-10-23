package fixture.mergedtwin;

/** The fake game's codec type; both halves of the merged anonymous class implement it. */
public interface PayloadCodec {
	String encode(String id);
}
