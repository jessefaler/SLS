package net.slimelabs.vsls.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TimeUtils {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("MMM d HH:mm:ss.SSS");

    /**
     * Formats an Instant to a string like "Dec  5 01:46:34.040"
     *
     * @param timestamp the Instant to format
     * @return formatted timestamp string
     */
    public static String formatTimestamp(Instant timestamp) {
        if (timestamp == null) return "null";
        ZonedDateTime zdt = timestamp.atZone(ZoneId.systemDefault());
        return zdt.format(FORMATTER);
    }

}
