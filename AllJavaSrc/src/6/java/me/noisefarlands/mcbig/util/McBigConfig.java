package me.noisefarlands.mcbig.util;

public class McBigConfig {
    private static NumberType activeType = NumberType.INT256;  // 默认使用 Int256

    public static void setNumberType(NumberType type) {
        activeType = type;
    }

    public static NumberType getNumberType() {
        return activeType;
    }

    // 是否开启调试（打印运算细节）
    private static boolean debug = false;

    public static void setDebug(boolean debug) {
        McBigConfig.debug = debug;
    }

    public static boolean isDebug() {
        return debug;
    }
}