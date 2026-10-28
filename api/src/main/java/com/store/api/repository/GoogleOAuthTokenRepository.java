package com.store.api.repository;

import com.store.api.model.entity.GoogleOAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import com.store.api.model.entity.User;

@Repository
public interface GoogleOAuthTokenRepository extends JpaRepository<GoogleOAuthToken, Long> {
    Optional<GoogleOAuthToken> findByEmail(String email);
    Optional<GoogleOAuthToken> findByUserId(Long userId);

    @Modifying
    @Query("UPDATE GoogleOAuthToken t SET t.lastSyncedInternalDate = :cursor WHERE t.user.id = :userId AND (t.lastSyncedInternalDate IS NULL OR t.lastSyncedInternalDate < :cursor)")
    int advanceCursor(@Param("userId") Long userId, @Param("cursor") Long cursor);

    @Modifying
    @Query("""
            UPDATE GoogleOAuthToken t SET
            t.lastSyncFailed = CASE WHEN t.lastSyncAttemptAt IS NULL OR t.lastSyncAttemptAt <= :now THEN :failed ELSE t.lastSyncFailed END,
            t.lastSyncFailureCode = CASE WHEN t.lastSyncAttemptAt IS NULL OR t.lastSyncAttemptAt <= :now THEN :failure ELSE t.lastSyncFailureCode END,
            t.lastSyncAttemptAt = CASE WHEN t.lastSyncAttemptAt IS NULL OR t.lastSyncAttemptAt < :now THEN :now ELSE t.lastSyncAttemptAt END,
            t.lastSuccessfulSyncAt = CASE WHEN :failed = false AND (t.lastSuccessfulSyncAt IS NULL OR t.lastSuccessfulSyncAt < :now)
                THEN :now ELSE t.lastSuccessfulSyncAt END
            WHERE t.user.id = :userId
            """)
    int recordSyncResult(@Param("userId") Long userId, @Param("failed") boolean failed, @Param("now") Instant now, @Param("failure") com.store.api.model.enums.EmailSyncFailure failure);

    @Query("SELECT DISTINCT t.user FROM GoogleOAuthToken t")
    List<User> findConnectedUsers();
}
