package com.fintrack.transaction.repository;

import com.fintrack.transaction.dto.TransactionFilter;
import com.fintrack.transaction.entity.Transaction;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Builds the dynamic WHERE clause for transaction searches so filtering happens in SQL, not in memory. */
public final class TransactionSpecifications {

    private static final int MAX_TEXT_LENGTH = 100;

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
        if (filter.text() != null && !filter.text().isBlank()) {
            String text = filter.text().trim();
            if (text.length() > MAX_TEXT_LENGTH) {
                text = text.substring(0, MAX_TEXT_LENGTH);
            }
            String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("description")), pattern, '\\'));
        }
        if (!filter.includeReversed()) {
            spec = spec.and((root, query, cb) -> cb.isNull(root.get("reversedAt")));
        }
        return spec;
    }

    /** Makes %, _ and the escape character match literally, so a search for "50%" is not a wildcard. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Specification<Transaction> ownedBy(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("account").get("userId"), userId);
    }
}
