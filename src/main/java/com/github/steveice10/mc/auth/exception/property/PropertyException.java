package com.github.steveice10.mc.auth.exception.property;

import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * Thrown when a property-related error occurs.
 */
@NoArgsConstructor
public class PropertyException extends Exception {
    @Serial private static final long serialVersionUID = 1L;

    public PropertyException(String message) {
        super(message);
    }

    public PropertyException(String message, Throwable cause) {
        super(message, cause);
    }

    public PropertyException(Throwable cause) {
        super(cause);
    }
}
