package com.github.steveice10.mc.auth.exception.profile;

import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * Thrown when an error occurs while looking up a profile.
 */
@NoArgsConstructor
public class ProfileLookupException extends ProfileException {
    @Serial private static final long serialVersionUID = 1L;

    public ProfileLookupException(String message) {
        super(message);
    }

    public ProfileLookupException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProfileLookupException(Throwable cause) {
        super(cause);
    }
}
