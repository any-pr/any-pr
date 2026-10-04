package net.minecraft.client.gui.components.debug;

import java.util.Locale;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.WorldReposition;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.MinecraftTools.Math._256Bit.Float256;
import org.jspecify.annotations.Nullable;

/**
 * 🔧 MCRe：Position 条目 —— 移植 26.4 Snapshot 2 全新架构（{@link DebugFact} 名字右对齐 + 值的对齐显示），
 * <b>完整保留 MCRe 全部自定义</b>（26 处）：
 * <ul>
 *   <li>256-bit 精确坐标显示（{@code Float256.toExactString} + 限长省略）</li>
 *   <li>XYZ(Camera)（正确的摄像机获取：{@code mainCamera()}）</li>
 *   <li>Terrain XYZ(BigInteger)（玩家坐标经 WorldReposition 偏移缩放，无精度损失）</li>
 *   <li>Current precision（基于 Terrain XYZ 的 bit 长度推算 double/float 精度 + 颜色码）</li>
 * </ul>
 *
 * @since 2026-10-01
 */
@OnlyIn(Dist.CLIENT)
public class DebugEntryPosition implements DebugScreenEntry {

    /** 26.2 旧组名常量（过渡期兼容：DebugEntrySectionPosition 引用；迁移完成后移除） */
    @Deprecated
    public static final Identifier GROUP = Identifier.withDefaultNamespace("position");

    // ==================== 🔧 MCRe 自定义：精确显示辅助 ====================

    /** 精确显示小数部分的最大位数（超出截断 + 省略号） */
    private static final int MAX_FRAC_DIGITS = 14;

    /** double → Float256 → 精确十进制（完整 52-bit 尾数展开，限长显示） */
    private static String fmtExact(final double value) {
        String s = Float256.of(value).toExactString();
        int dot = s.indexOf('.');
        if (dot < 0) return s;
        int frac = s.length() - dot - 1;
        if (frac <= MAX_FRAC_DIGITS) return s;
        return s.substring(0, dot + MAX_FRAC_DIGITS + 1) + "…";
    }

    /**
     * 🔧 MCRe：BigInteger 整数显示（Terrain XYZ 用，无小数点、无精度损失，直接 toString）。
     * 跟 XYZ/XYZ(Camera) 的 fmtExact 输出格式对齐（不省略、无限位数）。
     */
    private static String fmtBigInt(final BigInteger value) {
        return value.toString();
    }

    /**
     * 🔧 MCRe：计算 Terrain XYZ（玩家坐标经 WorldReposition 偏移缩放 → BigDecimal → BigInteger 截断）。
     * <p><b>精度关键：</b>用自研 {@code new BigDecimal(double)} 直接吃下 double 的全部 64-bit 信息——
     * 该构造器是完整 IEEE 754 精确转换（significand 去尾零 + 5^n 缩放，无舍入），
     * 不经 Float256 中转、也不走 Double.toString（后者对 ≥2^53 的大整数只保留 17 位有效数字，
     * 会造成 9223372036854776000 这类精度丢失）。
     * <p>无大小限制：scale/shift 即使是 1e49 也能精确算出 BigInteger 整数地形坐标。
     * <p>无精度损失：double → BigDecimal（精确）→ reposition BigDecimal（精确乘加）→ toBigInteger（截断小数部分）。
     */
    private static BigInteger[] computeTerrainXYZ(final double playerX, final double playerY, final double playerZ) {
        return new BigInteger[] {
            WorldReposition.reposition(new BigDecimal(playerX), Direction.Axis.X).toBigInteger(),
            WorldReposition.reposition(new BigDecimal(playerY), Direction.Axis.Y).toBigInteger(),
            WorldReposition.reposition(new BigDecimal(playerZ), Direction.Axis.Z).toBigInteger()
        };
    }

    private static char getColorCodeFromPrecision(double precision) {
        if (precision <= 0.03125) {
            return 'a';
        } else if (precision > 0.25) {
            return 'c';
        } else {
            return 'e';
        }
    }

    // ==================== 26.4 新架构：display（Fact 对齐） ====================

    @Override
    public void display(
        final DebugScreenDisplayer displayer,
        final @Nullable Level serverOrClientLevel,
        final @Nullable LevelChunk clientChunk,
        final @Nullable LevelChunk serverChunk
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.getCameraEntity();
        if (entity != null) {
            BlockPos feetPos = minecraft.getCameraEntity().blockPosition();
            ChunkPos chunkPos = ChunkPos.containing(feetPos);
            Direction direction = entity.getDirection();

            // ===== 🔧 MCRe：正确的摄像机获取方式 =====
            Camera camera = minecraft.gameRenderer.mainCamera();
            double camX = camera.position().x;
            double camY = camera.position().y;
            double camZ = camera.position().z;

            String faceString = switch (direction) {
                case NORTH -> "Towards negative Z";
                case SOUTH -> "Towards positive Z";
                case WEST -> "Towards negative X";
                case EAST -> "Towards positive X";
                default -> "Invalid";
            };
            java.util.Set<ChunkPos> chunks = serverOrClientLevel instanceof ServerLevel serverLevel
                    ? serverLevel.getForceLoadedChunks()
                    : java.util.Set.of();

            // ===== 🔧 MCRe：Terrain XYZ + 精度计算 =====
            final BigInteger[] terrainXYZ = computeTerrainXYZ(entity.getX(), entity.getY(), entity.getZ());
            final BigInteger maxAbs = terrainXYZ[0].abs().max(terrainXYZ[1].abs()).max(terrainXYZ[2].abs());
            final int bitLen = maxAbs.bitLength();  // 最高位 1 的位置（等价于 64 - Long.numberOfLeadingZeros）
            final double doublePrecision = Math.pow(2.0, (double) (bitLen - 53));
            final double floatPrecision = Math.pow(2.0, (double) (bitLen - 24));

            // ===== 26.4 Fact 风格：名字右对齐 + 值（DebugGroups.POSITION，白强调色）=====
            displayer.addFactToGroup(
                DebugGroups.POSITION,
                "XYZ",
                fact -> fact.value(fmtExact(entity.getX())).text(" / ").value(fmtExact(entity.getY())).text(" / ").value(fmtExact(entity.getZ()))
            );
            displayer.addFactToGroup(
                DebugGroups.POSITION,
                "XYZ(Camera)",
                fact -> fact.value(fmtExact(camX)).text(" / ").value(fmtExact(camY)).text(" / ").value(fmtExact(camZ))
            );
            displayer.addFactToGroup(
                DebugGroups.POSITION,
                "Terrain XYZ(BigInteger)",
                fact -> fact.value(fmtBigInt(terrainXYZ[0])).text(" / ").value(fmtBigInt(terrainXYZ[1])).text(" / ").value(fmtBigInt(terrainXYZ[2]))
            );
            displayer.addFactToGroup(
                DebugGroups.POSITION,
                "Block",
                fact -> fact.value(feetPos.getX()).text(" ").value(feetPos.getY()).text(" ").value(feetPos.getZ())
            );
            displayer.addFactToGroup(
                DebugGroups.POSITION,
                "Chunk",
                fact -> fact.value(chunkPos.x())
                    .text(" ")
                    .value(SectionPos.blockToSectionCoord(feetPos.getY()))
                    .text(" ")
                    .value(chunkPos.z())
                    .text(" [")
                    .value(chunkPos.getRegionLocalX())
                    .text(" ")
                    .value(chunkPos.getRegionLocalZ())
                    .text(" in ")
                    .formattedValue("r.%d.%d.mca", chunkPos.getRegionX(), chunkPos.getRegionZ())
                    .text("]")
            );
            displayer.addFactToGroup(
                DebugGroups.POSITION,
                "Current precision",
                fact -> fact.text(
                    "§" + getColorCodeFromPrecision(doublePrecision) + doublePrecision
                    + "§r (float: §" + getColorCodeFromPrecision(floatPrecision) + floatPrecision + "§r)"
                )
            );
            displayer.addFactToGroup(
                DebugGroups.POSITION,
                "Facing",
                fact -> fact.value(direction.toString())
                    .text(" (")
                    .value(faceString)
                    .text(") (")
                    .formattedValue("%.1f", Mth.wrapDegrees(entity.getYRot()))
                    .text(" / ")
                    .formattedValue("%.1f", Mth.wrapDegrees(entity.getXRot()))
                    .text(")")
            );
            displayer.addFactToGroup(
                DebugGroups.POSITION,
                "Dimension",
                fact -> fact.value(minecraft.level.dimension().identifier().toString())
            );
            if (!chunks.isEmpty()) {
                displayer.addFactToGroup(DebugGroups.POSITION, "Forced Chunks", fact -> fact.value(chunks.size()));
            }
        }
    }
}