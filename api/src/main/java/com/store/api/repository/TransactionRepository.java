package com.store.api.repository;

import com.store.api.model.entity.Transaction;
import com.store.api.model.entity.User;
import com.store.api.model.enums.FlowType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    boolean existsByTransactionHashAndUser(String transactionHash, User user);

    Optional<Transaction> findByTransactionHashAndUser(String transactionHash, User user);
    Optional<Transaction> findByIdAndUser(Long id, User user);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.flowType = :flowType AND t.user.id = :userId")
    BigDecimal sumAmountByFlowType(@Param("flowType") FlowType flowType, @Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.flowType = :flowType AND t.transactionDate >= :start AND t.transactionDate <= :end AND t.user.id = :userId")
    BigDecimal sumAmountByFlowTypeAndDateRange(
            @Param("flowType") FlowType flowType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("userId") Long userId
    );

    @Query("SELECT t FROM Transaction t WHERE t.flowType = 'EXPENSE' AND t.user.id = :userId ORDER BY t.transactionDate DESC LIMIT 1")
    Optional<Transaction> findLatestExpense(@Param("userId") Long userId);

    @Query("SELECT t.channel, t.cardLast4, COALESCE(SUM(t.amount), 0), COUNT(t) FROM Transaction t WHERE t.flowType = 'EXPENSE' AND t.transactionDate >= :start AND t.transactionDate <= :end AND t.user.id = :userId GROUP BY t.channel, t.cardLast4 ORDER BY SUM(t.amount) DESC")
    List<Object[]> findExpenseBreakdownByChannel(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("userId") Long userId
    );

    @Query("SELECT t.contactName, COALESCE(SUM(t.amount), 0), COUNT(t) FROM Transaction t WHERE t.flowType = 'EXPENSE' AND t.transactionDate >= :start AND t.transactionDate <= :end AND t.user.id = :userId GROUP BY t.contactName ORDER BY COUNT(t) DESC, SUM(t.amount) DESC LIMIT 5")
    List<Object[]> findTopMerchants(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("userId") Long userId
    );

    @Query("SELECT t.transactionDate, t.amount FROM Transaction t WHERE t.flowType = 'EXPENSE' AND t.transactionDate >= :start AND t.transactionDate <= :end AND t.user.id = :userId ORDER BY t.transactionDate")
    List<Object[]> findExpenseAmountsByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("userId") Long userId
    );

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
}
