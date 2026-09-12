package org.sspd.servicemgmt.customerportaloptions.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoogleIdTokenVerifier {

    private final RestClient.Builder restClientBuilder;

    @Value("${app.customer-portal.google.client-ids:}")
    private String clientIdsRaw;

    public GoogleIdentity verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("Google token မရှိပါ");
        }
        Set<String> allowed = Arrays.stream(clientIdsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        if (allowed.isEmpty()) {
            throw new IllegalStateException("Google Sign-In ကို server မှာ မသတ်မှတ်ရသေးပါ");
        }
        TokenInfo info;
        try {
            info = restClientBuilder.build()
                    .get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", idToken.trim())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(TokenInfo.class);
        } catch (RestClientException e) {
            throw new IllegalArgumentException("Google token မမှန်ကန်ပါ");
        }
        if (info == null || info.sub == null || info.sub.isBlank()) {
            throw new IllegalArgumentException("Google token မမှန်ကန်ပါ");
        }
        if (info.aud == null || !allowed.contains(info.aud)) {
            throw new IllegalArgumentException("Google client ID မကိုက်ပါ");
        }
        if (info.email == null || info.email.isBlank()) {
            throw new IllegalArgumentException("Gmail လိပ်စာ မရပါ");
        }
        if (!"true".equalsIgnoreCase(info.emailVerified)) {
            throw new IllegalArgumentException("Gmail အတည်မပြုရသေးပါ");
        }
        GoogleIdentity identity = new GoogleIdentity();
        identity.setSub(info.sub);
        identity.setEmail(info.email.trim().toLowerCase());
        identity.setName(info.name == null || info.name.isBlank() ? info.email : info.name.trim());
        return identity;
    }

    @Data
    public static class GoogleIdentity {
        private String sub;
        private String email;
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TokenInfo {
        private String aud;
        private String sub;
        private String email;
        private String name;
        @JsonProperty("email_verified")
        private String emailVerified;
    }
}
