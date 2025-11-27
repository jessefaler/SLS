package com.protoxon.S4J.entites;

import com.protoxon.S4J.client.entites.SLSClient;
import com.protoxon.S4J.requests.Requester;
import okhttp3.OkHttpClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public interface S4J {

    String getToken();

    Requester getRequester();

    OkHttpClient getHttpClient();

    String getUrl();

    ExecutorService getCallbackPool();

    ExecutorService getActionPool();

    ScheduledExecutorService getRateLimitPool();

    ExecutorService getSupplierPool();

    SLSClient asClient();
}
