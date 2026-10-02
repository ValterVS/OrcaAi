package com.orcaai.shared.error;

/** A change to an existing resource arrived without If-Match (428). */
public class PreconditionRequiredException extends RuntimeException {

    public PreconditionRequiredException() {
        super("If-Match is required");
    }
}
