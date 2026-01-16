package com.protoxon.S4J.client.entites.impl;

import com.protoxon.S4J.client.actions.ServerCreationAction;
import com.protoxon.S4J.client.entites.ClientServer;
import com.protoxon.S4J.client.entites.ServerLimits;
import com.protoxon.S4J.requests.Route;
import com.protoxon.S4J.requests.SLSActionImpl;
import okhttp3.RequestBody;
import org.json.JSONObject;

public class CreateServerImpl extends SLSActionImpl<ClientServer> implements ServerCreationAction {

    private String blueprintId;
    private String nodeId;
    private Boolean save;
    private ServerLimits limits;

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
    public String getBlueprintId() {
        return this.blueprintId != null ? this.blueprintId : "";
    }

    @Override
    public String getNodeId() {
        return this.nodeId != null ? this.nodeId : "";
    }

    @Override
    public ServerCreationAction setNodeId(String nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    @Override
    public ServerCreationAction setSave(Boolean save) {
        this.save = save;
        return this;
    }

    @Override
    public ServerCreationAction setMemoryLimit(Long memoryLimit) {
        if (limits == null) {
            limits = new ServerLimits();
        }
        limits.setMemoryLimit(memoryLimit);
        return this;
    }

    @Override
    public ServerCreationAction setSwap(Long swap) {
        if (limits == null) {
            limits = new ServerLimits();
        }
        limits.setSwap(swap);
        return this;
    }

    @Override
    public ServerCreationAction setIoWeight(Integer ioWeight) {
        if (limits == null) {
            limits = new ServerLimits();
        }
        limits.setIoWeight(ioWeight);
        return this;
    }

    @Override
    public ServerCreationAction setCpuLimit(Long cpuLimit) {
        if (limits == null) {
            limits = new ServerLimits();
        }
        limits.setCpuLimit(cpuLimit);
        return this;
    }

    @Override
    public ServerCreationAction setDiskSpace(Long diskSpace) {
        if (limits == null) {
            limits = new ServerLimits();
        }
        limits.setDiskSpace(diskSpace);
        return this;
    }

    @Override
    public ServerCreationAction setThreads(String threads) {
        if (limits == null) {
            limits = new ServerLimits();
        }
        limits.setThreads(threads);
        return this;
    }

    @Override
    public ServerCreationAction setOomDisabled(Boolean oomDisabled) {
        if (limits == null) {
            limits = new ServerLimits();
        }
        limits.setOomDisabled(oomDisabled);
        return this;
    }

    @Override
    public ServerCreationAction setLimits(ServerLimits limits) {
        this.limits = limits;
        return this;
    }

    @Override
    protected RequestBody finalizeData() {
        JSONObject obj = new JSONObject()
                .put("blueprint_id", blueprintId);
        if (nodeId != null) {
            obj.put("node_id", nodeId);
        }

        // Build overrides object if any override is set
        JSONObject overrides = null;
        if (save != null || limits != null) {
            overrides = new JSONObject();
            if (save != null) {
                overrides.put("save", save);
            }
            if (limits != null) {
                JSONObject limitsObj = new JSONObject();
                if (limits.getMemoryLimit() != null) {
                    limitsObj.put("memory_limit", limits.getMemoryLimit());
                }
                if (limits.getSwap() != null) {
                    limitsObj.put("swap", limits.getSwap());
                }
                if (limits.getIoWeight() != null) {
                    limitsObj.put("io_weight", limits.getIoWeight());
                }
                if (limits.getCpuLimit() != null) {
                    limitsObj.put("cpu_limit", limits.getCpuLimit());
                }
                if (limits.getDiskSpace() != null) {
                    limitsObj.put("disk_space", limits.getDiskSpace());
                }
                if (limits.getThreads() != null) {
                    limitsObj.put("threads", limits.getThreads());
                }
                if (limits.getOomDisabled() != null) {
                    limitsObj.put("oom_disabled", limits.getOomDisabled());
                }
                if (limitsObj.length() > 0) {
                    overrides.put("limits", limitsObj);
                }
            }
            if (overrides.length() > 0) {
                obj.put("overrides", overrides);
            }
        }

        return getRequestBody(obj);
    }

}