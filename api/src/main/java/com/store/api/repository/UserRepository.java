package com.store.api.repository;

import com.store.api.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE User u SET u.lastPhoneReceivedAt = :now WHERE u.id = :userId AND (u.lastPhoneReceivedAt IS NULL OR u.lastPhoneReceivedAt < :now)")
    int recordPhoneReception(@org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);

    @org.springframework.data.jpa.repository.Query("SELECT u.lastPhoneReceivedAt FROM User u WHERE u.id = :userId")
    Optional<java.time.Instant> findLastPhoneReceivedAt(@org.springframework.data.repository.query.Param("userId") Long userId);

    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleSubId(String googleSubId);

    boolean existsByEmail(String email);

    Optional<User> findByDevicePairingToken(String devicePairingToken);
}
