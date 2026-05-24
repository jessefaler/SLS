

package com.protoxon.S4J.requests;

import com.protoxon.S4J.entities.S4J;
import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.exceptions.ApiError;
import com.protoxon.S4J.exceptions.ApiFailure;

import java.util.function.Consumer;

public class CompletedSLSAction<T> implements SLSAction<T> {

	private final S4J api;
	protected final T value;
	protected final Throwable error;

	public CompletedSLSAction(S4J api, T value, Throwable error) {
		this.api = api;
		this.value = value;
		this.error = error;
	}

	public CompletedSLSAction(S4J api, T value) {
		this(api, value, null);
	}

	public CompletedSLSAction(S4J api, Throwable error) {
		this(api, null, error);
	}

	@Override
	public S4J getS4J() {
		return api;
	}

	public T execute(boolean shouldQueue) {
		if (error != null) {
			if (error instanceof RuntimeException) throw (RuntimeException) error;
			throw new IllegalStateException(error);
		}
		return value;
	}

	public void executeAsync(Consumer<? super T> success, Consumer<? super ApiFailure> failure) {
		if (error == null) {
			if (success == null) SLSAction.getDefaultSuccess().accept(value);
			else success.accept(value);
		} else {
			ApiError err = ApiError.coerce(error);
			ApiError.logFailure(null, err);
			if (failure == null) SLSAction.getDefaultFailure().accept(err);
			else failure.accept(err);
		}
	}

	public SLSAction<T> deadline(long timestamp) {
		return this;
	}
}





