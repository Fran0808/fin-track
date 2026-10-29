package com.store.api.repository;

import com.store.api.model.entity.TransactionCorrection;
import com.store.api.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TransactionCorrectionRepository extends JpaRepository<TransactionCorrection, Long> {
    Optional<TransactionCorrection> findFirstByTransactionIdAndUserOrderByIdDesc(Long id, User user);
    Optional<TransactionCorrection> findByTransactionIdAndUserAndRequestId(Long id, User user, String requestId);
    Page<TransactionCorrection> findByTransactionIdAndUserOrderByIdDesc(Long id, User user, Pageable pageable);
}
