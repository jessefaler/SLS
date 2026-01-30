package com.protoxon.S4J.entities.impl;

import com.protoxon.S4J.entities.DockerCgroups;
import com.protoxon.S4J.entities.DockerContainers;
import com.protoxon.S4J.entities.DockerInformation;
import com.protoxon.S4J.entities.DockerRunc;
import com.protoxon.S4J.entities.DockerStorage;
import org.json.JSONObject;

public class DockerInformationImpl implements DockerInformation {

    private final JSONObject json;
    private final DockerCgroups cgroups;
    private final DockerContainers containers;
    private final DockerStorage storage;
    private final DockerRunc runc;

    public DockerInformationImpl(JSONObject json) {
        this.json = json;
        
        JSONObject cgroupsJson = json.optJSONObject("cgroups");
        this.cgroups = cgroupsJson != null ? new DockerCgroupsImpl(cgroupsJson) : null;
        
        JSONObject containersJson = json.optJSONObject("containers");
        this.containers = containersJson != null ? new DockerContainersImpl(containersJson) : null;
        
        JSONObject storageJson = json.optJSONObject("storage");
        this.storage = storageJson != null ? new DockerStorageImpl(storageJson) : null;
        
        JSONObject runcJson = json.optJSONObject("runc");
        this.runc = runcJson != null ? new DockerRuncImpl(runcJson) : null;
    }

    @Override
    public String getVersion() {
        return json.optString("version");
    }

    @Override
    public DockerCgroups getCgroups() {
        return cgroups;
    }

    @Override
    public DockerContainers getContainers() {
        return containers;
    }

    @Override
    public DockerStorage getStorage() {
        return storage;
    }

    @Override
    public DockerRunc getRunc() {
        return runc;
    }

}





