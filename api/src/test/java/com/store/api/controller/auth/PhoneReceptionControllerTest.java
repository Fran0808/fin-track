package com.store.api.controller.auth;

import com.store.api.model.dto.auth.PhoneReceptionStatusResponse;
import com.store.api.service.auth.PhoneReceptionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PhoneReceptionControllerTest {
    @Test void exposesOnlyUncacheableReceptionTime() throws Exception {
        var service = mock(PhoneReceptionService.class);
        when(service.getStatus()).thenReturn(new PhoneReceptionStatusResponse(Instant.parse("2026-10-27T12:00:00Z")));
        MockMvcBuilders.standaloneSetup(new PhoneReceptionController(service)).build()
                .perform(get("/api/v1/user/phone-status"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"lastReceivedAt\":\"2026-10-27T12:00:00Z\"}"));
    }
}
