package com.fintrack.transfer.repository;

import com.fintrack.transfer.entity.Transfer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransferRepository extends JpaRepository<Transfer, UUID>, JpaSpecificationExecutor<Transfer> {

    /** Keys are scoped to the user, so one user can never collide with or replay another user's key. */
    Optional<Transfer> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
