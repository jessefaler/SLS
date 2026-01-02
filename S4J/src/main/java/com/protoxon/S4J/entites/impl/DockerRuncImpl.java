package com.protoxon.S4J.entites.impl;

import com.protoxon.S4J.entites.DockerRunc;
import org.json.JSONObject;

public class DockerRuncImpl implements DockerRunc {

    private final JSONObject json;

    public DockerRuncImpl(JSONObject json) {
        this.json = json;
    }

    @Override
    public String getVersion() {
        return json.optString("version");
    }

}

