package com.fintrack.transaction.repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Total spent in one category. For transactions without a category both {@code categoryId} and
 * {@code categoryName} are null.
 */
public record CategoryTotal(UUID categoryId, String categoryName, BigDecimal total) {
}
