package net.slimelabs.sls.installer;

public class InstalliationException extends RuntimeException {
    public InstalliationException() {
        super("An exception occurred while installing the server.");
    }

    public InstalliationException(String message) {
        super(message);
    }

    public InstalliationException(String message, Throwable cause) {
        super(message, cause);
    }

    public InstalliationException(Throwable cause) {
        super(cause);
    }
}
