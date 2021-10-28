package com.github.steveice10.mc.auth.exception.request;

import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * Thrown when a service is unavailable.
 */
@NoArgsConstructor
public class ServiceUnavailableException extends RequestException {
    @Serial private static final long serialVersionUID = 1L;

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServiceUnavailableException(Throwable cause) {
        super(cause);
    }
}
