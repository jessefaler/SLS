package net.slimelabs.sls.utils;

/**
 * Enum for ANSI color codes
 * Provides a set of constants for different text colors and styles
 */
public enum Color {
    RESET("\u001B[0m"),
    YELLOW("\u001B[33m"),
    GREEN("\u001B[32m"),
    CYAN("\u001B[36m"),
    BLUE("\u001B[34m"),
    RED("\u001B[31m"),
    DARK_GRAY("\u001B[90m"),
    MAGENTA("\u001B[35m"),
    LIGHT_BLUE("\u001B[94m"),
    ORANGE("\u001B[38;5;214m"),
    PURPLE("\u001B[38;5;135m"),
    DARK_GREEN("\u001B[32m");

    private final String code;

    Color(String code) {
        this.code = code;
    }

    public String toString() {
        return code;
    }
}