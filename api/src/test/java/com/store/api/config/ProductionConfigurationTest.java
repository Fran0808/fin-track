package com.store.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class ProductionConfigurationTest {
    private MockEnvironment valid() {
        return new MockEnvironment().withProperty("jwt.secret", "synthetic-signing-key-with-more-than-32-bytes")
                .withProperty("spring.datasource.password", "synthetic-password")
                .withProperty("google.oauth.client-id", "synthetic-client")
                .withProperty("google.oauth.client-secret", "synthetic-secret")
                .withProperty("spring.datasource.url", "jdbc:postgresql://database.example.test/test")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("app.server.public-url", "https://app.example.test")
                .withProperty("google.oauth.redirect-uri", "https://app.example.test/api/v1/auth/google/callback")
                .withProperty("google.oauth.frontend-success-url", "https://app.example.test/?auth=google_connected")
                .withProperty("google.oauth.frontend-error-url", "https://app.example.test/?auth=google_error")
                .withProperty("app.cors.allowed-origins", "https://app.example.test");
    }
    @Test void validProductionSettingsAreAccepted() { assertDoesNotThrow(() -> new ProductionConfiguration(valid()).validate()); }
    @Test void defaultsAndMissingValuesAreRejected() {
        assertThrows(IllegalStateException.class, () -> new ProductionConfiguration(valid().withProperty("jwt.secret", "")).validate());
        assertThrows(IllegalStateException.class, () -> new ProductionConfiguration(valid().withProperty("google.oauth.client-secret", "replace-me")).validate());
        assertThrows(IllegalStateException.class, () -> new ProductionConfiguration(valid().withProperty("spring.jpa.hibernate.ddl-auto", "update")).validate());
    }
    @Test void callbackRequiresHttpsAndSameOriginBrowserHandoff() {
        assertThrows(IllegalStateException.class, () -> new ProductionConfiguration(valid().withProperty("google.oauth.redirect-uri", "http://localhost:8080/callback")).validate());
        assertThrows(IllegalStateException.class, () -> new ProductionConfiguration(valid().withProperty("google.oauth.redirect-uri", "https://other.example.test/callback")).validate());
        assertThrows(IllegalStateException.class, () -> new ProductionConfiguration(valid().withProperty("app.cors.allowed-origins", "*")).validate());
    }
}
