package com.protoxon.S4J.entities.impl;

import com.protoxon.S4J.client.entities.impl.SLSClientImpl;
import com.protoxon.S4J.entities.Mixin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MixinImpl implements Mixin {

    private final JSONObject json;
    private final JSONObject meta;
    private final JSONObject server;
    private final SLSClientImpl impl;

    public MixinImpl(JSONObject json, SLSClientImpl impl) {
        this.json = json;
        this.meta = json.getJSONObject("mixin");
        this.impl = impl;
        this.server = json.optJSONObject("server");
    }

    @Override
    public String getId() {
        return meta.getString("id");
    }

    @Override
    public String getDescription() {
        return meta.optString("description", "");
    }

    @Override
    public List<String> getExtends() {
        JSONArray array = json.optJSONArray("extends");
        if (array == null) {
            return Collections.emptyList();
        }
        List<String> extendsIds = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            extendsIds.add(array.getString(i));
        }
        return extendsIds;
    }

    @Override
    public String getImage() {
        return server != null ? server.optString("image", null) : null;
    }

    @Override
    public String getServerVersion() {
        return server != null ? server.optString("version", null) : null;
    }

    @Override
    public String getServerSoftware() {
        return server != null ? server.optString("software", null) : null;
    }

    @Override
    public Map<String, Object> getAnnotations() {
        JSONObject obj = json.optJSONObject("annotations");
        if (obj == null) {
            return Collections.emptyMap();
        }
        return obj.toMap();
    }

    @Override
    public JSONObject getRawJson() {
        return json;
    }

}
