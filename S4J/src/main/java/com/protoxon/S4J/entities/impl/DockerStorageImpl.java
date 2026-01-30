package com.protoxon.S4J.entities.impl;

import com.protoxon.S4J.entities.DockerStorage;
import org.json.JSONObject;

public class DockerStorageImpl implements DockerStorage {

    private final JSONObject json;

    public DockerStorageImpl(JSONObject json) {
        this.json = json;
    }

    @Override
    public String getDriver() {
        return json.optString("driver");
    }

    @Override
    public String getFilesystem() {
        return json.optString("filesystem");
    }

}





