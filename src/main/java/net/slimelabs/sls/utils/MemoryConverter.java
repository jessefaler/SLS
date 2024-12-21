package net.slimelabs.sls.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class for converting string representations of memory sizes into integers representing megabytes.
 * <p>
 * This class can parse various formats for memory sizes, including:<ul>
 *
 *   <li>Gigabytes (e.g., "2.5GB", "5 gigabytes")</li>
 *   <li>Megabytes (e.g., "500MB", "1 megabyte")</li>
 *   <li>Kilobytes (e.g., "4000KB")</li></ul></p><p>
 *
 * The conversion is performed using the binary system (base-2) conversion sizes. In this system, 1 gigabyte is equivalent to 1024 megabytes.
 * This differs from the decimal system (base-10), where 1 gigabyte is considered to be 1000 megabytes.</p><p>
 * The class supports case-insensitive parsing and various formats for memory size representations.</p>
 */
public class MemoryConverter {
    public static int convertToMegabytes(String memorySize) {
        Pattern pattern = Pattern.compile("(\\d+(\\.\\d+)?)\\s*(\\w+)");
        Matcher matcher = pattern.matcher(memorySize.toLowerCase());

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid memory size format: " + memorySize);
        }

        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(3);

        return switch (unit) {
            case "gb", "gigabyte", "gigabytes", "g" -> (int) (value * 1024);
            case "mb", "megabyte", "megabytes", "m" -> (int) value;
            case "kb", "kilobyte", "kilobytes", "k" -> (int) (value / 1024);
            default -> throw new IllegalArgumentException("Unsupported memory unit: " + value + unit + "\n Supported units gb, mb, kb");
        };
    }
}
