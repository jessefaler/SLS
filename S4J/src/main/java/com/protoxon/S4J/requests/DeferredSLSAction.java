

package com.protoxon.S4J.requests;

import com.protoxon.S4J.entities.S4J;
import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.exceptions.ApiFailure;
import com.protoxon.S4J.exceptions.RateLimitedException;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DeferredSLSAction<T> implements SLSAction<T> {

	private final S4J api;
	private final Supplier<? extends T> value;

	public DeferredSLSAction(S4J api, Supplier<? extends T> value) {
		this.api = api;
		this.value = value;
	}

	@Override
    public S4J getS4J() {
        return api;
    }

	@Override
	public T execute(boolean shouldQueue) throws RateLimitedException {
		return value.get();
	}

	@Override
	public void executeAsync(Consumer<? super T> success, Consumer<? super ApiFailure> failure) {
		CompletableFuture.supplyAsync(value, api.getSupplierPool())
				.thenAcceptAsync(success == null ? SLSAction.getDefaultSuccess() : success);
	}

	@Override
	public SLSAction<T> deadline(long timestamp) {
		return this;
	}
}
