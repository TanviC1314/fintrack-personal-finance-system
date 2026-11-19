package com.fintrack.account.repository;

import com.fintrack.account.entity.Account;
import com.fintrack.account.entity.AccountStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every lookup takes the owner's id, so ownership is enforced by the query itself.
 * An account that belongs to someone else is indistinguishable from one that does not exist.
 */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    /** Same as {@link #findByIdAndUserId} but takes a row lock (SELECT ... FOR UPDATE) until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id and a.userId = :userId")
    Optional<Account> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    List<Account> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    List<Account> findAllByUserIdAndStatusOrderByCreatedAtAsc(UUID userId, AccountStatus status);

    boolean existsByUserIdAndNameIgnoreCaseAndStatus(UUID userId, String name, AccountStatus status);
}
