package com.store.api.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {
    @org.junit.jupiter.api.Test
    void rejectsUntrustedOrigins() throws Exception {
        var request = new MockHttpServletRequest("OPTIONS", "/api/v1/transactions");
        request.addHeader("Origin", "https://untrusted.example.test");
        request.addHeader("Access-Control-Request-Method", "GET");
        var response = new MockHttpServletResponse();
        new CorsConfig().corsFilterRegistration().getFilter().doFilter(request, response,
                (req, res) -> org.junit.jupiter.api.Assertions.fail("Untrusted origin reached the application"));
        assertEquals(403, response.getStatus());
    }
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/transactions/1/classification",
            "/api/v1/categories/1/toggle"
    })
    void allowsPatchPreflight(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("Access-Control-Request-Method", "PATCH");
        request.addHeader("Access-Control-Request-Headers", "authorization, content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorsConfig().corsFilterRegistration().getFilter().doFilter(request, response,
                (req, res) -> fail("Preflight must be handled by the CORS filter"));

        assertEquals(200, response.getStatus());
        assertEquals("http://localhost:5173", response.getHeader("Access-Control-Allow-Origin"));
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("PATCH"));
        assertTrue(response.getHeader("Access-Control-Allow-Headers").contains("authorization"));
        assertTrue(response.getHeader("Access-Control-Allow-Headers").contains("content-type"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/transactions/1/classification",
            "/api/v1/categories/1/toggle"
    })
    void forwardsActualPatchToAuthentication(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", path);
        request.addHeader("Origin", "http://localhost:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean forwarded = new AtomicBoolean();

        new CorsConfig().corsFilterRegistration().getFilter().doFilter(request, response,
                (req, res) -> forwarded.set(true));

        assertTrue(forwarded.get());
        assertEquals(200, response.getStatus());
        assertEquals("http://localhost:5173", response.getHeader("Access-Control-Allow-Origin"));
        assertTrue(response.getHeader("Access-Control-Expose-Headers").contains("X-Fintrack-Review-Filter"));
        assertTrue(response.getHeader("Access-Control-Expose-Headers").contains("X-Fintrack-Merchant-Filter"));
    }
}
