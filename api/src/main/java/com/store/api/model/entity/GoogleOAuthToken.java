package com.store.api.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.Instant;

@Entity
@Table(name = "google_oauth_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@org.hibernate.annotations.DynamicUpdate
public class GoogleOAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private String scope;

    @Column(name = "last_synced_internal_date")
    private Long lastSyncedInternalDate;

    @Column(name = "last_successful_sync_at")
    private Instant lastSuccessfulSyncAt;

    @Column(name = "last_sync_failed")
    private Boolean lastSyncFailed;

    private Instant lastSyncAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private com.store.api.model.enums.EmailSyncFailure lastSyncFailureCode;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        // Safety buffer of 60 seconds before actual expiration
        return LocalDateTime.now().isAfter(expiresAt.minusSeconds(60));
    }
}
