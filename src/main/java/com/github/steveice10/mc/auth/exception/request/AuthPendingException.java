package com.github.steveice10.mc.auth.exception.request;

import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * Thrown when authorisation for a msa oauth code is still pending
 */
@NoArgsConstructor
public class AuthPendingException extends RequestException {
    @Serial private static final long serialVersionUID = 1L;

    public AuthPendingException(String message) {
        super(message);
    }

    public AuthPendingException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuthPendingException(Throwable cause) {
        super(cause);
    }
}
