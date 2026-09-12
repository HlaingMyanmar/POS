package org.sspd.servicemgmt.adminqueryoptions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminQueryConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.admin-query")
    AdminQueryProperties adminQueryProperties() {
        return new AdminQueryProperties();
    }
}
