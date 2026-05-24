

package com.protoxon.S4J.requests;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.entities.S4J;
import com.protoxon.S4J.exceptions.ApiError;
import com.protoxon.S4J.exceptions.ApiFailure;
import com.protoxon.S4J.exceptions.RateLimitedException;
import com.protoxon.S4J.requests.action.operator.impl.PaginationActionImpl;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class CompletedPaginationAction<T> extends PaginationActionImpl<T> {

	private final S4J api;
	protected final List<T> value;
	protected final Throwable error;

	public CompletedPaginationAction(S4J api, List<T> value, Throwable error) {
		super(api);
		this.api = api;
		this.value = value;
		this.error = error;
	}

	public CompletedPaginationAction(S4J api, List<T> value) {
		this(api, value, null);
	}

	@Override
	public S4J getS4J() {
		return api;
	}

    @Override
	public List<T> execute(boolean shouldQueue) throws RateLimitedException {
        if (error != null) {
            if (error instanceof RateLimitedException) throw (RateLimitedException) error;
            if (error instanceof RuntimeException) throw (RuntimeException) error;
            throw new IllegalStateException(error);
        }
        return value;
    }

    @Override
	public void executeAsync(Consumer<? super List<T>> success, Consumer<? super ApiFailure> failure) {
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

    @Override
	public PaginationIterator<T> iterator() {
        return new PaginationIterator<>(value, Collections::emptyList);
    }
}
