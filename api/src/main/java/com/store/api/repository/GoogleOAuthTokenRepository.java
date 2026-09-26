package com.store.api.repository;

import com.store.api.model.entity.GoogleOAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import com.store.api.model.entity.User;

@Repository
public interface GoogleOAuthTokenRepository extends JpaRepository<GoogleOAuthToken, Long> {
    Optional<GoogleOAuthToken> findByEmail(String email);
    Optional<GoogleOAuthToken> findByUserId(Long userId);

    @Query("SELECT DISTINCT t.user FROM GoogleOAuthToken t")
    List<User> findConnectedUsers();
}
