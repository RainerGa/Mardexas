package de.richmarkdown;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class FxRuntimeOptionsTest {
    @ParameterizedTest @ValueSource(strings = {"Linux", "linux", "LINUX"})
    void enablesCompletedFrameUploadsOnLinux(String os) {
        Properties properties = new Properties(); properties.setProperty("os.name", os);
        FxRuntimeOptions.configure(properties);
        assertEquals("true", properties.getProperty("prism.forceUploadingPainter"));
        assertNull(properties.getProperty("prism.order"), "Do not force software rendering");
        FxRuntimeOptions.configure(properties);
        assertEquals(2, properties.size(), "Repeated configuration is idempotent");
    }
    @ParameterizedTest @ValueSource(strings = {"Windows 11", "Mac OS X", "", "FreeBSD"})
    void leavesOtherPlatformsUnchanged(String os) {
        Properties properties = new Properties(); properties.setProperty("os.name", os);
        FxRuntimeOptions.configure(properties);
        assertEquals(1, properties.size());
    }
    @ParameterizedTest @ValueSource(strings = {"false", "true"})
    void preservesExplicitGraphicsOverrides(String override) {
        Properties properties = new Properties(); properties.setProperty("os.name", "Linux");
        properties.setProperty("prism.forceUploadingPainter", override);
        properties.setProperty("prism.order", "es2");
        FxRuntimeOptions.configure(properties);
        assertEquals(override, properties.getProperty("prism.forceUploadingPainter"));
        assertEquals("es2", properties.getProperty("prism.order"));
    }
    @Test void missingPlatformDoesNotChangeProperties() {
        Properties properties = new Properties(); FxRuntimeOptions.configure(properties);
        assertTrue(properties.isEmpty());
    }
}
