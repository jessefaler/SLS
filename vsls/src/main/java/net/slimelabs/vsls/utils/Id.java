package net.slimelabs.vsls.utils;

import java.util.List;

public class Id {

    // Returns a list of ids shortened to the desired length
    public static List<String> shortIds(List<String> ids, int length) {
        return ids.stream()
                .map(id -> shortId(id, length))
                .toList();
    }

    // Returns a single id shortened to the desired length
    public static String shortId(String id, int length) {
        return id.length() <= length ? id : id.substring(0, length);
    }

    // Find full ID from short ID
    public static String findFullId(String shortId, List<String> ids) {
        for (String id : ids) {
            if (id.startsWith(shortId)) {
                return id; // return the first match
            }
        }
        return null; // or throw exception if not found
    }

}
