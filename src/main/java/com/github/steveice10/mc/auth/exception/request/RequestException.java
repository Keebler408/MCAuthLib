package com.github.steveice10.mc.auth.exception.request;

import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * Thrown when an error occurs while making an HTTP request.
 */
@NoArgsConstructor
public class RequestException extends Exception {
    @Serial private static final long serialVersionUID = 1L;

    public RequestException(String message) {
        super(message);
    }

    public RequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public RequestException(Throwable cause) {
        super(cause);
    }
}
