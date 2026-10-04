package com.store.api.config.security;

import com.store.api.config.CorsConfig;
import com.store.api.model.entity.User;
import com.store.api.repository.UserRepository;
import com.store.api.service.auth.JwtService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {
    private static final String SECRET = "test-only-signing-key-with-at-least-32-bytes";
    private final UserRepository users = mock(UserRepository.class);
    private final JwtService jwt = new JwtService(SECRET, 60_000);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwt, users);
    private final User user = User.builder().id(42L).email("user@example.test").build();

    @BeforeEach
    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/api/v1/financial-instruments", "POST,/api/v1/financial-instruments",
            "PUT,/api/v1/financial-instruments/1", "PATCH,/api/v1/transactions/1/financial-instrument",
            "GET,/api/v1/transactions/1/financial-instrument-suggestions",
            "GET,/api/v1/transactions", "POST,/api/v1/transactions/sync",
            "POST,/api/v1/transactions/sync/batch", "GET,/api/v1/analytics/summary",
            "GET,/api/v1/analytics/period", "POST,/api/v1/emails/sync",
            "GET,/api/v1/emails/test-connection", "GET,/api/v1/auth/google/me",
            "GET,/api/v1/auth/google/status", "POST,/api/v1/auth/google/disconnect",
            "POST,/api/v1/auth/google/url", "POST,/api/v1/auth/google/callback",
            "GET,/api/v1/auth/google/url/extra", "OPTIONS,/api/v1/transactions"
    })
    void rejectsAnonymousRequests(String method, String path) throws Exception {
        UserContext.setCurrentUser(user);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(method, path), response,
                (request, result) -> fail("Anonymous request reached the application"));
        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        assertNull(UserContext.getCurrentUser());
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic abc", "Bearer ", "Bearer invalid", "Bearer null"})
    void rejectsMalformedCredentials(String authorization) throws Exception {
        assertRejected(authorization);
        verifyNoInteractions(users);
    }

    @Test
    void rejectsExpiredAndWronglySignedTokens() throws Exception {
        assertRejected("Bearer " + new JwtService(SECRET, -60_000).generateToken(user));
        assertRejected("Bearer " + new JwtService("different-test-key-with-at-least-32-bytes", 60_000).generateToken(user));
        verifyNoInteractions(users);
    }

    @Test
    void rejectsTokenForDeletedUser() throws Exception {
        when(users.findById(42L)).thenReturn(Optional.empty());
        assertRejected("Bearer " + jwt.generateToken(user));
        verify(users).findById(42L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/google/url", "/api/v1/auth/google/callback"})
    void allowsPublicGoogleEndpoints(String path) throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(),
                (request, response) -> called.set(true));
        assertTrue(called.get());
        assertNull(UserContext.getCurrentUser());
        verifyNoInteractions(users);
    }

    @Test
    void authenticatesAndClearsContextEvenWhenApplicationFails() {
        when(users.findById(42L)).thenReturn(Optional.of(user));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions");
        request.addHeader("Authorization", "bearer " + jwt.generateToken(user));
        assertThrows(ServletException.class, () -> filter.doFilter(request, new MockHttpServletResponse(),
                (req, response) -> {
                    assertSame(user, UserContext.requireCurrentUser());
                    throw new ServletException("Application failure");
                }));
        assertNull(UserContext.getCurrentUser());
    }

    @Test
    void permitsPublicLoginWithServletContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/api/v1/auth/google/url");
        request.setContextPath("/app");
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (req, response) -> called.set(true));
        assertTrue(called.get());
    }

    @Test
    void corsPreflightAndUnauthorizedResponsesRemainReadableByBrowser() throws Exception {
        var cors = new CorsConfig().corsFilterRegistration().getFilter();
        MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/api/v1/transactions");
        preflight.addHeader("Origin", "http://localhost:5173");
        preflight.addHeader("Access-Control-Request-Method", "GET");
        preflight.addHeader("Access-Control-Request-Headers", "Authorization");
        MockHttpServletResponse response = new MockHttpServletResponse();
        cors.doFilter(preflight, response, (req, res) -> fail("Preflight reached the application"));
        assertEquals(200, response.getStatus());
        assertEquals("http://localhost:5173", response.getHeader("Access-Control-Allow-Origin"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions");
        request.addHeader("Origin", "http://localhost:5173");
        response = new MockHttpServletResponse();
        cors.doFilter(request, response, (req, res) -> filter.doFilter(req, res,
                (ignoredRequest, ignoredResponse) -> fail("Anonymous request reached the application")));
        assertEquals(401, response.getStatus());
        assertEquals("http://localhost:5173", response.getHeader("Access-Control-Allow-Origin"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/transactions/sync", "/api/v1/transactions/sync/batch"})
    void authenticatesWithValidDevicePairingToken(String path) throws Exception {
        when(users.findByDevicePairingToken("wp_dev_valid_token_123")).thenReturn(Optional.of(user));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader("X-Device-Token", "wp_dev_valid_token_123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> {
            called.set(true);
            assertSame(user, UserContext.requireCurrentUser());
        });

        assertTrue(called.get());
        assertEquals(200, response.getStatus());
        assertNull(UserContext.getCurrentUser());
        verify(users).findByDevicePairingToken("wp_dev_valid_token_123");
    }

    @Test
    void rejectsInvalidDevicePairingToken() throws Exception {
        when(users.findByDevicePairingToken("wp_dev_invalid")).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions/sync");
        request.addHeader("X-Device-Token", "wp_dev_invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> fail("Invalid device token reached application"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid or revoked device pairing token"));
        assertNull(UserContext.getCurrentUser());
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/api/v1/transactions", "GET,/api/v1/analytics/summary",
            "POST,/api/v1/auth/google/disconnect", "POST,/api/v1/emails/sync",
            "GET,/api/v1/user/pairing-info", "POST,/api/v1/user/pairing-info/regenerate",
            "GET,/api/v1/transactions/sync", "GET,/api/v1/transactions/sync/batch",
            "POST,/api/v1/transactions/sync/extra", "POST,/api/v1/app/update",
            "GET,/api/v1/financial-instruments", "POST,/api/v1/financial-instruments",
            "PUT,/api/v1/financial-instruments/1", "PATCH,/api/v1/transactions/1/financial-instrument",
            "GET,/api/v1/transactions/1/financial-instrument-suggestions"
    })
    void rejectsDeviceCredentialsOutsideAllowedEndpoints(String method, String path) throws Exception {
        when(users.findByDevicePairingToken("wp_dev_valid_token_123")).thenReturn(Optional.of(user));
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("X-Device-Token", "wp_dev_valid_token_123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response,
                (req, res) -> fail("Device accessed a forbidden endpoint"));

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertNull(UserContext.getCurrentUser());
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/api/v1/transactions", "GET,/api/v1/analytics/summary",
            "POST,/api/v1/auth/google/disconnect", "GET,/api/v1/user/pairing-info",
            "POST,/api/v1/user/pairing-info/regenerate", "GET,/api/v1/financial-instruments",
            "PATCH,/api/v1/transactions/1/financial-instrument"
    })
    void permitsWebCredentialsOnDeviceRestrictedEndpoints(String method, String path) throws Exception {
        when(users.findById(42L)).thenReturn(Optional.of(user));
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer " + jwt.generateToken(user));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> {
            called.set(true);
            assertSame(user, UserContext.requireCurrentUser());
        });

        assertTrue(called.get());
        assertEquals(200, response.getStatus());
        assertNull(UserContext.getCurrentUser());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void permitsPublicAppUpdatesWithOrWithoutDeviceCredentials(boolean withDeviceToken) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/app/update");
        if (withDeviceToken) {
            when(users.findByDevicePairingToken("wp_dev_valid_token_123")).thenReturn(Optional.of(user));
            request.addHeader("X-Device-Token", "wp_dev_valid_token_123");
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> called.set(true));

        assertTrue(called.get());
        assertEquals(200, response.getStatus());
        assertNull(UserContext.getCurrentUser());
    }

    private void assertRejected(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions");
        request.addHeader("Authorization", authorization);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("Invalid credentials reached the application"));
        assertEquals(401, response.getStatus());
        assertNull(UserContext.getCurrentUser());
    }
}
