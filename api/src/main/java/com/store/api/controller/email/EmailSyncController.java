package com.store.api.controller.email;

import com.store.api.model.dto.email.EmailConnectionTestResponse;
import com.store.api.model.dto.email.EmailSyncResponse;
import com.store.api.service.email.EmailIngestionService;
import com.store.api.service.email.EmailSyncException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/emails")
@RequiredArgsConstructor
@Slf4j
public class EmailSyncController {

    private final EmailIngestionService emailIngestionService;

    @GetMapping("/test-connection")
    public ResponseEntity<EmailConnectionTestResponse> testConnection() {
        EmailConnectionTestResponse result = emailIngestionService.testConnection();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sync")
    public ResponseEntity<EmailSyncResponse> syncEmails() {
        EmailSyncResponse result = emailIngestionService.syncEmails();
        log.info("Manual email sync: status={}, scanned={}, saved={}",
                result.getStatus(), result.getScannedCount(), result.getSavedCount());
        return ResponseEntity.ok(result);
    }

    @ExceptionHandler(EmailSyncException.class)
    public ResponseEntity<Map<String, Object>> handleEmailSyncFailure(EmailSyncException exception) {
        return switch (exception.getFailure()) {
            case RATE_LIMITED -> syncError(HttpStatus.TOO_MANY_REQUESTS,
                    "Gmail limitó temporalmente las consultas. Espera unos minutos antes de volver a sincronizar.");
            case AUTH_REQUIRED -> syncError(HttpStatus.CONFLICT,
                    "Vuelve a conectar Gmail para continuar la sincronización.");
            case ACCESS_DENIED -> syncError(HttpStatus.FORBIDDEN,
                    "Gmail rechazó la consulta. Revisa el diagnóstico de correo en Configuración.");
            case TEMPORARY -> syncError(HttpStatus.SERVICE_UNAVAILABLE,
                    "La conexión con Gmail se interrumpió. Inténtalo más tarde.");
            default -> syncError(HttpStatus.BAD_GATEWAY,
                    "La sincronización de correo no pudo completarse. Los movimientos ya guardados se conservan.");
        };
    }

    private ResponseEntity<Map<String, Object>> syncError(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "message", message));
    }
}
