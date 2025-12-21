package com.protoxon.S4J.entites.impl;

import com.protoxon.S4J.entites.SystemInformation;
import org.json.JSONObject;

public class SystemInformationImpl implements SystemInformation {

    private final JSONObject json;
    private final JSONObject system;

    public SystemInformationImpl(JSONObject json) {
        this.json = json;
        this.system = json.getJSONObject("system");
    }

    @Override
    public String getVersion() {
        return json.optString("version");
    }

    @Override
    public String getArchitecture() {
        return system.optString("architecture");
    }

    @Override
    public int getCpuThreads() {
        return system.optInt("cpu_threads", 0);
    }

    @Override
    public long getMemoryBytes() {
        return system.optLong("memory_bytes", -1);
    }

    @Override
    public String getKernelVersion() {
        return system.optString("kernel_version");
    }

    @Override
    public String getOs() {
        return system.optString("os");
    }

    @Override
    public String getOsType() {
        return system.optString("os_type");
    }

    @Override
    public JSONObject getRawJson() {
        return json;
    }
}
