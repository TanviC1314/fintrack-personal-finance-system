package com.fintrack.transaction.repository;

import com.fintrack.transaction.entity.TransactionType;
import java.math.BigDecimal;

/** One row of the monthly aggregate: the total of one transaction type in one calendar month. */
public record MonthlyTypeTotal(Integer year, Integer month, TransactionType type, BigDecimal total) {
}
