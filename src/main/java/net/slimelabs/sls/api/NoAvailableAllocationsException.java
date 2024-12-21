package net.slimelabs.sls.api;

public class NoAvailableAllocationsException extends RuntimeException {
    public NoAvailableAllocationsException() {
        super("No available allocations found.");
    }

    public NoAvailableAllocationsException(String message) {
        super(message);
    }

    public NoAvailableAllocationsException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoAvailableAllocationsException(Throwable cause) {
        super(cause);
    }
}
