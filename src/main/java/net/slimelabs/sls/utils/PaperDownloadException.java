package net.slimelabs.sls.utils;

public class PaperDownloadException extends RuntimeException {
    public PaperDownloadException() {
        super("Failed to download jar");
    }

    public PaperDownloadException(String message) {
        super(message);
    }

    public PaperDownloadException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaperDownloadException(Throwable cause) {
        super(cause);
    }
}
