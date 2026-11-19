package com.fintrack.budget.entity;

import com.fintrack.category.entity.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A spending limit for one category in one calendar month and currency. */
@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, updatable = false)
    private Category category;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private LocalDate month;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Budget() {
        // required by JPA
    }

    /** @param month the first day of the month */
    public Budget(UUID userId, Category category, String currency, LocalDate month, BigDecimal amount) {
        this.userId = userId;
        this.category = category;
        this.currency = currency;
        this.month = month;
        this.amount = amount;
    }

    public void changeAmount(BigDecimal newAmount) {
        this.amount = newAmount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Category getCategory() {
        return category;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getMonth() {
        return month;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
