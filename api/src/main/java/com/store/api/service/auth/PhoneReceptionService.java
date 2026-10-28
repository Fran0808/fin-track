package com.store.api.service.auth;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.auth.PhoneReceptionStatusResponse;
import com.store.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PhoneReceptionService {
    private final UserRepository users;

    @Transactional(readOnly = true)
    public PhoneReceptionStatusResponse getStatus() {
        return new PhoneReceptionStatusResponse(users.findLastPhoneReceivedAt(UserContext.requireCurrentUser().getId()).orElse(null));
    }
}
