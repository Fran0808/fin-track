package com.store.api.controller.auth;

import com.store.api.config.GlobalExceptionHandler;
import com.store.api.model.entity.*;
import com.store.api.service.auth.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class GoogleLoginControllerTest {
    @Test void callbackDeliversOnlyASingleUseBrowserBoundCode() throws Exception {
        var google = mock(GoogleOAuthService.class);
        var jwt = mock(JwtService.class);
        var flow = new OAuthLoginSession();
        var controller = new GoogleAuthController(google, jwt, flow);
        ReflectionTestUtils.setField(controller, "frontendSuccessUrl", "https://app.example.test/?auth=google_connected");
        ReflectionTestUtils.setField(controller, "frontendErrorUrl", "https://app.example.test/?auth=google_error");
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        var browser = new MockHttpSession();
        String state = flow.begin(browser);
        var user = User.builder().id(1L).build();
        when(google.exchangeCodeForTokens("synthetic-code")).thenReturn(GoogleOAuthToken.builder().user(user).build());
        when(jwt.generateToken(user)).thenReturn("synthetic-private-jwt");
        String redirect = mvc.perform(get("/api/v1/auth/google/callback").session(browser)
                .param("code", "synthetic-code").param("state", state)).andExpect(status().isFound())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getRedirectedUrl();
        assertNotNull(redirect);
        assertFalse(redirect.contains("synthetic-private-jwt"));
        String code = redirect.substring(redirect.indexOf("#login_code=") + 12);
        mvc.perform(post("/api/v1/auth/google/session").session(new MockHttpSession()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/google/session").session(browser).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("synthetic-private-jwt"));
        mvc.perform(post("/api/v1/auth/google/session").session(browser).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}")).andExpect(status().isUnauthorized());
    }
    @Test void missingOrWrongStateNeverExchangesGoogleCredentials() throws Exception {
        var google = mock(GoogleOAuthService.class);
        var controller = new GoogleAuthController(google, mock(JwtService.class), new OAuthLoginSession());
        ReflectionTestUtils.setField(controller, "frontendErrorUrl", "https://app.example.test/?auth=google_error");
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/v1/auth/google/callback").param("code", "unsolicited"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("https://app.example.test/?auth=google_error"));
        verifyNoInteractions(google);
    }
}
