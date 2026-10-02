package com.orcaai.shared.error;

/**
 * A request that is well-formed but cannot be fulfilled. The message is shown to the user as is,
 * so it must never contain internal details.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String publicMessage) {
        super(publicMessage);
    }
}
