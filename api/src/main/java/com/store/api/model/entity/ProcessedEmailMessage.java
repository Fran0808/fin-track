package com.store.api.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tracks individual Gmail message IDs that have already been retrieved and processed
 * to eliminate N+1 detail HTTP calls on subsequent synchronization runs.
 */
@Entity
@Table(name = "processed_email_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEmailMessage {

    @Id
    @Column(name = "message_id", nullable = false, length = 100)
    private String messageId;

    // Existing scoped hashes remain unchanged; legacy rows can have no known owner.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "internal_date_ms")
    private Long internalDateMs;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
