package com.store.api.service.auth;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PairingConfigurationTest {
    private MockEnvironment environment() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addLast(new PropertiesPropertySource("application",
                PropertiesLoaderUtils.loadAllProperties("application.properties")));
        return environment;
    }

    @Test
    void pairingUsesTheConfiguredPublicAddress() throws IOException {
        MockEnvironment environment = environment()
                .withProperty("APP_SERVER_PUBLIC_URL", "http://192.0.2.20:8080");
        assertEquals("http://192.0.2.20:8080", environment.getProperty("app.server.public-url"));
    }

    @Test
    void unconfiguredDevelopmentAddressUsesTheEffectiveServerPort() throws IOException {
        MockEnvironment environment = environment().withProperty("SERVER_PORT", "9090");
        assertEquals("http://localhost:9090", environment.getProperty("app.server.public-url"));
    }
}
