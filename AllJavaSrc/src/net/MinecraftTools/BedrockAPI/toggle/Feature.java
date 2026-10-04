package net.MinecraftTools.BedrockAPI.toggle;

/**
 * 特性标识 —— 对应 Bedrock 的 {@code FeatureToggles} 枚举项。
 *
 * <p>Bedrock 用 {@code FeatureToggles::isEnabled(Feature::X)} 做运行期查询，
 * 同时在 CMake 构建阶段把整段代码从 release 目标里剔除。Java 没有条件编译，
 * 因此本包采用<b>双轨制</b>：
 * <ul>
 *   <li><b>编译期常量轨</b>：{@link FeatureToggles} 里的 {@code static final boolean} 字面量，
 *       被 {@code javac} 常量折叠，热路径写 {@code if (FeatureToggles.ENABLE_XXX)} 可零开销消除死分支。</li>
 *   <li><b>运行期覆盖轨</b>：{@link FeatureToggles#isEnabled(Feature)}，供开发/测试临时开关，
 *       发布构建里覆盖表恒为空。</li>
 * </ul>
 *
 * <p>每个特性还带一个 {@code baseGameVersion} 语义位（对应 Bedrock 的 BaseGameVersion 门控），
 * 用于「同一份世界在不同版本下行为不同」的场景。
 *
 * @since 2026-09-27
 */
public enum Feature {

    // ===== 坐标与精度（MCRe Long 化主线）=====

    /** 坐标对象化（BlockPos/SectionPos/Vec3i 全对象，无 asLong 打包）。 */
    LONG_COORDINATE("mcre.feature.longCoordinate", true, 0),

    /** 区块窗口化（allSections + windowSections 双轨，窗口跟随相机）。 */
    WINDOWED_CHUNK("mcre.feature.windowedChunk", true, 0),

    /** 窗口锚定策略：true=固定 0 中心，false=跟随相机。 */
    WINDOW_FOLLOW_CAMERA("mcre.feature.windowFollowCamera", false, 0),

    /** 生成期高度访问器钳制（NoiseChunk cellCountY 恒定小内存）。 */
    GENERATION_HEIGHT_CLAMP("mcre.feature.generationHeightClamp", true, 0),

    // ===== 光照 =====

    /** 光照增量按绝对 sectionY 编码（废弃 BitSet 窗口索引）。 */
    LIGHT_ABSOLUTE_SECTION_Y("mcre.feature.lightAbsoluteSectionY", true, 0),

    /** Far Lands 光照循环切断。 */
    FAR_LANDS_LIGHT_CUTOFF("mcre.feature.farLandsLightCutoff", true, 0),

    // ===== 渲染 =====

    /** MultiDrawIndirect 批量渲染（26.3 Snapshot 6 移植）。 */
    MULTIDRAW_INDIRECT("mcre.feature.multidrawIndirect", false, 0),

    /** GPU 相机相对坐标（terrain.vsh 顶点着色器相对偏移）。 */
    GPU_CAMERA_RELATIVE("mcre.feature.gpuCameraRelative", false, 0),

    /** 实体遮挡剔除（visibleSectionSet）。 */
    ENTITY_OCCLUSION_CULLING("mcre.feature.entityOcclusionCulling", true, 0),

    /** 树叶内部面剔除。 */
    LEAVES_INTERNAL_FACE_CULLING("mcre.feature.leavesInternalFaceCulling", true, 0),

    // ===== 世界生成 =====

    /** WorldReposition 偏移缩放（噪声/表面/含水层全链）。 */
    WORLD_REPOSITION("mcre.feature.worldReposition", false, 0),

    /** Y 轴钳制梯度偏移（与 surface 偏移分离的独立开关）。 */
    Y_CLAMPED_GRADIENT_OFFSET("mcre.feature.yClampedGradientOffset", false, 0),

    // ===== 存储与网络 =====

    /** RegionFileStorage 真 LRU + IOWorker 批量写盘。 */
    CHUNK_IO_BATCH("mcre.feature.chunkIoBatch", true, 0),

    /** 区块包按窗口过滤 sendableSections。 */
    CHUNK_PACKET_WINDOW_FILTER("mcre.feature.chunkPacketWindowFilter", true, 0),

    // ===== 命令与调试 =====

    /** 命令模板延迟拼装（禁止字符串拼接）。 */
    CMD_TEMPLATE("mcre.feature.cmdTemplate", true, 0),

    /** 传送走异步请求 + 系统轮询。 */
    ASYNC_TELEPORT_REQUEST("mcre.feature.asyncTeleportRequest", false, 0),

    /** 聊天框全界面开启（LocalCommandExecutor）。 */
    CHAT_EVERYWHERE("mcre.feature.chatEverywhere", true, 0);

    private final String key;
    private final boolean defaultValue;
    private final int baseGameVersion;

    Feature(String key, boolean defaultValue, int baseGameVersion) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.baseGameVersion = baseGameVersion;
    }

    /** 配置键（用于 JSON/系统属性覆盖）。 */
    public String key() {
        return key;
    }

    /** 编译期默认值 —— 与 {@link FeatureToggles} 里的字面量保持一致。 */
    public boolean defaultValue() {
        return defaultValue;
    }

    /** BaseGameVersion 门控位（0 = 不门控）。 */
    public int baseGameVersion() {
        return baseGameVersion;
    }
}
