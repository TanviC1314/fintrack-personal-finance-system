package com.fintrack.transaction.repository;

import com.fintrack.transaction.dto.TransactionFilter;
import com.fintrack.transaction.entity.Transaction;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Builds the dynamic WHERE clause for transaction searches so filtering happens in SQL, not in memory. */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> matching(UUID userId, TransactionFilter filter) {
        // The owner condition is always first and cannot be switched off by any filter value.
        Specification<Transaction> spec = ownedBy(userId);
        if (filter.accountId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("accountId"), filter.accountId()));
        }
        if (filter.type() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), filter.type()));
        }
        if (filter.categoryId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("categoryId"), filter.categoryId()));
        }
        if (filter.from() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transactionDate"), filter.from()));
        }
        if (filter.to() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("transactionDate"), filter.to()));
        }
        if (!filter.includeReversed()) {
            spec = spec.and((root, query, cb) -> cb.isNull(root.get("reversedAt")));
        }
        return spec;
    }

    private static Specification<Transaction> ownedBy(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("account").get("userId"), userId);
    }
}
