package com.github.steveice10.mc.auth.exception.request;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class XboxRequestException extends RequestException {
    public XboxRequestException(String message) {
        super(message);
    }
}
