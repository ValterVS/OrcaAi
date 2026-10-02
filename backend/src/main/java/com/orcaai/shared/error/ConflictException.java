package com.orcaai.shared.error;

/**
 * The resource changed since the client read it. The message is shown to the user as is.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String publicMessage) {
        super(publicMessage);
    }
}
