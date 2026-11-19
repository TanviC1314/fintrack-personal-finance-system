package com.fintrack.transaction.repository;

import com.fintrack.transaction.entity.Transaction;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Ownership of a transaction is the ownership of its account, so every lookup also takes the user id. */
public interface TransactionRepository
        extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    // The category name is shown in every response, so load it with the transaction (the controller runs
    // outside the transaction, where a lazy association could no longer be read).
    @EntityGraph(attributePaths = "category")
    Optional<Transaction> findByIdAndAccountUserId(UUID id, UUID userId);

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);

    boolean existsByCategoryId(UUID categoryId);

    /**
     * Returns only the account id, without loading the transaction. Callers use it to lock the account
     * first and then load the transaction, so the state they act on cannot be stale.
     */
    @Query("select t.accountId from Transaction t where t.id = :id and t.account.userId = :userId")
    Optional<UUID> findAccountIdByIdAndOwner(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * The ledger's view of an account's movement: credits minus debits of all non-reversed transactions.
     * An account's current balance must equal its opening balance plus this value.
     */
    @Query("""
            select coalesce(sum(case when t.type in (
                    com.fintrack.transaction.entity.TransactionType.INCOME,
                    com.fintrack.transaction.entity.TransactionType.TRANSFER_IN)
                then t.amount else -t.amount end), 0)
            from Transaction t
            where t.accountId = :accountId and t.reversedAt is null
            """)
    BigDecimal ledgerTotal(@Param("accountId") UUID accountId);
}
