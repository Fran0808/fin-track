package com.store.api.service.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class OAuthLoginSessionTest {
    @Test void stateIsBrowserBoundSingleUseAndWrongStateDoesNotConsumeIt() {
        var flow = new OAuthLoginSession();
        var browser = new MockHttpSession();
        String state = flow.begin(browser);
        assertFalse(flow.consumeState(new MockHttpSession(), state));
        assertFalse(flow.consumeState(browser, "wrong"));
        assertTrue(flow.consumeState(browser, state));
        assertFalse(flow.consumeState(browser, state));
    }
    @Test void expiredStateAndHandoffAreRejected() {
        var flow = new OAuthLoginSession();
        var browser = new MockHttpSession();
        Instant now = Instant.parse("2026-10-01T12:00:00Z");
        ReflectionTestUtils.setField(flow, "clock", Clock.fixed(now, ZoneOffset.UTC));
        String state = flow.begin(browser);
        String handoff = flow.issueHandoff(browser, "synthetic-jwt");
        ReflectionTestUtils.setField(flow, "clock", Clock.fixed(now.plusSeconds(61), ZoneOffset.UTC));
        assertNull(flow.redeem(browser, handoff));
        ReflectionTestUtils.setField(flow, "clock", Clock.fixed(now.plusSeconds(601), ZoneOffset.UTC));
        assertFalse(flow.consumeState(browser, state));
    }
    @Test void handoffIsBrowserBoundSingleUseAndNewLoginDiscardsIt() {
        var flow = new OAuthLoginSession();
        var browser = new MockHttpSession();
        String code = flow.issueHandoff(browser, "synthetic-jwt");
        assertNull(flow.redeem(new MockHttpSession(), code));
        assertEquals("synthetic-jwt", flow.redeem(browser, code));
        assertNull(flow.redeem(browser, code));
        String next = flow.issueHandoff(browser, "another-jwt");
        flow.begin(browser);
        assertNull(flow.redeem(browser, next));
    }
    @Test void missingJwtSecretNeverUsesAnImplicitKey() {
        assertThrows(IllegalStateException.class, () -> new JwtService("", 60000));
        assertThrows(IllegalStateException.class, () -> new JwtService("short", 60000));
    }
}
