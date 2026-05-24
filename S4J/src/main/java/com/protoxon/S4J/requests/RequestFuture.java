

package com.protoxon.S4J.requests;

import java.util.concurrent.CompletableFuture;

import com.protoxon.S4J.exceptions.ApiError;

import okhttp3.RequestBody;

public class RequestFuture<T> extends CompletableFuture<T> {

	private final Request<T> request;

	public RequestFuture(
			SLSActionImpl<T> action,
			Route.CompiledRoute route,
			RequestBody requestBody,
			boolean shouldQueue,
			long deadline) {
		this.request =
				new Request<>(
						action,
						this::complete,
						f -> completeExceptionally((ApiError) f),
						route,
						requestBody,
						shouldQueue,
						deadline);
		action.getS4J().getRequester().request(this.request);
	}

	@Override
	public boolean cancel(final boolean mayInterrupt) {
		if (this.request != null) this.request.cancel();

		return (!isDone() && !isCancelled()) && super.cancel(mayInterrupt);
	}
}





