package com.store.api.repository;

import com.store.api.model.entity.FinancialInstrument;
import com.store.api.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FinancialInstrumentRepository extends JpaRepository<FinancialInstrument, Long> {
    List<FinancialInstrument> findByUserOrderByAliasAscIdAsc(User user);
    Optional<FinancialInstrument> findByIdAndUser(Long id, User user);
    boolean existsByLinkedAccount(FinancialInstrument account);
}
