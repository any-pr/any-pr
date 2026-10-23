package net.forbric.kernel.boot;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("system-properties")
class MacAwtBootstrapTest {
    private String before;
    @BeforeEach void remember() { before = System.getProperty("java.awt.headless"); System.clearProperty("java.awt.headless"); }
    @AfterEach void restore() {
        MacAwtBootstrap.configure("Linux");
        if (before == null) System.clearProperty("java.awt.headless"); else System.setProperty("java.awt.headless", before);
    }
    @Test void macUsesBitmapOnlyAwtAndKeepsTheExternalDialogAvailable() {
        MacAwtBootstrap.configure("Mac OS X");
        assertEquals("true", System.getProperty("java.awt.headless")); assertTrue(MacAwtBootstrap.usesHeadlessFonts());
    }
    @Test void explicitWindowPreferenceAndOtherPlatformsArePreserved() {
        System.setProperty("java.awt.headless", "false"); MacAwtBootstrap.configure("Mac OS X");
        assertEquals("false", System.getProperty("java.awt.headless")); assertFalse(MacAwtBootstrap.usesHeadlessFonts());
        System.clearProperty("java.awt.headless"); MacAwtBootstrap.configure("Windows 11");
        assertNull(System.getProperty("java.awt.headless")); assertFalse(MacAwtBootstrap.usesHeadlessFonts());
    }
}
