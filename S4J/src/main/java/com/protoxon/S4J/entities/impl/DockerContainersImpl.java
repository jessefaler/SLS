package com.protoxon.S4J.entities.impl;

import com.protoxon.S4J.entities.DockerContainers;
import org.json.JSONObject;

public class DockerContainersImpl implements DockerContainers {

    private final JSONObject json;

    public DockerContainersImpl(JSONObject json) {
        this.json = json;
    }

    @Override
    public int getTotal() {
        return json.optInt("total", 0);
    }

    @Override
    public int getRunning() {
        return json.optInt("running", 0);
    }

    @Override
    public int getPaused() {
        return json.optInt("paused", 0);
    }

    @Override
    public int getStopped() {
        return json.optInt("stopped", 0);
    }

}





