package net.slimelabs.vsls.utils;

import java.util.HashMap;
import java.util.Map;

public class ColorString {

    private static final Map<String, String> COLORS = new HashMap<>();

    static {
        COLORS.put("reset", "\u001b[0m");
        COLORS.put("black", "\u001b[30m");
        COLORS.put("red", "\u001b[31m");
        COLORS.put("green", "\u001b[32m");
        COLORS.put("yellow", "\u001b[33m");
        COLORS.put("blue", "\u001b[34m");
        COLORS.put("magenta", "\u001b[35m");
        COLORS.put("cyan", "\u001b[36m");
        COLORS.put("light_gray", "\u001b[37m");

        COLORS.put("dark_gray", "\u001b[90m");
        COLORS.put("light_red", "\u001b[91m");
        COLORS.put("light_green", "\u001b[92m");
        COLORS.put("light_yellow", "\u001b[93m");
        COLORS.put("light_blue", "\u001b[94m");
        COLORS.put("light_magenta", "\u001b[95m");
        COLORS.put("light_cyan", "\u001b[96m");
        COLORS.put("white", "\u001b[97m");
    }

    public static String parse(String input) {
        StringBuilder result = new StringBuilder();
        int i = 0;

        while (i < input.length()) {
            int open = input.indexOf('[', i);
            if (open == -1) {
                result.append(input.substring(i));
                break;
            }

            result.append(input, i, open);

            int close = input.indexOf(']', open);
            if (close == -1) {
                result.append(input.substring(open));
                break;
            }

            String tag = input.substring(open + 1, close).trim().toLowerCase();
            String color = COLORS.get(tag);

            if (color != null) {
                result.append(color);
            } else {
                result.append(input, open, close + 1);
            }

            i = close + 1;
        }

        // Reset before *every* newline
        String processed = result.toString()
                .replace("\n", COLORS.get("reset") + "\n");

        // Also reset at final end to be 100% safe
        return processed + COLORS.get("reset");
    }
}
