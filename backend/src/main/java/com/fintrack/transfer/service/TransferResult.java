package com.fintrack.transfer.service;

import com.fintrack.transfer.entity.Transfer;

/**
 * @param replayed true when the request repeated an earlier one (same idempotency key) and the
 *                 original transfer is returned without moving any money
 */
public record TransferResult(Transfer transfer, boolean replayed) {
}
