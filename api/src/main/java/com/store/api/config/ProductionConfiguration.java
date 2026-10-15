package com.store.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import java.net.URI;
import java.util.List;

@Configuration @Profile("prod")
public class ProductionConfiguration {
    private final Environment environment;
    public ProductionConfiguration(Environment environment) { this.environment = environment; }

    @PostConstruct
    public void validate() {
        for (String name : List.of("jwt.secret", "spring.datasource.password", "google.oauth.client-id", "google.oauth.client-secret")) {
            String value = environment.getProperty(name, "");
            if (value.isBlank() || value.toLowerCase(java.util.Locale.ROOT).startsWith("replace")
                    || value.toLowerCase(java.util.Locale.ROOT).startsWith("change")) fail(name);
        }
        String jdbc = environment.getProperty("spring.datasource.url", "");
        if (!jdbc.startsWith("jdbc:postgresql://")) fail("DATABASE_JDBC_URL");
        if (!"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))) fail("spring.jpa.hibernate.ddl-auto");
        URI callback = https("google.oauth.redirect-uri");
        URI success = https("google.oauth.frontend-success-url");
        URI error = https("google.oauth.frontend-error-url");
        https("app.server.public-url");
        if (!origin(callback).equals(origin(success)) || !origin(callback).equals(origin(error))) fail("OAuth callback/frontend origins");
        String allowed = environment.getProperty("app.cors.allowed-origins", "");
        if (allowed.isBlank()) fail("CORS_ALLOWED_ORIGINS");
        boolean frontendAllowed = false;
        for (String item : allowed.split(",")) {
            URI uri;
            try { uri = URI.create(item.trim()); } catch (RuntimeException ex) { throw new IllegalStateException("Invalid CORS_ALLOWED_ORIGINS"); }
            validateHttps(uri, "CORS_ALLOWED_ORIGINS");
            if (!uri.getPath().isEmpty() || uri.getQuery() != null) fail("CORS_ALLOWED_ORIGINS");
            frontendAllowed |= origin(uri).equals(origin(success));
        }
        if (!frontendAllowed) fail("CORS_ALLOWED_ORIGINS frontend origin");
    }

    private URI https(String key) {
        URI uri;
        try { uri = URI.create(environment.getProperty(key, "")); }
        catch (RuntimeException ex) { throw new IllegalStateException("Invalid production setting: " + key); }
        validateHttps(uri, key);
        return uri;
    }
    private void validateHttps(URI uri, String key) {
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getHost().equalsIgnoreCase("localhost") || uri.getHost().equals("127.0.0.1") || uri.getHost().equals("[::1]")) fail(key);
    }
    private String origin(URI uri) { return uri.getScheme() + "://" + uri.getHost().toLowerCase(java.util.Locale.ROOT) + ":" + (uri.getPort() == -1 ? 443 : uri.getPort()); }
    private void fail(String name) { throw new IllegalStateException("Missing or unsafe production setting: " + name); }
}
