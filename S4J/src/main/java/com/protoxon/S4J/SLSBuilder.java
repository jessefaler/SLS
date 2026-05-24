package com.protoxon.S4J;

import com.protoxon.S4J.client.entities.SLSClient;
import com.protoxon.S4J.entities.S4J;
import com.protoxon.S4J.entities.impl.S4JImpl;
import com.protoxon.S4J.utils.config.EndpointConfig;
import com.protoxon.S4J.utils.config.SessionConfig;
import com.protoxon.S4J.utils.config.ThreadingConfig;
import okhttp3.OkHttpClient;

import java.util.concurrent.*;

public class SLSBuilder {

    private String url;
    private String token;

    private ExecutorService actionPool = null;
    private ExecutorService callbackPool = null;
    private ScheduledExecutorService rateLimitPool = null;
    private ExecutorService supplierPool = null;

    private OkHttpClient httpClient = null;

    private SLSBuilder(String url, String token) {
        this.url = url;
        this.token = token;
    }

    public static SLSClient createClient(String url, String token) {
        return new SLSBuilder(url, token).build().asClient();
    }

    public S4J build() {
        EndpointConfig endpointConfig = new EndpointConfig(url, token);
        ThreadingConfig threadingConfig = new ThreadingConfig();
        threadingConfig.setCallbackPool(callbackPool);
        threadingConfig.setActionPool(actionPool);
        threadingConfig.setRateLimitPool(rateLimitPool);
        threadingConfig.setSupplierPool(supplierPool);
        SessionConfig sessionConfig = new SessionConfig(httpClient);
        return new S4JImpl(endpointConfig, threadingConfig, sessionConfig);
    }

    /**
     * Sets the API key that will be used when S4J makes a Request
     *
     * @param  token
     *         The API key for the user or application
     *
     * @return The SLSBuilder instance. Useful for chaining.
     */
    public SLSBuilder setToken(String token) {
        this.token = token;
        return this;
    }

    /**
     * Sets the {@link okhttp3.OkHttpClient OkHttpClient} that will be used by S4Js requester.
     *
     * <br>This can be used to set things such as connection timeout and proxy.
     *
     * @param  client
     *         The new {@link okhttp3.OkHttpClient OkHttpClient} to use
     *
     * @return The SLSBuilder instance. Useful for chaining.
     */
    public SLSBuilder setHttpClient(OkHttpClient client) {
        this.httpClient = client;
        return this;
    }

    /**
     * Sets the {@link ExecutorService ExecutorService} that should be used in the S4J request handler.
     *
     * <br><b>Only change this pool if you know what you're doing.</b>
     *
     * <p>This is used to queue the request and finalize its request body for {@link SLSAction#executeAsync()} tasks.
     *
     * <p>Default: {@link ThreadPoolExecutor} with 1 thread.
     *
     * @param  pool
     *         The thread pool to use for action handling
     *
     * @return The SLSBuilder instance. Useful for chaining.
     */
    public SLSBuilder setActionPool(ExecutorService pool) {
        this.actionPool = pool;
        return this;
    }

    /**
     * Sets the {@link ExecutorService ExecutorService} that should be used in
     * the S4J callback handler which consists of {@link com.protoxon.S4J.SLSAction SLSAction} callbacks.
     * <br><b>Only change this pool if you know what you're doing.</b>
     *
     * <p>This is used to handle callbacks of {@link SLSAction#executeAsync()}, similarly it is used to
     * finish {@link SLSAction#execute()} tasks which build on queue.
     *
     * <p>Default: {@link ForkJoinPool#commonPool()}
     *
     * @param  pool
     *         The thread pool to use for callback handling
     *
     * @return The SLSBuilder instance. Useful for chaining.
     */
    public SLSBuilder setCallbackPool(ExecutorService pool) {
        this.callbackPool = pool;
        return this;
    }

    /**
     * Sets the {@link ScheduledExecutorService ScheduledExecutorService} that should be used in
     * the S4J rate limiter. Changing this can affect the S4J behavior for SLSAction execution
     * and should be handled carefully.
     *
     * <br><b>Only change this pool if you know what you're doing.</b>
     *
     * <p>This is used by the rate limiter to handle backoff delays by using scheduled executions.
     *
     * <p>Default: {@link ScheduledThreadPoolExecutor} with 5 threads.
     *
     * @param  pool
     *         The thread pool to use for rate limiting
     *
     * @return The SLSBuilder instance. Useful for chaining.
     */
    public SLSBuilder setRateLimitPool(ScheduledExecutorService pool) {
        this.rateLimitPool = pool;
        return this;
    }

    /**
     * Sets the {@link ExecutorService ExecutorService} that should be used in
     * the S4J Action CompletableFutures.
     *
     * <br><b>Only change this pool if you know what you're doing.</b>
     *
     * <p>This is used to execute Suppliers mainly used by SLSActions that aren't requests.
     *
     * <p>Default: {@link ThreadPoolExecutor} with 3 threads.
     *
     * @param  pool
     *         The thread pool to use for CompletableFutures
     *
     * @return The SLSBuilder instance. Useful for chaining.
     */
    public SLSBuilder setSupplierPool(ExecutorService pool) {
        this.supplierPool = pool;
        return this;
    }

    /**
     * The API key that is currently being used for S4J authentication.
     *
     * @return The API key
     */
    public String getToken() {
        return this.token;
    }

    /**
     * The URL of the master node that is currently being used with S4J.
     *
     * @return The master node URL
     */
    public String getUrl() {
        return this.url;
    }

}
