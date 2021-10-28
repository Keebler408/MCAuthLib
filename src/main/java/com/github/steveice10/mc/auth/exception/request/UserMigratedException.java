package com.github.steveice10.mc.auth.exception.request;

import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * Thrown when using the username of an account that has been migrated to an email address.
 */
@NoArgsConstructor
public class UserMigratedException extends InvalidCredentialsException {
    @Serial private static final long serialVersionUID = 1L;

    public UserMigratedException(String message) {
        super(message);
    }

    public UserMigratedException(String message, Throwable cause) {
        super(message, cause);
    }

    public UserMigratedException(Throwable cause) {
        super(cause);
    }
}
