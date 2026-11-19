package com.fintrack.transaction.repository;

import com.fintrack.transaction.entity.Transaction;
import com.fintrack.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
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

    // ---- aggregates for the dashboard and budgets ----
    // All of them count only the caller's non-reversed transactions in accounts of one currency, because
    // amounts in different currencies cannot be added together. Totals are computed by the database.

    /** Sum of one transaction type between two dates, both inclusive; zero when there are none. */
    @Query("""
            select coalesce(sum(t.amount), 0)
            from Transaction t
            where t.account.userId = :userId and t.account.currency = :currency
              and t.type = :type and t.reversedAt is null
              and t.transactionDate between :from and :to
            """)
    BigDecimal sumAmount(@Param("userId") UUID userId, @Param("currency") String currency,
                         @Param("type") TransactionType type, @Param("from") LocalDate from,
                         @Param("to") LocalDate to);

    /** Totals per calendar month and type, only for months that have transactions. */
    @Query("""
            select new com.fintrack.transaction.repository.MonthlyTypeTotal(
                       year(t.transactionDate), month(t.transactionDate), t.type, sum(t.amount))
            from Transaction t
            where t.account.userId = :userId and t.account.currency = :currency
              and t.type in :types and t.reversedAt is null
              and t.transactionDate between :from and :to
            group by year(t.transactionDate), month(t.transactionDate), t.type
            """)
    List<MonthlyTypeTotal> monthlyTotals(@Param("userId") UUID userId, @Param("currency") String currency,
                                         @Param("types") Collection<TransactionType> types,
                                         @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Expense totals per category, largest first. Uncategorised expenses form one row with null id and name. */
    @Query("""
            select new com.fintrack.transaction.repository.CategoryTotal(c.id, c.name, sum(t.amount))
            from Transaction t left join t.category c
            where t.account.userId = :userId and t.account.currency = :currency
              and t.type = com.fintrack.transaction.entity.TransactionType.EXPENSE and t.reversedAt is null
              and t.transactionDate between :from and :to
            group by c.id, c.name
            order by sum(t.amount) desc
            """)
    List<CategoryTotal> expenseTotalsByCategory(@Param("userId") UUID userId, @Param("currency") String currency,
                                                @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** The newest transactions, including transfer legs; pass a PageRequest to limit how many. */
    @EntityGraph(attributePaths = "category")
    @Query("""
            select t from Transaction t
            where t.account.userId = :userId and t.account.currency = :currency and t.reversedAt is null
            order by t.transactionDate desc, t.createdAt desc, t.id desc
            """)
    List<Transaction> findRecent(@Param("userId") UUID userId, @Param("currency") String currency,
                                 Pageable pageable);

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
