package com.fintrack.security;

import java.util.UUID;

/** The identity of the caller, taken from a verified access token. */
public record AuthenticatedUser(UUID id, String email) {
}
