package com.protoxon.S4J.client.actions;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.entites.ClientServer;

public interface ServerCreationAction extends SLSAction<ClientServer> {

    ServerCreationAction setBlueprintId(String id);



}
