package net.minecraft.server.level;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.slf4j.Logger;

/**
 * 🔧 MCRe 分带生成（阶段 2+3+4）：玩家竖直窗口触发的按需「带填充 + 分带 surface/carve」。
 *
 * <p><b>解决的问题</b>：超高世界下地形只在固定窗口带生成（{@code getHeightAccessorForGeneration()}
 * 返回的 34 段窗口被 {@code NoiseSettings.clampToHeightAccessor} 钳制），所以天空边境之地
 * 永远不会在理论高度（如 25101648）生成。
 *
 * <p><b>流程</b>：
 * <ol>
 *   <li>每 tick 取玩家所在 sectionY，算出带 {@code [playerSectionY ± VERTICAL_SIMULATION_DISTANCE]}；</li>
 *   <li>扫玩家附近 chunk：该带未生成（{@code ChunkAccess.isBandGenerated}）→ 提交异步带任务；</li>
 *   <li>带任务：{@code setGenerationBand} → fill（NoiseSettings 被钳到该带 → vanilla fill 天然只填该带）
 *       → surface（{@code SurfaceSystem.buildSurface}）→ carve（17×17 起点网格）
 *       → {@code markBandGenerated} → {@code clearGenerationBand}；</li>
 *   <li>fill 在途加 {@link TicketType#FARLANDS_BAND_GEN} 票据防止区块卸载。</li>
 * </ol>
 *
 * <p><b>与 P5（渲染窗口跟随）的区别</b>：本类**只动生成带**（{@code ChunkAccess.generationBand*}），
 * 不触碰显示窗口 / 渲染 / 网络发送窗口，所以不会重蹈 P5 的坑。
 *
 * <p><b>安全网</b>：若目标带内已有非空 section（玩家先放了方块），则跳过填充并直接标记已生成，
 * 避免覆盖玩家建筑。
 */
public final class FarLandsBandGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 玩家竖直窗口半径（section，±N）——与 34 段显示窗口的一半对齐。 */
    public static final int VERTICAL_SIMULATION_DISTANCE = 17;

    /** 每 tick 最多提交的带任务数（限流，避免一次性提交海量异步任务）。 */
    private static final int MAX_TASKS_PER_TICK = 2;

    /** 触发半径（chunk，相对玩家所在 chunk）。 */
    private static final int CHUNK_RADIUS = 4;

    /** carver 起点网格半径（vanilla applyCarvers 硬编码 8）。 */
    private static final int CARVER_GRID_RADIUS = 8;

    private final ServerLevel level;

    /** 在途 chunk 去重（ChunkPos 是 record，天然可作 map key）。 */
    private final Set<ChunkPos> inFlight = ConcurrentHashMap.newKeySet();

    public FarLandsBandGenerator(final ServerLevel level) {
        this.level = level;
    }

    /** 主线程每 tick 调用（{@code ServerLevel.tick} 里 chunkSource.tick 之后）。 */
    public void tick() {
        final List<ServerPlayer> players = this.level.players();
        if (players.isEmpty()) {
            return;
        }

        int budget = MAX_TASKS_PER_TICK;
        for (final ServerPlayer player : players) {
            if (budget <= 0) {
                break;
            }

            final int playerSectionY = SectionPos.blockToSectionCoord(player.getBlockY());
            final int bandMin = playerSectionY - VERTICAL_SIMULATION_DISTANCE;
            final int bandMax = playerSectionY + VERTICAL_SIMULATION_DISTANCE;
            final ChunkPos center = player.chunkPosition();

            for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS && budget > 0; dx++) {
                for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS && budget > 0; dz++) {
                    final ChunkPos pos = new ChunkPos(center.x() + dx, center.z() + dz);
                    final LevelChunk chunk = this.level.getChunkSource()
                            .getChunkNow((int) pos.x(), (int) pos.z());
                    if (chunk == null || chunk.isBandGenerated(bandMin, bandMax)) {
                        continue;
                    }

                    // 安全网：带内已有非空 section（玩家先放了方块）→ 跳过填充，直接标记，避免覆盖建筑
                    if (this.hasNonEmptySectionInBand(chunk, bandMin, bandMax)) {
                        chunk.markBandGenerated(bandMin, bandMax);
                        continue;
                    }

                    if (!this.inFlight.add(pos)) {
                        continue;
                    }

                    budget--;
                    this.submit(chunk, pos, bandMin, bandMax);
                }
            }
        }
    }

    /** 目标带内是否存在非空 section（已生成/玩家建筑）。 */
    private boolean hasNonEmptySectionInBand(final LevelChunk chunk, final int bandMin, final int bandMax) {
        for (int sy = bandMin; sy <= bandMax; sy++) {
            final LevelChunkSection section = chunk.getSectionAt(sy);
            if (section != null && !section.hasOnlyAir()) {
                return true;
            }
        }
        return false;
    }

    /** 提交一次异步带任务（fill → surface → carve）。 */
    private void submit(final LevelChunk chunk, final ChunkPos pos, final int bandMin, final int bandMax) {
        // 保加载：任务在途区块不卸载
        this.level.getChunkSource().addTicketWithRadius(TicketType.FARLANDS_BAND_GEN, pos, 0);

        CompletableFuture.runAsync(() -> {
            chunk.setGenerationBand(bandMin, bandMax);
            // 🔧 MCRe：整段填充期间标记「生成式写入」——后续所有写入（fill 本体 + surface + carve）
            // 都走生成式路径，避免 state.onPlace → scheduleTick 从异步线程污染 tick 队列 ✗
            chunk.beginGenerationWrite();
            try {
                // 🔧 MCRe：分带填充必须用「不加 section 锁」的变体——写的是活着的 LevelChunk，
                // 原版 fill 的 acquire/release 会与服务器线程的流体 tick 撞车，
                // 触发 PalettedContainer 的「Accessing ... from multiple threads」硬异常 ✗
                final net.minecraft.world.level.chunk.ChunkGenerator generator = this.level.getChunkSource().getGenerator();
                final net.minecraft.world.level.levelgen.blending.Blender blender = Blender.empty();
                final net.minecraft.world.level.levelgen.RandomState randomState = this.level.getChunkSource().randomState();
                final net.minecraft.world.level.StructureManager structureManager = this.level.structureManager();
                final java.util.concurrent.CompletableFuture<net.minecraft.world.level.chunk.ChunkAccess> fillFuture =
                        generator instanceof NoiseBasedChunkGenerator noiseGenerator
                                ? noiseGenerator.fillFromNoise(blender, randomState, structureManager, chunk, false)
                                : generator.fillFromNoise(blender, randomState, structureManager, chunk);
                fillFuture.join();
                // 🔧 MCRe 分带生成（阶段 4）：surface / carve 紧跟 fill（复用带域 NoiseChunk），
                // 必须在 clearGenerationBand 之前（之后 NoiseChunk 缓存被清空）
                this.applySurface(chunk);
                this.applyCarvers(chunk, bandMin, bandMax);
                chunk.markBandGenerated(bandMin, bandMax);
            } finally {
                chunk.clearGenerationBand();
                chunk.endGenerationWrite();
            }
        }, Util.backgroundExecutor()).whenComplete((ignored, throwable) -> {
            // 票据增删 + 光照通知 + 区块重发必须回主线程
            this.level.getServer().execute(() -> {
                this.level.getChunkSource().removeTicketWithRadius(TicketType.FARLANDS_BAND_GEN, pos, 0);
                this.inFlight.remove(pos);
                if (throwable == null) {
                    this.notifyLight(bandMin, bandMax, chunk);
                    // 🔧 MCRe 分带生成（阶段 5a）：重发区块包——初次发送时带还没填，
                    // 不重发客户端就收不到新 section（看不到天空边境之地）
                    this.resendChunk(chunk, bandMin, bandMax);
                }
            });
            if (throwable != null) {
                LOGGER.error("MCRe: 分带填充失败 chunk={} band=[{}, {}]", pos, bandMin, bandMax, throwable);
            }
        });
    }

    /**
     * 🔧 MCRe 分带生成（阶段 4）：分带地表。
     * <p>与 vanilla 同源（{@code SurfaceSystem.buildSurface}），差异：
     * <ul>
     *   <li>NoiseChunk 复用 fill 缓存的**带域**实例（surface 只用 preliminarySurfaceLevel）；</li>
     *   <li>BiomeManager 直查 biomeSource（绕开 chunk section biome 的窗口限制）；</li>
     *   <li>{@code SurfaceSystem.buildSurface} 的列扫描下界已钳制（{@code FarLandsYScan}）。</li>
     * </ul>
     */
    private void applySurface(final LevelChunk chunk) {
        if (!(this.level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            return;
        }
        final NoiseChunk noiseChunk = chunk.getCachedNoiseChunk();
        if (noiseChunk == null) {
            return;
        }

        final RandomState randomState = this.level.getChunkSource().randomState();
        final NoiseGeneratorSettings settings = noiseGenerator.generatorSettings().value();
        final BiomeSource biomeSource = noiseGenerator.getBiomeSource();
        final BiomeManager biomeManager = new BiomeManager(
                (quartX, quartY, quartZ) -> biomeSource.getNoiseBiome(quartX, quartY, quartZ, randomState.sampler()),
                BiomeManager.obfuscateSeed(this.level.getSeed()));
        final WorldGenerationContext generationContext = new WorldGenerationContext(noiseGenerator, chunk);

        randomState.surfaceSystem()
                .buildSurface(
                        randomState,
                        biomeManager,
                        settings.useLegacyRandomSource(),
                        generationContext,
                        chunk,
                        noiseChunk,
                        settings.surfaceRule(),
                        null);
    }

    /**
     * 🔧 MCRe 分带生成（阶段 4）：分带雕刻。
     * <p>移植 vanilla {@code NoiseBasedChunkGenerator.applyCarvers} 的 17×17 起点网格，差异：
     * <ul>
     *   <li>mask 用**带限安全 mask**（LevelChunk 没有 vanilla ProtoChunk 的 carving mask；
     *       带外 Y 的写入/查询直接忽略，避免 BitSet 越界）；</li>
     *   <li>biome 直查 biomeSource（起点 chunk 可能未生成）。</li>
     * </ul>
     * <p>注：carver 的 Y 范围由生物群系配置的 HeightProvider 给出（主世界锚定在绝对 Y 附近），
     * 所以极高带（如 2.5e7）天然不会被雕——与 vanilla 语义一致。
     */
    private void applyCarvers(final LevelChunk chunk, final int bandMin, final int bandMax) {
        if (SharedConstants.DEBUG_DISABLE_CARVERS
                || !(this.level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            return;
        }
        final NoiseChunk noiseChunk = chunk.getCachedNoiseChunk();
        if (noiseChunk == null) {
            return;
        }

        final RandomState randomState = this.level.getChunkSource().randomState();
        final NoiseGeneratorSettings settings = noiseGenerator.generatorSettings().value();
        final Aquifer aquifer = noiseChunk.aquifer();
        final CarvingContext carvingContext = new CarvingContext(
                noiseGenerator,
                this.level.registryAccess(),
                chunk.getHeightAccessorForGeneration(),
                noiseChunk,
                randomState,
                settings.surfaceRule());
        final BandCarvingMask mask = new BandCarvingMask((bandMax - bandMin + 1) * 16, bandMin * 16);
        final BiomeSource biomeSource = noiseGenerator.getBiomeSource();
        final Function<BlockPos, Holder<Biome>> biomeGetter = pos -> biomeSource.getNoiseBiome(
                QuartPos.fromBlock(pos.getX()),
                QuartPos.fromBlock(pos.getY()),
                QuartPos.fromBlock(pos.getZ()),
                randomState.sampler());
        final ChunkPos pos = chunk.getPos();
        final WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));

        for (int dx = -CARVER_GRID_RADIUS; dx <= CARVER_GRID_RADIUS; dx++) {
            for (int dz = -CARVER_GRID_RADIUS; dz <= CARVER_GRID_RADIUS; dz++) {
                final ChunkPos sourcePos = new ChunkPos(pos.x() + dx, pos.z() + dz);
                final Holder<Biome> sourceBiome = biomeSource.getNoiseBiome(
                        QuartPos.fromSection((int) sourcePos.x()),
                        0,
                        QuartPos.fromSection((int) sourcePos.z()),
                        randomState.sampler());
                int index = 0;

                for (final Holder<ConfiguredWorldCarver<?>> carverHolder : sourceBiome.value()
                        .getGenerationSettings()
                        .getCarvers()) {
                    final ConfiguredWorldCarver<?> carver = carverHolder.value();
                    random.setLargeFeatureSeed(
                            this.level.getSeed() + index, (int) sourcePos.x(), (int) sourcePos.z());
                    if (carver.isStartChunk(random)) {
                        carver.carve(carvingContext, chunk, biomeGetter, random, aquifer, sourcePos, mask);
                    }
                    index++;
                }
            }
        }
    }

    /**
     * 🔧 MCRe 分带生成（阶段 5a）：把分带填充后的区块重新发送给跟踪它的玩家。
     * <p>不能走 {@code PlayerChunkSender}（它的全量模式光照范围只有 chunk 窗口 → band 光照不发送，
     * 客户端拿到地形也是黑的）；这里直接用光照包的**「changed sections」模式**：
     * 每个 long 就是 sectionY，精确发送 band 的光照，不需要遍历上百万段。
     */
    private void resendChunk(final LevelChunk chunk, final int bandMin, final int bandMax) {
        final ChunkPos pos = chunk.getPos();
        final it.unimi.dsi.fastutil.longs.LongOpenHashSet bandSections = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (int sy = bandMin; sy <= bandMax; sy++) {
            bandSections.add(sy);
        }
        for (final ServerPlayer player : this.level.getChunkSource().chunkMap.getPlayers(pos, false)) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                    chunk, this.level.getLightEngine(), bandSections, bandSections));
        }
    }

    /**
     * 通知光照引擎：带内 section 已有内容（否则新填的地形不参与光照计算 = 全黑）。
     * 与 {@code LevelChunk.setBlockState} 里 section 空/非空切换时的调用一致。
     */
    private void notifyLight(final int bandMin, final int bandMax, final LevelChunk chunk) {
        final int chunkX = (int) chunk.getPos().x();
        final int chunkZ = (int) chunk.getPos().z();
        final LevelLightEngine lightEngine = this.level.getChunkSource().getLightEngine();
        for (int sy = bandMin; sy <= bandMax; sy++) {
            final LevelChunkSection section = chunk.getSectionAt(sy);
            final boolean empty = section == null || section.hasOnlyAir();
            lightEngine.updateSectionStatus(SectionPos.of(chunkX, sy, chunkZ), empty);
            this.level.getChunkSource().onSectionEmptinessChanged(chunkX, sy, chunkZ, empty);
        }
    }

    /** 🔧 MCRe 分带生成：带限安全 carving mask——带外 Y 的写入/查询直接忽略（防 BitSet 越界）。 */
    private static final class BandCarvingMask extends CarvingMask {
        private final int minY;
        private final int maxY;

        private BandCarvingMask(final int height, final int minY) {
            super(height, minY);
            this.minY = minY;
            this.maxY = minY + height - 1;
        }

        @Override
        public void set(final int x, final int y, final int z) {
            if (y < this.minY || y > this.maxY) {
                return;
            }
            super.set(x, y, z);
        }

        @Override
        public boolean get(final int x, final int y, final int z) {
            if (y < this.minY || y > this.maxY) {
                return false;
            }
            return super.get(x, y, z);
        }
    }
}
