package com.fintrack.account.repository;

import com.fintrack.account.entity.Account;
import com.fintrack.account.entity.AccountStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every lookup takes the owner's id, so ownership is enforced by the query itself.
 * An account that belongs to someone else is indistinguishable from one that does not exist.
 */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    List<Account> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    List<Account> findAllByUserIdAndStatusOrderByCreatedAtAsc(UUID userId, AccountStatus status);

    boolean existsByUserIdAndNameIgnoreCaseAndStatus(UUID userId, String name, AccountStatus status);
}
