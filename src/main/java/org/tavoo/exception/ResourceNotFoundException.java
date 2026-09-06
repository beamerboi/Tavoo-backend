package org.tavoo.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " with id " + id + " was not found");
    }

    public ResourceNotFoundException(String resource, String identifier) {
        super(resource + " '" + identifier + "' was not found");
    }
}
