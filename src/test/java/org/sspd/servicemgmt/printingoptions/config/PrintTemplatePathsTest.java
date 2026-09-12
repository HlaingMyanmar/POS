package org.sspd.servicemgmt.printingoptions.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrintTemplatePathsTest {

    @TempDir
    Path tempDir;

    @Test
    void mapsOverlayLayoutUsedOnTheVps() throws Exception {
        Path css = tempDir.resolve("css");
        Path templates = tempDir.resolve("templates").resolve("print");
        Files.createDirectories(css);
        Files.createDirectories(templates);
        Files.writeString(css.resolve("print-base.css"), "body{}");
        Files.writeString(templates.resolve("booking-a5.html"), "<div></div>");

        String root = tempDir.toString();
        assertTrue(PrintTemplatePaths.cssAvailable(root));
        assertTrue(PrintTemplatePaths.templatesAvailable(root));
        assertEquals(css.resolve("print-base.css"), PrintTemplatePaths.cssFile(root));
        assertTrue(PrintTemplatePaths.thymeleafPrefix(root).replace('\\', '/').endsWith("/templates/"));
        assertTrue(Files.isRegularFile(
                PrintTemplatePaths.templatesDirectory(root).resolve("print").resolve("booking-a5.html")));
    }

    @Test
    void blankDirUsesWarFallback() {
        assertFalse(PrintTemplatePaths.isConfigured(""));
        assertFalse(PrintTemplatePaths.templatesAvailable(""));
        assertFalse(PrintTemplatePaths.cssAvailable(""));
    }
}
