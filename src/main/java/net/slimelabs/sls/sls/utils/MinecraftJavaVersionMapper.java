package net.slimelabs.sls.utils;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class MinecraftJavaVersionMapper {

    // Method to get the required Java version for a given Minecraft version
    public static String getRequiredJavaVersion(String minecraftVersion) {
        if(minecraftVersion.equals("latest")) {
            return "ghcr.io/pterodactyl/yolks:java_21";
        }

        minecraftVersion = removePeriodsAfterFirst(minecraftVersion);
        System.out.println(minecraftVersion);
        // Mapping thresholds where Java requirements change
        NavigableMap<Double, String> versionThresholds = getThresholds();
        try {
            // Convert the input Minecraft version to a double
            double version = Double.parseDouble(minecraftVersion);

            // Find the closest threshold less than or equal to the input version
            Map.Entry<Double, String> entry = versionThresholds.floorEntry(version);

            if (entry != null) {
                return entry.getValue();
            } else {
                return "Unsupported Minecraft version";
            }
        } catch (NumberFormatException e) {
            return "Invalid Minecraft version format";
        }
    }

    private static @NotNull NavigableMap<Double, String> getThresholds() {
        NavigableMap<Double, String> versionThresholds = new TreeMap<>();
        // Populate the thresholds (version, Java version)
        versionThresholds.put(1.20, "sls:java_21");
        versionThresholds.put(1.17, "sls:java_17");
        versionThresholds.put(1.16, "sls:java_16");
        versionThresholds.put(1.13, "sls:java_11");
        versionThresholds.put(1.0, "sls:java_8");
        return versionThresholds;
    }

    public static String removePeriodsAfterFirst(String input) {
        int firstPeriodIndex = input.indexOf(".");

        // If there is no period, return the input as it is
        if (firstPeriodIndex == -1) {
            return input;
        }

        // Split the string at the first period
        String beforeFirstPeriod = input.substring(0, firstPeriodIndex);
        String afterFirstPeriod = input.substring(firstPeriodIndex + 1);

        // Remove all periods in the part after the first period
        afterFirstPeriod = afterFirstPeriod.replace(".", "");

        // Return the combined result
        return beforeFirstPeriod + "." + afterFirstPeriod;
    }
}
