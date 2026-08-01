package com.stylecast.auth;

/**
 * Thrown by {@link AuthService#register} when the (normalized) email is
 * already registered. Mapped to HTTP 409 by {@link
 * com.stylecast.common.error.GlobalExceptionHandler}. Deliberately doesn't
 * echo the submitted email back in the message body's wording beyond what
 * the user themselves just typed - this is a registration-time check, not
 * an account-enumeration oracle for a stranger (unlike login, which never
 * reveals whether an account exists at all).
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("An account with that email already exists");
    }
}
