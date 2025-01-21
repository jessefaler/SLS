package net.slimelabs.sls.utils;

/**
 * A utility class that provides static methods for performing various operations on strings.
 */
public class StringUtils {

    private StringUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Removes any text that appears after the first period in a given string. For example,
     * for the input "example.string", the output will be "example". This method is primarily
     * used to remove the namespace from server names.
     *
     * @param input the input string, which may contain a period to separate the namespace
     * @return the input string with everything after the first period removed, or the input string
     *         itself if no period is found
     */
    public static String truncateAtPeriod(String input) {
        int index = input.indexOf('.');
        if (index == -1) {
            // No period found, return the input string as is
            return input;
        }
        // Return the substring before the first period
        return input.substring(0, index);
    }
}
