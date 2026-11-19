package com.fintrack.account.entity;

import com.fintrack.account.exception.InsufficientFundsException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Held as a plain id: ownership checks only need the id, so there is no reason to load the User.
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AccountType type;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "opening_balance", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
        // required by JPA
    }

    public Account(UUID userId, String name, AccountType type, String currency, BigDecimal openingBalance) {
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.openingBalance = openingBalance;
        this.currentBalance = openingBalance;
        this.status = AccountStatus.ACTIVE;
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public void archive() {
        this.status = AccountStatus.ARCHIVED;
    }

    /**
     * Adds money. Callers must hold the row lock (see AccountRepository#findByIdAndUserIdForUpdate)
     * so the read-modify-write on the balance cannot interleave with another request.
     */
    public void credit(BigDecimal amount) {
        requirePositive(amount);
        currentBalance = currentBalance.add(amount);
    }

    /** Removes money; the balance can never go below zero. Same locking requirement as {@link #credit}. */
    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (currentBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }
        currentBalance = currentBalance.subtract(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
