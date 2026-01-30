package com.protoxon.S4J.entities.impl;

import com.protoxon.S4J.entities.DockerRunc;
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





