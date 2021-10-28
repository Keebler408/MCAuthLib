package com.github.steveice10.mc.auth.exception.profile;

import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * Thrown when a profile cannot be found.
 */
@NoArgsConstructor
public class ProfileNotFoundException extends ProfileException {
    @Serial private static final long serialVersionUID = 1L;

    public ProfileNotFoundException(String message) {
        super(message);
    }

    public ProfileNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProfileNotFoundException(Throwable cause) {
        super(cause);
    }
}
