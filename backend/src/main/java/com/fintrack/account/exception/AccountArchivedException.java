package com.fintrack.account.exception;

import com.fintrack.common.exception.ConflictException;

public class AccountArchivedException extends ConflictException {

    public AccountArchivedException() {
        super("This account is archived and can no longer be changed");
    }
}
