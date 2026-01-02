package com.protoxon.S4J.client.entites.impl;

import com.protoxon.S4J.client.actions.ServerCreationAction;
import com.protoxon.S4J.client.entites.ClientServer;
import com.protoxon.S4J.requests.Route;
import com.protoxon.S4J.requests.SLSActionImpl;
import okhttp3.RequestBody;
import org.json.JSONObject;

public class CreateServerImpl extends SLSActionImpl<ClientServer> implements ServerCreationAction {

    private String blueprintId;
    private String nodeId;

    private SLSClientImpl impl;

    public CreateServerImpl(SLSClientImpl impl) {

        super(
                impl.getS4J(),
                Route.Servers.CREATE_SERVER.compile(),
                (response, request) -> new ClientServerImpl(response.getObject(), impl)
        );

        this.impl = impl;
    }

    @Override
    public ServerCreationAction setBlueprintId(String blueprintId) {
        this.blueprintId = blueprintId;
        return this;
    }

    @Override
    public ServerCreationAction setNodeId(String nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    @Override
    protected RequestBody finalizeData() {
        JSONObject obj = new JSONObject()
                .put("blueprint_id", blueprintId);
        if (nodeId != null) {
            obj.put("node_id", nodeId);
        }
        return getRequestBody(obj);
    }

}