package fixture.defaultconflict;

/** The mod's own extension, which its mixin puts on the vanilla renderer interface. */
public interface BlockEntityRenderFabricExtension<T> {
	default String getRenderBoundingBox(T blockEntity) {
		return "fabric:" + blockEntity;
	}
}
