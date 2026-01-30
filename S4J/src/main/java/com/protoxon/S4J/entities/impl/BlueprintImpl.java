package com.protoxon.S4J.entities.impl;

import com.protoxon.S4J.client.entities.impl.SLSClientImpl;
import com.protoxon.S4J.entities.Blueprint;
import org.json.JSONObject;

import java.util.Map;

public class BlueprintImpl implements Blueprint {

    private final JSONObject json;
    private final JSONObject meta;
    private final SLSClientImpl impl;

    public BlueprintImpl(JSONObject json, SLSClientImpl impl) {
        this.json = json;
        this.meta = json.getJSONObject("metadata");
        this.impl = impl;
    }

    @Override
    public String getId() {
        return meta.getString("id");
    }

    @Override
    public String getName() {
        return meta.optString("name");
    }

    @Override
    public String getServerVersion() {
        JSONObject server = json.optJSONObject("server");
        return server != null ? server.optString("version", null) : null;
    }

    @Override
    public String getServerSoftware() {
        JSONObject server = json.optJSONObject("server");
        return server != null ? server.optString("software", null) : null;
    }

    @Override
    public String getType() {
        return meta.optString("type");
    }

    @Override
    public Map<String, Object> getAnnotations() {
        JSONObject obj = json.optJSONObject("annotations");
        return obj.toMap();
    }

    @Override
    public JSONObject getRawJson() {
        return json;
    }

}
