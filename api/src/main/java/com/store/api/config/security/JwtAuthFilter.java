package com.store.api.config.security;

import com.store.api.model.entity.User;
import com.store.api.repository.UserRepository;
import com.store.api.service.auth.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.CorsUtils;

import java.io.IOException;
import java.util.Optional;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        UserContext.clear();
        try {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            boolean publicAuthEndpoint = "GET".equals(request.getMethod())
                    && ("/api/v1/auth/google/url".equals(path)
                    || "/api/v1/auth/google/callback".equals(path));
            if (publicAuthEndpoint || CorsUtils.isPreFlightRequest(request)) {
                filterChain.doFilter(request, response);
                return;
            }

            String authHeader = request.getHeader(AUTH_HEADER);
            Optional<User> user = Optional.empty();
            if (authHeader != null && authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                String token = authHeader.substring(BEARER_PREFIX.length()).trim();
                if (!token.isEmpty()) {
                    user = jwtService.extractUserId(token).flatMap(userRepository::findById);
                }
            }
            boolean isMobileSyncEndpoint = "POST".equals(request.getMethod())
                    && path.startsWith("/api/v1/transactions/sync");
            boolean isAppUpdateEndpoint = "GET".equals(request.getMethod())
                    && path.startsWith("/api/v1/app/");

            if (user.isEmpty()) {
                if (isMobileSyncEndpoint) {
                    User defaultUser = userRepository.findById(1L)
                            .orElseGet(() -> userRepository.findAll().stream().findFirst().orElse(null));
                    if (defaultUser != null) {
                        UserContext.setCurrentUser(defaultUser);
                    }
                    filterChain.doFilter(request, response);
                    return;
                }
                if (isAppUpdateEndpoint) {
                    filterChain.doFilter(request, response);
                    return;
                }
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader("WWW-Authenticate", "Bearer");
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Authentication required\"}");
                return;
            }
            UserContext.setCurrentUser(user.get());
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
