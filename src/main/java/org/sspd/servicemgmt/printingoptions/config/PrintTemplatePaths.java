package org.sspd.servicemgmt.printingoptions.config;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the on-disk print overlay that mirrors WAR classpath layout:
 * {@code {root}/css/print-base.css} and {@code {root}/templates/print/**}.
 */
public final class PrintTemplatePaths {

    private PrintTemplatePaths() {}

    public static boolean isConfigured(String root) {
        return root != null && !root.isBlank();
    }

    public static Path root(String templatesDir) {
        return Path.of(templatesDir.trim()).toAbsolutePath().normalize();
    }

    public static Path templatesDirectory(String templatesDir) {
        return root(templatesDir).resolve("templates");
    }

    public static String thymeleafPrefix(String templatesDir) {
        String prefix = templatesDirectory(templatesDir).toString().replace('\\', '/');
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    public static Path cssFile(String templatesDir) {
        return root(templatesDir).resolve("css").resolve("print-base.css");
    }

    public static boolean templatesAvailable(String templatesDir) {
        return isConfigured(templatesDir) && Files.isDirectory(templatesDirectory(templatesDir));
    }

    public static boolean cssAvailable(String templatesDir) {
        return isConfigured(templatesDir) && Files.isRegularFile(cssFile(templatesDir));
    }
}
