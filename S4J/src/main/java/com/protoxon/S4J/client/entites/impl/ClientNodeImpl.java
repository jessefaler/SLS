package com.protoxon.S4J.client.entites.impl;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.entites.ClientNode;
import com.protoxon.S4J.entites.SystemInformation;
import com.protoxon.S4J.entites.impl.SystemInformationImpl;
import com.protoxon.S4J.requests.Route;
import com.protoxon.S4J.requests.SLSActionImpl;
import org.json.JSONObject;

public class ClientNodeImpl implements ClientNode {

    private final JSONObject json;
    private final SLSClientImpl impl;

    public ClientNodeImpl(JSONObject json, SLSClientImpl impl) {
        this.json = json;
        this.impl = impl;
    }

    @Override
    public String getId() {
        return json.getString("id");
    }

    @Override
    public String getName() {
        return json.getString("name");
    }

    @Override
    public String getLocation() {
        return json.getString("location");
    }

    @Override
    public String getUrl() {
        return json.getString("url");
    }

    @Override
    public SLSAction<SystemInformation> getSystemInformation() {
        return SLSActionImpl.onRequestExecute(
                impl.getS4J(),
                Route.Node.GET_SYSTEM_INFO.compile(getId()),
                (response, request) -> {
                    JSONObject systemObj = response.getObject();
                    return new SystemInformationImpl(systemObj);
                });
    }

}

