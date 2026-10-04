package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.FarLandsYScan;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.configurations.BlockBlobConfiguration;

public class BlockBlobFeature extends Feature<BlockBlobConfiguration> {
    public BlockBlobFeature(final Codec<BlockBlobConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(final FeaturePlaceContext<BlockBlobConfiguration> context) {
        BlockPos origin = context.origin();
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockBlobConfiguration config = context.config();

        // 🔧 MCRe：钳制下扫范围（超高世界 getMinY() 可达 -21 亿）
        final int scanBottom = (int)Math.max((long)level.getMinY() + 3, (long)origin.getY() - FarLandsYScan.MAX_BLOCK_SCAN);
        while (origin.getY() > scanBottom && !config.canPlaceOn().test(level, origin.below())) {
            origin = origin.below();
        }

        if (origin.getY() <= scanBottom) {
            return false;
        }

        for (int c = 0; c < 3; c++) {
            int xr = random.nextInt(2);
            int yr = random.nextInt(2);
            int zr = random.nextInt(2);
            float tr = (xr + yr + zr) * 0.333F + 0.5F;

            for (BlockPos blockPos : BlockPos.betweenClosed(origin.offset(-xr, -yr, -zr), origin.offset(xr, yr, zr))) {
                if (blockPos.distSqr(origin) <= tr * tr) {
                    level.setBlock(blockPos, config.state(), 3);
                }
            }

            origin = origin.offset(-1 + random.nextInt(2), -random.nextInt(2), -1 + random.nextInt(2));
        }

        return true;
    }
}