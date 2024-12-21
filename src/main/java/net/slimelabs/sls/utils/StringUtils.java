package net.slimelabs.sls.utils;

public class StringUtils {
    public static String removeTextAfterPeriod(String input) {
        int index = input.indexOf('.');
        if (index == -1) {
            // No period found, return the input string as is
            return input;
        }
        // Return the substring before the first period
        return input.substring(0, index);
    }
}
