package com.orcaai.shared.error;

/**
 * Also used when a resource exists but belongs to another organization, so responses never reveal
 * whether an id exists elsewhere.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
