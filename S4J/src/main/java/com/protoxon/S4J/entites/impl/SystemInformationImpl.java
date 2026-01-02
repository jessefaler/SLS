package com.protoxon.S4J.entites.impl;

import com.protoxon.S4J.DataType;
import com.protoxon.S4J.entites.DockerInformation;
import com.protoxon.S4J.entites.SystemInformation;
import org.json.JSONObject;

public class SystemInformationImpl implements SystemInformation {

    private final JSONObject json;
    private final JSONObject system;
    private final DockerInformation docker;

    public SystemInformationImpl(JSONObject json) {
        this.json = json;
        this.system = json.optJSONObject("system");
        JSONObject dockerJson = json.optJSONObject("docker");
        this.docker = dockerJson != null ? new DockerInformationImpl(dockerJson) : null;
    }

    @Override
    public String getVersion() {
        return json.optString("version");
    }

    @Override
    public String getArchitecture() {
        return system != null ? system.optString("architecture") : null;
    }

    @Override
    public int getCpuThreads() {
        return system != null ? system.optInt("cpu_threads", 0) : 0;
    }

    @Override
    public long getMemoryBytes() {
        return system != null ? system.optLong("memory_bytes", -1) : -1;
    }

    @Override
    public String getMemoryFormatted(DataType dataType) {
        return formatBytes(getMemoryBytes(), dataType);
    }

    @Override
    public String getMemoryFormattedAuto() {
        return formatBytesAuto(getMemoryBytes());
    }

    @Override
    public String getKernelVersion() {
        return system != null ? system.optString("kernel_version") : null;
    }

    @Override
    public String getOs() {
        return system != null ? system.optString("os") : null;
    }

    @Override
    public String getOsType() {
        return system != null ? system.optString("os_type") : null;
    }

    @Override
    public DockerInformation getDocker() {
        return docker;
    }

    @Override
    public JSONObject getRawJson() {
        return json;
    }

    /**
     * Formats bytes into the specified data type with appropriate decimal precision.
     *
     * @param bytes the number of bytes to format
     * @param dataType the data type to format the bytes in
     * @return a formatted string representing the bytes in the specified unit
     */
    private static String formatBytes(long bytes, DataType dataType) {
        if (bytes < 0) {
            return "Unknown";
        }

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
    private static String formatBytesAuto(long bytes) {
        if (bytes < 0) {
            return "Unknown";
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
}
