package net.minecraft.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import org.jspecify.annotations.Nullable;

public class ViewArea {
    protected final LevelRenderer levelRenderer;
    protected final Level level;
    protected int sectionGridSizeY;
    protected int sectionGridSizeX;
    protected int sectionGridSizeZ;
    private int viewDistance;
    private SectionPos cameraSectionPos;
    public SectionRenderDispatcher.RenderSection[] sections;

    public ViewArea(final SectionRenderDispatcher sectionRenderDispatcher, final Level level, final int renderDistance, final LevelRenderer levelRenderer) {
        this.levelRenderer = levelRenderer;
        this.level = level;
        this.setViewDistance(renderDistance);
        this.createSections(sectionRenderDispatcher);
        this.cameraSectionPos = SectionPos.of(this.viewDistance + 1, 0, this.viewDistance + 1);
    }

    // ===== MCRe: 使用 SectionPos 构造 RenderSection =====
    protected void createSections(final SectionRenderDispatcher sectionRenderDispatcher) {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("createSections called from wrong thread: " + Thread.currentThread().getName());
        }

        int totalSections = this.sectionGridSizeX * this.sectionGridSizeY * this.sectionGridSizeZ;
        this.sections = new SectionRenderDispatcher.RenderSection[totalSections];

        for (int x = 0; x < this.sectionGridSizeX; x++) {
            for (int y = 0; y < this.sectionGridSizeY; y++) {
                for (int z = 0; z < this.sectionGridSizeZ; z++) {
                    int index = this.getSectionIndex(x, y, z);
                    SectionPos pos = SectionPos.of(x, y + this.level.getMinSectionY(), z);
                    // 直接传入 SectionPos 对象，不再使用 asLong
                    this.sections[index] = sectionRenderDispatcher.new RenderSection(index, pos);
                }
            }
        }
    }
    // ===== MCRe 结束 =====

    public void releaseAllBuffers() {
        for (SectionRenderDispatcher.RenderSection section : this.sections) {
            section.reset();
        }
    }

    private int getSectionIndex(final int x, final int y, final int z) {
        return (z * this.sectionGridSizeY + y) * this.sectionGridSizeX + x;
    }

    protected void setViewDistance(final int renderDistance) {
        int dist = renderDistance * 2 + 1;
        this.sectionGridSizeX = dist;
        this.sectionGridSizeY = this.level.getSectionsCount();
        this.sectionGridSizeZ = dist;
        this.viewDistance = renderDistance;
    }

    public int getViewDistance() {
        return this.viewDistance;
    }

    public LevelHeightAccessor getLevelHeightAccessor() {
        return this.level;
    }

    // ===== MCRe: 使用 SectionPos 更新所有 Section =====
    public void repositionCamera(final SectionPos cameraSectionPos) {
        for (int gridX = 0; gridX < this.sectionGridSizeX; gridX++) {
            int lowestX = cameraSectionPos.x() - this.viewDistance;
            int newSectionX = lowestX + Math.floorMod(gridX - lowestX, this.sectionGridSizeX);

            for (int gridZ = 0; gridZ < this.sectionGridSizeZ; gridZ++) {
                int lowestZ = cameraSectionPos.z() - this.viewDistance;
                int newSectionZ = lowestZ + Math.floorMod(gridZ - lowestZ, this.sectionGridSizeZ);

                for (int gridY = 0; gridY < this.sectionGridSizeY; gridY++) {
                    int newSectionY = this.level.getMinSectionY() + gridY;
                    SectionRenderDispatcher.RenderSection section = this.sections[this.getSectionIndex(gridX, gridY, gridZ)];
                    SectionPos newPos = SectionPos.of(newSectionX, newSectionY, newSectionZ);
                    // 使用 setSectionPos 而非 setSectionNode
                    if (!section.getSectionPos().equals(newPos)) {
                        section.setSectionPos(newPos);
                    }
                }
            }
        }
        this.cameraSectionPos = cameraSectionPos;
        this.levelRenderer.getSectionOcclusionGraph().invalidate();
    }
    // ===== MCRe 结束 =====

    public SectionPos getCameraSectionPos() {
        return this.cameraSectionPos;
    }

    // ===== MCRe: 新增接受 SectionPos 的方法，并标记旧方法为 @Deprecated =====
    public void setDirty(final SectionPos sectionPos, final boolean playerChanged) {
        SectionRenderDispatcher.RenderSection section = this.getRenderSection(sectionPos);
        if (section != null) {
            section.setDirty(playerChanged);
        }
    }

    @Deprecated
    public void setDirty(final int sectionX, final int sectionY, final int sectionZ, final boolean playerChanged) {
        this.setDirty(SectionPos.of(sectionX, sectionY, sectionZ), playerChanged);
    }

    protected SectionRenderDispatcher.@Nullable RenderSection getRenderSectionAt(final BlockPos pos) {
        return this.getRenderSection(SectionPos.of(pos));
    }

    // ===== 新方法：接受 SectionPos =====
    protected SectionRenderDispatcher.@Nullable RenderSection getRenderSection(final SectionPos sectionPos) {
        int sectionX = sectionPos.x();
        int sectionY = sectionPos.y();
        int sectionZ = sectionPos.z();
        if (!this.containsSection(sectionPos)) {
            return null;
        }
        int y = sectionY - this.level.getMinSectionY();
        int x = Math.floorMod(sectionX, this.sectionGridSizeX);
        int z = Math.floorMod(sectionZ, this.sectionGridSizeZ);
        return this.sections[this.getSectionIndex(x, y, z)];
    }

    @Deprecated
    protected SectionRenderDispatcher.@Nullable RenderSection getRenderSection(final long sectionNode) {
        return this.getRenderSection(SectionPos.of(sectionNode));
    }

    private boolean containsSection(final SectionPos sectionPos) {
        int sectionY = sectionPos.y();
        if (sectionY < this.level.getMinSectionY() || sectionY > this.level.getMaxSectionY()) {
            return false;
        }
        int x = sectionPos.x();
        int z = sectionPos.z();
        return x >= this.cameraSectionPos.x() - this.viewDistance && x <= this.cameraSectionPos.x() + this.viewDistance
                && z >= this.cameraSectionPos.z() - this.viewDistance && z <= this.cameraSectionPos.z() + this.viewDistance;
    }

    @Deprecated
    private boolean containsSection(final int sectionX, final int sectionY, final int sectionZ) {
        return this.containsSection(SectionPos.of(sectionX, sectionY, sectionZ));
    }

    @Deprecated
    private SectionRenderDispatcher.@Nullable RenderSection getRenderSection(final int sectionX, final int sectionY, final int sectionZ) {
        return this.getRenderSection(SectionPos.of(sectionX, sectionY, sectionZ));
    }
    // ===== MCRe 结束 =====
}