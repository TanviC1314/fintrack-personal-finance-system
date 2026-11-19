package com.fintrack.account.service;

import com.fintrack.account.dto.CreateAccountRequest;
import com.fintrack.account.entity.Account;
import com.fintrack.account.entity.AccountStatus;
import com.fintrack.account.exception.AccountArchivedException;
import com.fintrack.account.exception.AccountNameAlreadyExistsException;
import com.fintrack.account.exception.AccountNotEmptyException;
import com.fintrack.account.repository.AccountRepository;
import com.fintrack.audit.entity.AuditAction;
import com.fintrack.audit.entity.AuditEntityType;
import com.fintrack.audit.service.AuditService;
import com.fintrack.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account lifecycle: create, read, rename, archive. Every method takes the caller's user id and
 * only ever touches that user's accounts. Balance changes belong to the transaction and transfer
 * services, not here.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final AuditService auditService;

    public AccountService(AccountRepository accountRepository, AuditService auditService) {
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Account create(UUID userId, CreateAccountRequest request) {
        String name = request.name().strip();
        ensureNameIsFree(userId, name);

        BigDecimal openingBalance = request.openingBalance() == null ? BigDecimal.ZERO : request.openingBalance();
        Account account = new Account(userId, name, request.type(), request.currency(), openingBalance);
        Account saved;
        try {
            saved = accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request created the same name between our check and insert.
            throw new AccountNameAlreadyExistsException(name);
        }
        auditService.record(userId, AuditEntityType.ACCOUNT, saved.getId(), AuditAction.CREATED, Map.of(
                "name", name, "type", saved.getType().name(), "currency", saved.getCurrency(),
                "openingBalance", saved.getOpeningBalance()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Account> list(UUID userId, boolean includeArchived) {
        return includeArchived
                ? accountRepository.findAllByUserIdOrderByCreatedAtAsc(userId)
                : accountRepository.findAllByUserIdAndStatusOrderByCreatedAtAsc(userId, AccountStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Account get(UUID userId, UUID accountId) {
        return findOwned(userId, accountId);
    }

    /**
     * Loads the caller's account and locks its row until the surrounding transaction ends. Every
     * balance change must start here so concurrent changes to one account run one after another.
     * Must be called inside an existing transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Account getForUpdate(UUID userId, UUID accountId) {
        return accountRepository.findByIdAndUserIdForUpdate(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    @Transactional
    public Account rename(UUID userId, UUID accountId, String newName) {
        Account account = findOwned(userId, accountId);
        if (!account.isActive()) {
            throw new AccountArchivedException();
        }

        String name = newName.strip();
        // Changing only the letter case of its own name is not a conflict.
        if (!name.equalsIgnoreCase(account.getName())) {
            ensureNameIsFree(userId, name);
        }
        String previousName = account.getName();
        account.rename(name);
        Account saved;
        try {
            saved = accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new AccountNameAlreadyExistsException(name);
        }
        auditService.record(userId, AuditEntityType.ACCOUNT, saved.getId(), AuditAction.RENAMED,
                Map.of("from", previousName, "to", name));
        return saved;
    }

    /** Archives the account. Repeating the call on an already archived account does nothing. */
    @Transactional
    public Account archive(UUID userId, UUID accountId) {
        Account account = findOwned(userId, accountId);
        if (!account.isActive()) {
            return account;
        }
        if (account.getCurrentBalance().signum() != 0) {
            throw new AccountNotEmptyException();
        }
        account.archive();
        // Flushing runs the version check, so a deposit that raced with this call makes it fail.
        Account saved = accountRepository.saveAndFlush(account);
        auditService.record(userId, AuditEntityType.ACCOUNT, saved.getId(), AuditAction.ARCHIVED,
                Map.of("name", saved.getName()));
        return saved;
    }

    private Account findOwned(UUID userId, UUID accountId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private void ensureNameIsFree(UUID userId, String name) {
        if (accountRepository.existsByUserIdAndNameIgnoreCaseAndStatus(userId, name, AccountStatus.ACTIVE)) {
            throw new AccountNameAlreadyExistsException(name);
        }
    }
}
