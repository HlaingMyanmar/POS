package org.sspd.servicemgmt.securityConfig;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NginxForwardedHeaderGuardTest {

    @Test
    void edgeProxyOverwritesClientForwardedHeaders() throws Exception {
        Path config = Path.of("deploy/nginx.conf");
        String text = Files.readString(config);

        assertFalse(text.contains("$proxy_add_x_forwarded_for"),
                "Nginx must not append client-supplied X-Forwarded-For");
        assertTrue(text.contains("proxy_set_header   X-Forwarded-For   $remote_addr;"),
                "Nginx must overwrite X-Forwarded-For with the connecting address");
        assertTrue(text.contains("proxy_set_header   Forwarded         \"\";"),
                "Nginx must clear the untrusted Forwarded header");
    }
}
