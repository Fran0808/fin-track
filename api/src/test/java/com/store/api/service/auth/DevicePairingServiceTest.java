package com.store.api.service.auth;

import com.store.api.model.dto.auth.DevicePairingInfoResponse;
import com.store.api.model.dto.auth.DevicePairingVerifyResponse;
import com.store.api.model.entity.User;
import com.store.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DevicePairingServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DevicePairingService devicePairingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(devicePairingService, "publicServerUrl", "http://192.168.1.5:8080");
        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .fullName("Test User")
                .devicePairingToken("wp_dev_testtoken123")
                .build();
    }

    @Test
    void getPairingInfo_whenTokenExists_returnsInfo() {
        DevicePairingInfoResponse response = devicePairingService.getPairingInfo(testUser);

        assertNotNull(response);
        assertEquals("wp_dev_testtoken123", response.getPairingToken());
        assertEquals("test@example.com", response.getUserEmail());
        assertEquals("http://192.168.1.5:8080", response.getServerUrl());
        assertTrue(response.getQrPayload().contains("wp_dev_testtoken123"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void getPairingInfo_whenTokenNull_generatesAndSavesToken() {
        testUser.setDevicePairingToken(null);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DevicePairingInfoResponse response = devicePairingService.getPairingInfo(testUser);

        assertNotNull(response);
        assertNotNull(response.getPairingToken());
        assertTrue(response.getPairingToken().startsWith("wp_dev_"));
        verify(userRepository).save(testUser);
    }

    @Test
    void regeneratePairingToken_updatesToken() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DevicePairingInfoResponse response = devicePairingService.regeneratePairingToken(testUser);

        assertNotNull(response);
        assertNotEquals("wp_dev_testtoken123", response.getPairingToken());
        assertTrue(response.getPairingToken().startsWith("wp_dev_"));
        verify(userRepository).save(testUser);
    }

    @Test
    void verifyDeviceToken_withValidToken_returnsSuccess() {
        when(userRepository.findByDevicePairingToken("wp_dev_testtoken123")).thenReturn(Optional.of(testUser));

        DevicePairingVerifyResponse response = devicePairingService.verifyDeviceToken("wp_dev_testtoken123");

        assertTrue(response.isValid());
        assertEquals(1L, response.getUserId());
        assertEquals("test@example.com", response.getUserEmail());
    }

    @Test
    void verifyDeviceToken_withInvalidToken_returnsFailure() {
        when(userRepository.findByDevicePairingToken("invalid_token")).thenReturn(Optional.empty());

        DevicePairingVerifyResponse response = devicePairingService.verifyDeviceToken("invalid_token");

        assertFalse(response.isValid());
        assertEquals("Invalid or revoked device token", response.getMessage());
    }
}
