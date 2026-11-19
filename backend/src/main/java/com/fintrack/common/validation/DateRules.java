package com.fintrack.common.validation;

import com.fintrack.common.exception.InvalidRequestException;
import java.time.Clock;
import java.time.LocalDate;

/** Date checks shared by the features that record money movements. */
public final class DateRules {

    private DateRules() {
    }

    /**
     * Rejects dates in the future. One day of slack is allowed so a user in a timezone ahead of UTC
     * can still record "today" during the hours when their date is already tomorrow in UTC.
     */
    public static void requireNotInFuture(LocalDate date, Clock clock) {
        if (date.isAfter(LocalDate.now(clock).plusDays(1))) {
            throw new InvalidRequestException("Transaction date cannot be in the future");
        }
    }
}
