package org.sspd.servicemgmt.securityConfig;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.sspd.servicemgmt.jwt.JwtAuthenticationFilter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final AuthenticationProvider authenticationProvider;
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final PublicEndpointRateLimitFilter publicEndpointRateLimitFilter;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")
    private String allowedOriginsRaw;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/api/v1/customer-portal/auth/**", "/ws-clinic/**", "/ws-native/**", "/topic/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/customer/reset-password").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/customer-reset-password.html").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/setup/status").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/setup/initial-admin").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/company-settings").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/app/version").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/app/technician/version").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/app/customer/version").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/customer-portal/catalog/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/customer-portal/delivery-townships").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/customer-portal/delivery-locations").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/customer-portal/branding", "/api/v1/customer-portal/branding/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/customer-portal/booking-availability/**").permitAll()
                        // Controller enforces staff CAN_ACCESS_SALE_CREATE or X-Scanner-Token.
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/scan").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(publicEndpointRateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        configuration.setAllowedOrigins(origins);

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin",
                "X-Setup-Token", "X-Scanner-Token", "X-Client-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public FilterRegistrationBean<PublicEndpointRateLimitFilter> publicEndpointRateLimitRegistration(
            PublicEndpointRateLimitFilter filter) {
        FilterRegistrationBean<PublicEndpointRateLimitFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
