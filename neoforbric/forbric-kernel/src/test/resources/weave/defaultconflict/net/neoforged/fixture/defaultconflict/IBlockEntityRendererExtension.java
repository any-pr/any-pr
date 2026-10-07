package net.neoforged.fixture.defaultconflict;

/** The base's extension interface: on the merged base the renderer already has this one before any mod runs. */
public interface IBlockEntityRendererExtension<T> {
	default String getRenderBoundingBox(T blockEntity) {
		return "neoforge:" + blockEntity;
	}
}
