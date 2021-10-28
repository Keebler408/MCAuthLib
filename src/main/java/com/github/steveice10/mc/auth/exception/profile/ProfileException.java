package com.github.steveice10.mc.auth.exception.profile;

import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * Thrown when a profile-related error occurs.
 */
@NoArgsConstructor

public class ProfileException extends Exception {
    @Serial private static final long serialVersionUID = 1L;

    public ProfileException(String message) {
        super(message);
    }

    public ProfileException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProfileException(Throwable cause) {
        super(cause);
    }
}
