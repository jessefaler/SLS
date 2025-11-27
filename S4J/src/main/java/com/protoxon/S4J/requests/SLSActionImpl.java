package com.protoxon.S4J.requests;


import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.entites.S4J;
import com.protoxon.S4J.exceptions.SLSException;
import com.protoxon.S4J.requests.Request;
import com.protoxon.S4J.requests.Response;
import com.protoxon.S4J.requests.Route;
import com.protoxon.S4J.utils.S4JLogger;
import okhttp3.RequestBody;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

public class SLSActionImpl<T> implements SLSAction<T> {

    public static final Logger LOGGER = S4JLogger.getLogger(SLSAction.class);

    private final S4J api;
    private final Route.CompiledRoute route;
    private final RequestBody data;
    private long deadline = 0;
    private final BiFunction<Response, Request<T>, T> handler;

    public SLSActionImpl(S4J api) {
        this(api, null);
    }

    public static <T> DeferredSLSAction<T> onExecute(S4J api, Supplier<? extends T> supplier) {
        return new DeferredSLSAction<>(api, supplier);
    }

    public static <T> SLSActionImpl<T> onRequestExecute(S4J api, Route.CompiledRoute route) {
        return new SLSActionImpl<>(api, route);
    }

    public static <T> SLSActionImpl<T> onRequestExecute(S4J api, Route.CompiledRoute route, RequestBody data) {
        return new SLSActionImpl<>(api, route, data);
    }

    public static <T> SLSActionImpl<T> onRequestExecute(
            S4J api, Route.CompiledRoute route, BiFunction<Response, Request<T>, T> handler) {
        return new SLSActionImpl<>(api, route, handler);
    }

    public static <T> SLSActionImpl<T> onRequestExecute(
            S4J api, Route.CompiledRoute route, RequestBody data, BiFunction<Response, Request<T>, T> handler) {
        return new SLSActionImpl<>(api, route, data, handler);
    }

    public SLSActionImpl(S4J api, Route.CompiledRoute route) {
        this(api, route, null, null);
    }

    public SLSActionImpl(S4J api, Route.CompiledRoute route, RequestBody data) {
        this(api, route, data, null);
    }

    public SLSActionImpl(S4J api, Route.CompiledRoute route, BiFunction<Response, Request<T>, T> handler) {
        this(api, route, null, handler);
    }

    public SLSActionImpl(
            S4J api, Route.CompiledRoute route, RequestBody data, BiFunction<Response, Request<T>, T> handler) {
        this.api = api;
        this.route = route;
        this.data = data;
        this.handler = handler;
    }

    public static final Consumer<Object> DEFAULT_SUCCESS = o -> {};
    public static final Consumer<? super Throwable> DEFAULT_FAILURE =
            t -> System.err.printf("Action execute returned failure: %s%n", t.getMessage());

    @Override
    public T execute(boolean shouldQueue) {
        Route.CompiledRoute route = finalizeRoute();
        RequestBody data = finalizeData();
        try {
            return new RequestFuture<>(this, route, data, shouldQueue, deadline).join();
        } catch (CompletionException ex) {
            if (ex.getCause() != null) {
                Throwable cause = ex.getCause();
                if (cause instanceof SLSException) throw (SLSException) cause.fillInStackTrace();
            }
            throw ex;
        }
    }

    @Override
    public void executeAsync(Consumer<? super T> success, Consumer<? super Throwable> failure) {
        Route.CompiledRoute route = finalizeRoute();
        if (success == null) success = DEFAULT_SUCCESS;
        if (failure == null) failure = DEFAULT_FAILURE;

        Consumer<? super T> finalizedSuccess = success;
        Consumer<? super Throwable> finalizedFailure = failure;

        api.getActionPool().submit(() -> {
            RequestBody data = finalizeData();
            api.getRequester()
                    .request(new Request<>(this, finalizedSuccess, finalizedFailure, route, data, true, deadline));
        });
    }

    @Override
    public SLSAction<T> deadline(long timestamp) {
        this.deadline = timestamp;
        return this;
    }

    @Override
    public S4J getS4J() {
        return api;
    }

    public void handleResponse(Response response, Request<T> request) {
        if (response.isOk()) handleSuccess(response, request);
        else request.setOnFailure(response);
    }

    public void handleSuccess(Response response, Request<T> request) {
        if (response.isEmpty() || handler == null) request.onSuccess(null);
        else request.onSuccess(handler.apply(response, request));
    }

    protected RequestBody finalizeData() {
        return data;
    }

    protected Route.CompiledRoute finalizeRoute() {
        return route;
    }

    public static RequestBody getRequestBody(JSONObject object) {
        return object == null ? null : RequestBody.create(Requester.MEDIA_TYPE_JSON, object.toString());
    }

    public static RequestBody getRequestBody(JSONArray array) {
        return array == null ? null : RequestBody.create(Requester.MEDIA_TYPE_JSON, array.toString());
    }
}
