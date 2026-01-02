package com.protoxon.S4J.entites.impl;

import com.protoxon.S4J.entites.DockerCgroups;
import org.json.JSONObject;

public class DockerCgroupsImpl implements DockerCgroups {

    private final JSONObject json;

    public DockerCgroupsImpl(JSONObject json) {
        this.json = json;
    }

    @Override
    public String getDriver() {
        return json.optString("driver");
    }

    @Override
    public String getVersion() {
        return json.optString("version");
    }

}

