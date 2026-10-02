package com.orcaai.shared.error;

/**
 * The client's If-Match no longer matches the resource: someone changed it since it was read.
 * Answered with 412 instead of overwriting that change.
 */
public class PreconditionFailedException extends RuntimeException {

    public PreconditionFailedException() {
        super("If-Match does not match the current version");
    }
}
