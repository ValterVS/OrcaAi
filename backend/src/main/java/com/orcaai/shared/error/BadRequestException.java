package com.orcaai.shared.error;

/** A request that cannot be understood. The message is shown to the user as is. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String publicMessage) {
        super(publicMessage);
    }
}
