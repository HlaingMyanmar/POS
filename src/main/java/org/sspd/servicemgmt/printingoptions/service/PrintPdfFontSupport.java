package org.sspd.servicemgmt.printingoptions.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Flying Saucer only embeds fonts that are registered on {@link ITextFontResolver}.
 * Browser print uses OS fonts (Pyidaungsu); direct PDF must register the same family.
 */
@Slf4j
final class PrintPdfFontSupport {

    private static final String IDENTITY_H = "Identity-H";

    private PrintPdfFontSupport() {}

    static void register(ITextRenderer renderer) {
        ITextFontResolver resolver = renderer.getFontResolver();
        Set<String> registered = new LinkedHashSet<>();
        registerClasspath(resolver, "print/fonts/Pyidaungsu-Regular.ttf", registered);
        registerClasspath(resolver, "print/fonts/Pyidaungsu-Bold.ttf", registered);
        for (String path : systemFontCandidates()) {
            registerFile(resolver, path, registered);
        }
        if (registered.isEmpty()) {
            log.warn("No Myanmar PDF fonts registered; invoice glyphs may be missing");
        } else {
            log.debug("PDF fonts registered: {}", registered);
        }
    }

    private static void registerClasspath(ITextFontResolver resolver, String classpath, Set<String> registered) {
        ClassPathResource resource = new ClassPathResource(classpath);
        if (!resource.exists()) return;
        try (InputStream in = resource.getInputStream()) {
            Path tmp = Files.createTempFile("print-font-", ".ttf");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            registerFile(resolver, tmp.toAbsolutePath().toString(), registered);
        } catch (Exception e) {
            log.warn("Could not load classpath font {}: {}", classpath, e.getMessage());
        }
    }

    private static void registerFile(ITextFontResolver resolver, String path, Set<String> registered) {
        if (path == null || path.isBlank() || !Files.isRegularFile(Path.of(path))) return;
        if (!registered.add(path)) return;
        try {
            resolver.addFont(path, IDENTITY_H, true);
        } catch (Exception e) {
            registered.remove(path);
            log.warn("Could not register PDF font {}: {}", path, e.getMessage());
        }
    }

    private static String[] systemFontCandidates() {
        String win = System.getenv("WINDIR");
        if (win == null || win.isBlank()) win = "C:\\Windows";
        return new String[] {
                win + "\\Fonts\\Pyidaungsu-2.5.3_Regular.ttf",
                win + "\\Fonts\\Pyidaungsu-2.5.3_Bold.ttf",
                win + "\\Fonts\\mmrtext.ttf",
                win + "\\Fonts\\mmrtextb.ttf",
                "/usr/share/fonts/truetype/pyidaungsu/Pyidaungsu-Regular.ttf",
                "/usr/share/fonts/truetype/noto/NotoSansMyanmar-Regular.ttf",
                "/usr/share/fonts/truetype/padauk/Padauk-Regular.ttf",
        };
    }
}
