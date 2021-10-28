package com.github.steveice10.mc.auth.exception.request;

import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * Thrown when invalid credentials are provided.
 */
@NoArgsConstructor
public class InvalidCredentialsException extends RequestException {
    @Serial private static final long serialVersionUID = 1L;

    public InvalidCredentialsException(String message) {
        super(message);
    }

    public InvalidCredentialsException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidCredentialsException(Throwable cause) {
        super(cause);
    }
}
