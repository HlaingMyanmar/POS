package org.sspd.servicemgmt.printingoptions.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.util.Set;

/**
 * Prefer {@code /opt/sspd/print/templates/print/*.html} over WAR classpath
 * templates when the overlay directory exists.
 */
@Slf4j
@Configuration
public class PrintExternalTemplateConfig {

    @Bean
    public ITemplateResolver printFileTemplateResolver(
            @Value("${app.print.templates-dir:}") String templatesDir) {
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setOrder(0);
        resolver.setResolvablePatterns(Set.of("print/*", "print/fragments/*"));

        if (PrintTemplatePaths.templatesAvailable(templatesDir)) {
            resolver.setPrefix(PrintTemplatePaths.thymeleafPrefix(templatesDir));
            resolver.setCacheable(false);
            resolver.setCheckExistence(false);
            log.info("Print templates overlay enabled (no WAR fallback): {}",
                    PrintTemplatePaths.templatesDirectory(templatesDir));
        } else {
            resolver.setPrefix("/nonexistent-sspd-print-templates/");
            resolver.setCacheable(true);
            resolver.setCheckExistence(true);
            if (PrintTemplatePaths.isConfigured(templatesDir)) {
                log.warn("Print templates dir is set but {} is missing — using WAR templates",
                        PrintTemplatePaths.templatesDirectory(templatesDir));
            }
        }
        return resolver;
    }
}
