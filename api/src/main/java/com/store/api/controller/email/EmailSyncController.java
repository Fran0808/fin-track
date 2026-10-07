package com.store.api.controller.email;

import com.store.api.model.dto.email.EmailConnectionTestResponse;
import com.store.api.model.dto.email.EmailSyncResponse;
import com.store.api.service.email.EmailIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/emails")
@RequiredArgsConstructor
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
        return ResponseEntity.ok(result);
    }
}
