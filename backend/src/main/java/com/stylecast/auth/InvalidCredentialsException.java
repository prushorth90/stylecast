package com.stylecast.auth;

/**
 * Thrown when a login attempt's email/password combination doesn't match a
 * registered user. Deliberately carries no detail about which field was
 * wrong, or whether the account exists at all. Mapped to HTTP 401 by {@link
 * com.stylecast.common.error.GlobalExceptionHandler}.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
