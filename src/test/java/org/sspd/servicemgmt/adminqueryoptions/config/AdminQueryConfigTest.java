package org.sspd.servicemgmt.adminqueryoptions.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AdminQueryConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                    .withUserConfiguration(AdminQueryConfig.class);

    @Test
    void defaultsToDisabled() {
        contextRunner.run(context ->
                assertThat(context.getBean(AdminQueryProperties.class).isEnabled()).isFalse());
    }

    @Test
    void canBeExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("app.admin-query.enabled=true")
                .run(context ->
                        assertThat(context.getBean(AdminQueryProperties.class).isEnabled()).isTrue());
    }
}
