package com.protoxon.S4J.entities.impl;

import com.protoxon.S4J.client.entities.SLSClient;
import com.protoxon.S4J.client.entities.impl.SLSClientImpl;
import com.protoxon.S4J.entities.S4J;
import com.protoxon.S4J.requests.Requester;
import com.protoxon.S4J.utils.config.EndpointConfig;
import com.protoxon.S4J.utils.config.SessionConfig;
import com.protoxon.S4J.utils.config.ThreadingConfig;
import okhttp3.OkHttpClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public class S4JImpl implements S4J {

    private final Requester requester;

    private final EndpointConfig endpointConfig;
    private final ThreadingConfig threadingConfig;
    private final SessionConfig sessionConfig;

    public S4JImpl(EndpointConfig endpointConfig, ThreadingConfig threadingConfig, SessionConfig sessionConfig) {
        this.endpointConfig = endpointConfig;
        this.threadingConfig = threadingConfig;
        this.sessionConfig = sessionConfig;
        this.requester = new Requester(this);
    }

    @Override
    public String getToken() {
        return endpointConfig.token();
    }

    @Override
    public Requester getRequester() {
        return requester;
    }

    @Override
    public String getUrl() {
        return endpointConfig.url();
    }

    @Override
    public OkHttpClient getHttpClient() {
        return sessionConfig.getHttpClient();
    }

    @Override
    public ExecutorService getCallbackPool() {
        return threadingConfig.getCallbackPool();
    }

    @Override
    public ExecutorService getActionPool() {
        return threadingConfig.getActionPool();
    }

    @Override
    public ScheduledExecutorService getRateLimitPool() {
        return threadingConfig.getRateLimitPool();
    }

    @Override
    public ExecutorService getSupplierPool() {
        return threadingConfig.getSupplierPool();
    }

    @Override
    public SLSClient asClient() {
        return new SLSClientImpl(this);
    }
}
