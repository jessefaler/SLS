package com.protoxon.S4J;

import org.json.JSONObject;

/**
 * Represents the resource usage statistics for a server instance.
 */
public class ServerStats {
    private final long memoryBytes;
    private final long memoryLimitBytes;
    private final double cpuAbsolute;
    private final NetworkStats network;
    private final long uptime;

    /**
     * Creates a new ServerStats instance.
     *
     * @param memoryBytes the total amount of memory, in bytes, that this server instance is consuming
     * @param memoryLimitBytes the total amount of memory this container or resource can use
     * @param cpuAbsolute the absolute CPU usage in relation to the entire system
     * @param network the current network transmit in & out for a container
     * @param uptime the current uptime of the container, in milliseconds
     */
    public ServerStats(long memoryBytes, long memoryLimitBytes, double cpuAbsolute, NetworkStats network, long uptime) {
        this.memoryBytes = memoryBytes;
        this.memoryLimitBytes = memoryLimitBytes;
        this.cpuAbsolute = cpuAbsolute;
        this.network = network;
        this.uptime = uptime;
    }

    /**
     * Parses a JSONObject into a ServerStats instance.
     *
     * @param json the JSON object containing the stats data
     * @return a new ServerStats instance parsed from the JSON
     */
    public static ServerStats fromJSON(JSONObject json) {
        long memoryBytes = json.optLong("memory_bytes", 0);
        long memoryLimitBytes = json.optLong("memory_limit_bytes", 0);
        double cpuAbsolute = json.optDouble("cpu_absolute", 0.0);
        long uptime = json.optLong("uptime", 0);

        NetworkStats network = null;
        if (json.has("network") && !json.isNull("network")) {
            JSONObject networkJson = json.getJSONObject("network");
            network = NetworkStats.fromJSON(networkJson);
        } else {
            network = new NetworkStats(0, 0);
        }

        return new ServerStats(memoryBytes, memoryLimitBytes, cpuAbsolute, network, uptime);
    }

    /**
     * @return the total amount of memory, in bytes, that this server instance is consuming
     */
    public long getMemoryBytes() {
        return memoryBytes;
    }

    /**
     * @return the total amount of memory this container or resource can use
     */
    public long getMemoryLimitBytes() {
        return memoryLimitBytes;
    }

    /**
     * @return the absolute CPU usage in relation to the entire system
     */
    public double getCpuAbsolute() {
        return cpuAbsolute;
    }

    /**
     * @return the current network transmit in & out for a container
     */
    public NetworkStats getNetwork() {
        return network;
    }

    /**
     * @return the current uptime of the container, in milliseconds
     */
    public long getUptime() {
        return uptime;
    }

    /**
     * Formats the memory usage in the specified data type.
     *
     * @param dataType the data type to format the memory in
     * @return a formatted string representing the memory usage
     */
    public String getMemoryFormatted(DataType dataType) {
        return formatBytes(memoryBytes, dataType);
    }

    /**
     * Formats the memory limit in the specified data type.
     *
     * @param dataType the data type to format the memory limit in
     * @return a formatted string representing the memory limit
     */
    public String getMaxMemoryFormatted(DataType dataType) {
        return formatBytes(memoryLimitBytes, dataType);
    }

    /**
     * Automatically formats the memory usage in the most appropriate unit (KB, MB, GB, or TB)
     * based on the size, similar to Docker's format (e.g., "612.2 MB" or "4.4 GB").
     *
     * @return a formatted string representing the memory usage with automatically selected unit
     */
    public String getMemoryFormattedAuto() {
        return formatBytesAuto(memoryBytes);
    }

    /**
     * Automatically formats the memory limit in the most appropriate unit (KB, MB, GB, or TB)
     * based on the size, similar to Docker's format (e.g., "612.2 MB" or "4.4 GB").
     *
     * @return a formatted string representing the memory limit with automatically selected unit
     */
    public String getMaxMemoryFormattedAuto() {
        return formatBytesAuto(memoryLimitBytes);
    }

    /**
     * Calculates and returns the memory usage as a percentage.
     *
     * @return the memory usage percentage (0.0 to 100.0), or 0.0 if memory limit is 0
     */
    public double getMemoryUsagePercentage() {
        if (memoryLimitBytes == 0) {
            return 0.0;
        }
        return (double) memoryBytes / memoryLimitBytes * 100.0;
    }

    /**
     * Formats the memory usage as a percentage string.
     *
     * @return a formatted string representing the memory usage as a percentage (e.g., "45.67%")
     */
    public String getMemoryUsagePercentageFormatted() {
        return String.format("%.2f", getMemoryUsagePercentage()) + "%";
    }

    /**
     * Formats the CPU usage as a percentage.
     *
     * @return a formatted string representing the CPU usage as a percentage
     */
    public String getCpuFormatted() {
        return String.format("%.2f", cpuAbsolute) + "%";
    }

    /**
     * Formats the uptime from milliseconds into a human-readable format (e.g., "1h 30m 15s").
     *
     * @return a formatted string representing the uptime
     */
    public String formatUptime() {
        return formatUptime(uptime);
    }

    /**
     * Formats the network ingress (received bytes) in the specified data type.
     *
     * @param dataType the data type to format the network ingress in
     * @return a formatted string representing the network ingress
     */
    public String getNetworkIngressFormatted(DataType dataType) {
        return network.getRxBytesFormatted(dataType);
    }

    /**
     * Formats the network egress (transmitted bytes) in the specified data type.
     *
     * @param dataType the data type to format the network egress in
     * @return a formatted string representing the network egress
     */
    public String getNetworkEgressFormatted(DataType dataType) {
        return network.getTxBytesFormatted(dataType);
    }

    /**
     * Automatically formats the network ingress (received bytes) in the most appropriate unit
     * (KB, MB, GB, or TB) based on the size, similar to Docker's format.
     *
     * @return a formatted string representing the network ingress with automatically selected unit
     */
    public String getNetworkIngressFormattedAuto() {
        return network.getRxBytesFormattedAuto();
    }

    /**
     * Automatically formats the network egress (transmitted bytes) in the most appropriate unit
     * (KB, MB, GB, or TB) based on the size, similar to Docker's format.
     *
     * @return a formatted string representing the network egress with automatically selected unit
     */
    public String getNetworkEgressFormattedAuto() {
        return network.getTxBytesFormattedAuto();
    }

    /**
     * Formats bytes into the specified data type with appropriate decimal precision.
     *
     * @param bytes the number of bytes to format
     * @param dataType the data type to format the bytes in
     * @return a formatted string representing the bytes in the specified unit
     */
    static String formatBytes(long bytes, DataType dataType) {
        if (dataType == null) {
            return String.valueOf(bytes) + " B";
        }

        double value = (double) bytes / dataType.getByteValue();
        String abbreviation = dataType.name();
        
        // Determine decimal places based on the value
        if (value >= 1000) {
            return String.format("%.1f %s", value, abbreviation);
        } else if (value >= 100) {
            return String.format("%.2f %s", value, abbreviation);
        } else {
            return String.format("%.2f %s", value, abbreviation);
        }
    }

    /**
     * Automatically formats bytes into the most appropriate unit (KB, MB, GB, or TB)
     * based on the size.
     *
     * @param bytes the number of bytes to format
     * @return a formatted string representing the bytes with automatically selected unit
     */
    static String formatBytesAuto(long bytes) {
        if (bytes < 0) {
            return "0 B";
        }

        // Determine the most appropriate unit
        DataType dataType;
        if (bytes >= DataType.TB.getByteValue()) {
            dataType = DataType.TB;
        } else if (bytes >= DataType.GB.getByteValue()) {
            dataType = DataType.GB;
        } else if (bytes >= DataType.MB.getByteValue()) {
            dataType = DataType.MB;
        } else if (bytes >= DataType.KB.getByteValue()) {
            dataType = DataType.KB;
        } else {
            return bytes + " B";
        }

        double value = (double) bytes / dataType.getByteValue();
        String abbreviation = dataType.name();
        
        // Format with appropriate decimal precision (similar to Docker: 612.2MiB, 4.4GiB)
        if (value >= 100) {
            return String.format("%.1f %s", value, abbreviation);
        } else {
            return String.format("%.2f %s", value, abbreviation);
        }
    }

    /**
     * Formats uptime from milliseconds into a human-readable format.
     * Only shows days, hours, and minutes if they are greater than 0.
     * Always shows seconds.
     *
     * @param uptimeMs the uptime in milliseconds
     * @return a formatted string representing the uptime (e.g., "1h 30m 15s" or "45s")
     */
    private static String formatUptime(long uptimeMs) {
        if (uptimeMs < 0) {
            return "0s";
        }

        long totalSeconds = uptimeMs / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    /**
     * Represents network statistics for a server instance.
     */
    public static class NetworkStats {
        private final long rxBytes;
        private final long txBytes;

        /**
         * Creates a new NetworkStats instance.
         *
         * @param rxBytes the number of bytes received
         * @param txBytes the number of bytes transmitted
         */
        public NetworkStats(long rxBytes, long txBytes) {
            this.rxBytes = rxBytes;
            this.txBytes = txBytes;
        }

        /**
         * Parses a JSONObject into a NetworkStats instance.
         *
         * @param json the JSON object containing the network stats data
         * @return a new NetworkStats instance parsed from the JSON
         */
        public static NetworkStats fromJSON(JSONObject json) {
            long rxBytes = json.optLong("rx_bytes", 0);
            long txBytes = json.optLong("tx_bytes", 0);
            return new NetworkStats(rxBytes, txBytes);
        }

        /**
         * @return the number of bytes received
         */
        public long getRxBytes() {
            return rxBytes;
        }

        /**
         * @return the number of bytes transmitted
         */
        public long getTxBytes() {
            return txBytes;
        }

        /**
         * Formats the received bytes in the specified data type.
         *
         * @param dataType the data type to format the received bytes in
         * @return a formatted string representing the received bytes
         */
        public String getRxBytesFormatted(DataType dataType) {
            return ServerStats.formatBytes(rxBytes, dataType);
        }

        /**
         * Formats the transmitted bytes in the specified data type.
         *
         * @param dataType the data type to format the transmitted bytes in
         * @return a formatted string representing the transmitted bytes
         */
        public String getTxBytesFormatted(DataType dataType) {
            return ServerStats.formatBytes(txBytes, dataType);
        }

        /**
         * Automatically formats the received bytes in the most appropriate unit (KB, MB, GB, or TB)
         * based on the size, similar to Docker's format.
         *
         * @return a formatted string representing the received bytes with automatically selected unit
         */
        public String getRxBytesFormattedAuto() {
            return ServerStats.formatBytesAuto(rxBytes);
        }

        /**
         * Automatically formats the transmitted bytes in the most appropriate unit (KB, MB, GB, or TB)
         * based on the size, similar to Docker's format.
         *
         * @return a formatted string representing the transmitted bytes with automatically selected unit
         */
        public String getTxBytesFormattedAuto() {
            return ServerStats.formatBytesAuto(txBytes);
        }
    }
}

