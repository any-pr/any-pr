package net.minecraft.world.level.levelgen.synth;

import java.lang.reflect.Field;
import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 🔧 MCRe（噪声溢出监测·方案 B 复刻检测器）：零侵入噪声本身（反射读内部状态，不改任何计算行为），
 * 复刻 NormalNoise → PerlinNoise → ImprovedNoise 的计算链，在每个晶格定位点检查
 * {@code floorToIntWithWrap} 的饱和触发——坐标超 int（±2^31）= 边境之地现象的根源。
 * <p>链路复刻与 {@code PerlinNoise.getValue} 的 double 路径逐行一致：
 * NormalNoise 的 first 组用原坐标、second 组用 ×1.0181268882175227 偏移坐标；
 * 每组内按倍频循环 {@code wrap(坐标 × factor) → +origin → 饱和判断}。</p>
 * <p>返回首个饱和的组/倍频定位（如 {@code "F3"} = first 组第 3 倍频、{@code "S5"} = second 组第 5 倍频），
 * 无饱和返回 null。首个饱和即主导溢出（后续倍频频率翻倍只会更饱和）。</p>
 */
@OnlyIn(Dist.CLIENT)
public class NoiseOverflowUtil {
    private static final Field NORMAL_FIRST;
    private static final Field NORMAL_SECOND;
    private static final Field PERLIN_LEVELS;
    private static final Field PERLIN_LOWEST_FREQ_INPUT;

    static {
        try {
            NORMAL_FIRST = NormalNoise.class.getDeclaredField("first");
            NORMAL_FIRST.setAccessible(true);
            NORMAL_SECOND = NormalNoise.class.getDeclaredField("second");
            NORMAL_SECOND.setAccessible(true);
            PERLIN_LEVELS = PerlinNoise.class.getDeclaredField("noiseLevels");
            PERLIN_LEVELS.setAccessible(true);
            PERLIN_LOWEST_FREQ_INPUT = PerlinNoise.class.getDeclaredField("lowestFreqInputFactor");
            PERLIN_LOWEST_FREQ_INPUT.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private NoiseOverflowUtil() {
    }

    /** 检测单个噪声在给定坐标下的首个饱和点；返回如 "F3+"/"F3-"（+/−=饱和到 MAX/MIN 的晶格坐标方向），无饱和返回 null */
    public static String detectFirstSaturate(final NormalNoise noise, final double x, final double y, final double z) {
        try {
            PerlinNoise first = (PerlinNoise)NORMAL_FIRST.get(noise);
            PerlinNoise second = (PerlinNoise)NORMAL_SECOND.get(noise);
            // first 组：原坐标（与 NormalNoise.getValue 一致）
            String result = detectPerlin(first, x, y, z, "F");
            if (result != null) {
                return result;
            }

            if (second != null) {
                // second 组：× 1.0181268882175227 偏移坐标
                return detectPerlin(
                    second, x * 1.0181268882175227, y * 1.0181268882175227, z * 1.0181268882175227, "S"
                );
            }

            return null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /** 🔧 MCRe：模拟饱和溢出开关（与 ImprovedNoise.simulatedWraparoundOverflowMode 同款语义，按 WorldMainSettingScreen 介绍为准） */
    private static boolean simulatedMode() {
        WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        return config != null && config.simulatedWraparoundOverflow;
    }

    /** 复刻单组 PerlinNoise 的倍频链：wrap(坐标×factor) → +origin → 饱和判断 */
    private static String detectPerlin(final PerlinNoise perlin, final double x, final double y, final double z, final String tag)
        throws IllegalAccessException {
        if (perlin == null) {
            return null;
        }

        ImprovedNoise[] levels = (ImprovedNoise[])PERLIN_LEVELS.get(perlin);
        if (levels == null) {
            return null;
        }

        double factor = PERLIN_LOWEST_FREQ_INPUT.getDouble(perlin);
        for (int i = 0; i < levels.length; i++) {
            ImprovedNoise noise = levels[i];
            if (noise != null) {
                // 与 PerlinNoise.getValue 一致：先 wrap 再进晶格定位
                double wx = PerlinNoise.wrap(x * factor) + noise.xo;
                double wy = PerlinNoise.wrap(y * factor) + noise.yo;
                double wz = PerlinNoise.wrap(z * factor) + noise.zo;
                // 🔧 适配（按 WorldMainSettingScreen 介绍）：两个分支的饱和方向不同——
                // 模拟饱和溢出（true）：floorToIntWithWrap 正/负溢出（含 == 端点）都钳到 Integer.MAX_VALUE（正坐标）
                // 原版饱和（false）：Mth.floor 正溢出（> MAX）钳 MAX / 负溢出（< MIN）钳 MIN（负坐标），端点 == 正常
                // 晶格坐标符号不同 → 晶格 hash 不同 → 噪声值不同——按开关精确模拟 + 方向标记（+/−）
                if (simulatedMode()) {
                    if (wx >= Integer.MAX_VALUE || wx <= Integer.MIN_VALUE
                        || wy >= Integer.MAX_VALUE || wy <= Integer.MIN_VALUE
                        || wz >= Integer.MAX_VALUE || wz <= Integer.MIN_VALUE) {
                        return tag + i + "+";
                    }
                } else {
                    if (wx > Integer.MAX_VALUE || wy > Integer.MAX_VALUE || wz > Integer.MAX_VALUE) {
                        return tag + i + "+";
                    }

                    if (wx < Integer.MIN_VALUE || wy < Integer.MIN_VALUE || wz < Integer.MIN_VALUE) {
                        return tag + i + "-";
                    }
                }
            }

            factor *= 2.0;
        }

        return null;
    }
}