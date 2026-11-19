package com.fintrack.budget.repository;

import com.fintrack.budget.entity.Budget;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every lookup takes the owner's id, so another user's budget is indistinguishable from a missing one. */
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    // The category name is part of every response, so load it with the budget.
    @EntityGraph(attributePaths = "category")
    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = "category")
    List<Budget> findAllByUserIdAndMonthOrderByCategoryNameAscCurrencyAsc(UUID userId, LocalDate month);

    boolean existsByCategoryId(UUID categoryId);
}
