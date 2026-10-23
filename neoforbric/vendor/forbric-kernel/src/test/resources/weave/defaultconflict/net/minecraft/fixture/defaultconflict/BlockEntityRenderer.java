package net.minecraft.fixture.defaultconflict;

import net.neoforged.fixture.defaultconflict.IBlockEntityRendererExtension;

/** The merged base's renderer interface: vanilla's, already extended by the base's own extension. */
public interface BlockEntityRenderer<T> extends IBlockEntityRendererExtension<T> {
}
