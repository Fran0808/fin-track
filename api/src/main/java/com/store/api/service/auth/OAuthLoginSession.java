package com.store.api.service.auth;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class OAuthLoginSession {
    private static final String STATE = OAuthLoginSession.class.getName() + ".state";
    private static final String HANDOFF = OAuthLoginSession.class.getName() + ".handoff";
    private final SecureRandom random = new SecureRandom();
    private java.time.Clock clock = java.time.Clock.systemUTC();
    private record Pending(String code, String token, Instant expiresAt) implements java.io.Serializable {}

    public String begin(HttpSession session) {
        synchronized (session) {
            session.setMaxInactiveInterval(600);
            session.removeAttribute(HANDOFF);
            String state = randomCode();
            session.setAttribute(STATE, new Pending(state, null, Instant.now(clock).plusSeconds(600)));
            return state;
        }
    }

    public boolean consumeState(HttpSession session, String state) {
        if (session == null || state == null) return false;
        synchronized (session) {
            if (!(session.getAttribute(STATE) instanceof Pending pending) || !matches(pending, state)) return false;
            session.removeAttribute(STATE);
            return true;
        }
    }

    public String issueHandoff(HttpSession session, String token) {
        synchronized (session) {
            String code = randomCode();
            session.setAttribute(HANDOFF, new Pending(code, token, Instant.now(clock).plusSeconds(60)));
            return code;
        }
    }

    public String redeem(HttpSession session, String code) {
        if (session == null || code == null) return null;
        synchronized (session) {
            if (!(session.getAttribute(HANDOFF) instanceof Pending pending) || !matches(pending, code)) return null;
            session.removeAttribute(HANDOFF);
            return pending.token();
        }
    }

    private boolean matches(Pending pending, String code) {
        return Instant.now(clock).isBefore(pending.expiresAt()) && MessageDigest.isEqual(
                pending.code().getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8));
    }

    private String randomCode() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
