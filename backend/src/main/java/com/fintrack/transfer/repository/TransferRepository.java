package com.fintrack.transfer.repository;

import com.fintrack.transfer.entity.Transfer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
}
