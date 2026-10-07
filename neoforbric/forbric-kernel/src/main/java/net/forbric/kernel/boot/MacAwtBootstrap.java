/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.boot;

/** Keep AWT's Cocoa event loop out of the GLFW-owned game JVM; font and bitmap rendering still work. */
public final class MacAwtBootstrap {
    private static volatile boolean headlessFonts;
    private MacAwtBootstrap() { }
    public static void configure() { configure(System.getProperty("os.name", "")); }
    static void configure(String os) {
        headlessFonts = false;
        if (!os.toLowerCase(java.util.Locale.ROOT).contains("mac") || System.getProperty("java.awt.headless") != null) return;
        System.setProperty("java.awt.headless", "true");
        headlessFonts = true;
    }
    /** A standalone dialog JVM can still own AWT while the game owns GLFW. */
    public static boolean usesHeadlessFonts() { return headlessFonts; }
}
