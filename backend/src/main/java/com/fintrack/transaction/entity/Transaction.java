package com.fintrack.transaction.entity;

import com.fintrack.account.entity.Account;
import com.fintrack.category.entity.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    // Read-only copy of the foreign key so the account id is available without loading the account.
    @Column(name = "account_id", insertable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    // Read-only copy of the foreign key so filters and aggregates need no join and the id is always available.
    @Column(name = "category_id", insertable = false, updatable = false)
    private UUID categoryId;

    @Column(length = 255)
    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    // Set only on the two legs created by a transfer.
    @Column(name = "transfer_id", updatable = false)
    private UUID transferId;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {
        // required by JPA
    }

    public Transaction(Account account, TransactionType type, BigDecimal amount, Category category,
                       String description, LocalDate transactionDate) {
        this.account = account;
        this.accountId = account.getId();
        this.type = type;
        this.amount = amount;
        this.category = category;
        this.categoryId = category == null ? null : category.getId();
        this.description = description;
        this.transactionDate = transactionDate;
    }

    /** One side of a transfer: TRANSFER_OUT on the source account or TRANSFER_IN on the destination. */
    public static Transaction transferLeg(Account account, TransactionType type, BigDecimal amount,
                                          String description, LocalDate date, UUID transferId) {
        if (!type.isTransfer()) {
            throw new IllegalArgumentException("A transfer leg must be TRANSFER_IN or TRANSFER_OUT");
        }
        Transaction leg = new Transaction(account, type, amount, null, description, date);
        leg.transferId = transferId;
        return leg;
    }

    public void update(BigDecimal amount, Category category, String description, LocalDate transactionDate) {
        this.amount = amount;
        this.category = category;
        this.categoryId = category == null ? null : category.getId();
        this.description = description;
        this.transactionDate = transactionDate;
    }

    public void markReversed(Instant when) {
        this.reversedAt = when;
    }

    public boolean isReversed() {
        return reversedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Category getCategory() {
        return category;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
