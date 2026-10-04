package net.minecraft.client.renderer.chunk;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public class CompiledSectionMesh implements SectionMesh {
    public static final SectionMesh UNCOMPILED = new SectionMesh() {
        @Override
        public boolean facesCanSeeEachother(final Direction direction1, final Direction direction2) {
            return false;
        }
    };
    public static final SectionMesh EMPTY = new SectionMesh() {
        @Override
        public boolean facesCanSeeEachother(final Direction direction1, final Direction direction2) {
            return true;
        }
    };
    private final List<BlockEntity> renderableBlockEntities;
    private final VisibilitySet visibilitySet;
    private final MeshData.@Nullable SortState transparencyState;
    private @Nullable TranslucencyPointOfView translucencyPointOfView;
    private final Map<ChunkSectionLayer, SectionBuffers> buffers = new EnumMap<>(ChunkSectionLayer.class);

    public CompiledSectionMesh(final TranslucencyPointOfView translucencyPointOfView, final SectionCompiler.Results results) {
        this.translucencyPointOfView = translucencyPointOfView;
        this.visibilitySet = results.visibilitySet;
        this.renderableBlockEntities = results.blockEntities;
        this.transparencyState = results.transparencyState;
    }

    public void setTranslucencyPointOfView(final TranslucencyPointOfView translucencyPointOfView) {
        this.translucencyPointOfView = translucencyPointOfView;
    }

    @Override
    public boolean isDifferentPointOfView(final TranslucencyPointOfView pointOfView) {
        return !pointOfView.equals(this.translucencyPointOfView);
    }

    @Override
    public boolean hasRenderableLayers() {
        return !this.buffers.isEmpty();
    }

    @Override
    public boolean isEmpty(final ChunkSectionLayer layer) {
        return !this.buffers.containsKey(layer);
    }

    @Override
    public List<BlockEntity> getRenderableBlockEntities() {
        return this.renderableBlockEntities;
    }

    @Override
    public boolean facesCanSeeEachother(final Direction direction1, final Direction direction2) {
        return this.visibilitySet.visibilityBetween(direction1, direction2);
    }

    @Override
    public @Nullable SectionBuffers getBuffers(final ChunkSectionLayer layer) {
        return this.buffers.get(layer);
    }

    // ===== MCRe: 参数改为 SectionPos =====
    public void uploadMeshLayer(final ChunkSectionLayer layer, final MeshData mesh, final SectionPos sectionPos) {
        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        SectionBuffers sectionBuffers = this.getBuffers(layer);
        int x = sectionPos.x();
        int y = sectionPos.y();
        int z = sectionPos.z();

        if (sectionBuffers != null) {
            if (sectionBuffers.getVertexBuffer().size() < mesh.vertexBuffer().remaining()) {
                sectionBuffers.getVertexBuffer().close();
                sectionBuffers.setVertexBuffer(
                        RenderSystem.getDevice()
                                .createBuffer(
                                        () -> "Section vertex buffer - layer: "
                                                + layer.label()
                                                + "; cords: "
                                                + x + ", " + y + ", " + z,
                                        40,
                                        mesh.vertexBuffer()
                                )
                );
            } else if (!sectionBuffers.getVertexBuffer().isClosed()) {
                commandEncoder.writeToBuffer(sectionBuffers.getVertexBuffer().slice(), mesh.vertexBuffer());
            }

            ByteBuffer indexByteBuffer = mesh.indexBuffer();
            if (indexByteBuffer != null) {
                if (sectionBuffers.getIndexBuffer() != null && sectionBuffers.getIndexBuffer().size() >= indexByteBuffer.remaining()) {
                    if (!sectionBuffers.getIndexBuffer().isClosed()) {
                        commandEncoder.writeToBuffer(sectionBuffers.getIndexBuffer().slice(), indexByteBuffer);
                    }
                } else {
                    if (sectionBuffers.getIndexBuffer() != null) {
                        sectionBuffers.getIndexBuffer().close();
                    }
                    sectionBuffers.setIndexBuffer(
                            RenderSystem.getDevice()
                                    .createBuffer(
                                            () -> "Section index buffer - layer: "
                                                    + layer.label()
                                                    + "; cords: "
                                                    + x + ", " + y + ", " + z,
                                            72,
                                            indexByteBuffer
                                    )
                    );
                }
            } else if (sectionBuffers.getIndexBuffer() != null) {
                sectionBuffers.getIndexBuffer().close();
                sectionBuffers.setIndexBuffer(null);
            }

            sectionBuffers.setIndexCount(mesh.drawState().indexCount());
            sectionBuffers.setIndexType(mesh.drawState().indexType());
        } else {
            GpuBuffer vertexBuffer = RenderSystem.getDevice()
                    .createBuffer(
                            () -> "Section vertex buffer - layer: "
                                    + layer.label()
                                    + "; cords: "
                                    + x + ", " + y + ", " + z,
                            40,
                            mesh.vertexBuffer()
                    );
            ByteBuffer indexByteBuffer = mesh.indexBuffer();
            GpuBuffer indexBuffer = indexByteBuffer != null
                    ? RenderSystem.getDevice()
                    .createBuffer(
                            () -> "Section index buffer - layer: "
                                    + layer.label()
                                    + "; cords: "
                                    + x + ", " + y + ", " + z,
                            72,
                            indexByteBuffer
                    )
                    : null;
            SectionBuffers newSectionBuffers = new SectionBuffers(vertexBuffer, indexBuffer, mesh.drawState().indexCount(), mesh.drawState().indexType());
            this.buffers.put(layer, newSectionBuffers);
        }
    }

    public void uploadLayerIndexBuffer(final ChunkSectionLayer layer, final ByteBufferBuilder.Result indexBuffer, final SectionPos sectionPos) {
        SectionBuffers target = this.getBuffers(layer);
        int x = sectionPos.x();
        int y = sectionPos.y();
        int z = sectionPos.z();

        if (target != null) {
            if (target.getIndexBuffer() == null) {
                target.setIndexBuffer(
                        RenderSystem.getDevice()
                                .createBuffer(
                                        () -> "Section index buffer - layer: "
                                                + layer.label()
                                                + "; cords: "
                                                + x + ", " + y + ", " + z,
                                        72,
                                        indexBuffer.byteBuffer()
                                )
                );
            } else {
                CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
                if (!target.getIndexBuffer().isClosed()) {
                    commandEncoder.writeToBuffer(target.getIndexBuffer().slice(), indexBuffer.byteBuffer());
                }
            }
        }
    }
    // ===== MCRe 结束 =====

    @Override
    public boolean hasTranslucentGeometry() {
        return this.buffers.containsKey(ChunkSectionLayer.TRANSLUCENT);
    }

    public MeshData.@Nullable SortState getTransparencyState() {
        return this.transparencyState;
    }

    @Override
    public void close() {
        this.buffers.values().forEach(SectionBuffers::close);
        this.buffers.clear();
    }
}