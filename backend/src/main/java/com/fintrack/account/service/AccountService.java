package com.fintrack.account.service;

import com.fintrack.account.dto.CreateAccountRequest;
import com.fintrack.account.entity.Account;
import com.fintrack.account.entity.AccountStatus;
import com.fintrack.account.exception.AccountArchivedException;
import com.fintrack.account.exception.AccountNameAlreadyExistsException;
import com.fintrack.account.exception.AccountNotEmptyException;
import com.fintrack.account.repository.AccountRepository;
import com.fintrack.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account lifecycle: create, read, rename, archive. Every method takes the caller's user id and
 * only ever touches that user's accounts. Balance changes belong to the transaction and transfer
 * services, not here.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account create(UUID userId, CreateAccountRequest request) {
        String name = request.name().strip();
        ensureNameIsFree(userId, name);

        BigDecimal openingBalance = request.openingBalance() == null ? BigDecimal.ZERO : request.openingBalance();
        Account account = new Account(userId, name, request.type(), request.currency(), openingBalance);
        try {
            return accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request created the same name between our check and insert.
            throw new AccountNameAlreadyExistsException(name);
        }
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
        account.rename(name);
        try {
            return accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new AccountNameAlreadyExistsException(name);
        }
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
        return accountRepository.saveAndFlush(account);
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
