package com.protoxon.S4J.entites.impl;

import com.protoxon.S4J.entites.DockerStorage;
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

