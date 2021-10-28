package com.github.steveice10.mc.auth.exception.property;

import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * Thrown when an error occurs while retrieving a profile texture.
 */
@NoArgsConstructor
public class ProfileTextureException extends PropertyException {
    @Serial private static final long serialVersionUID = 1L;

    public ProfileTextureException(String message) {
        super(message);
    }

    public ProfileTextureException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProfileTextureException(Throwable cause) {
        super(cause);
    }
}
