package com.github.steveice10.mc.auth.exception.property;

import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * Thrown when an error occurs while validating a signature.
 */
@NoArgsConstructor
public class SignatureValidateException extends PropertyException {
    @Serial private static final long serialVersionUID = 1L;

    public SignatureValidateException(String message) {
        super(message);
    }

    public SignatureValidateException(String message, Throwable cause) {
        super(message, cause);
    }

    public SignatureValidateException(Throwable cause) {
        super(cause);
    }
}
