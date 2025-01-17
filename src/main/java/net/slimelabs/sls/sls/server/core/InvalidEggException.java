package net.slimelabs.sls.server.core;

public class InvalidEggException extends RuntimeException {
    public InvalidEggException() {
        super("Egg not found");
    }

    public InvalidEggException(String message) {
        super(message);
    }

    public InvalidEggException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidEggException(Throwable cause) {
        super(cause);
    }
}
