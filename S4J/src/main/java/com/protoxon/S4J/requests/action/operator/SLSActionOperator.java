

package com.protoxon.S4J.requests.action.operator;

import com.protoxon.S4J.entities.S4J;
import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.exceptions.ApiError;
import com.protoxon.S4J.exceptions.ApiFailure;

import java.util.function.Consumer;

// big thanks to JDA for this tremendous code

public abstract class SLSActionOperator<I, O> implements SLSAction<O> {

	protected final SLSAction<I> action;
	protected long deadline = 0;

	public SLSActionOperator(SLSAction<I> action) {
		this.action = action;
	}

	protected static <E> void doSuccess(Consumer<? super E> callback, E value) {
		if (callback == null) SLSAction.getDefaultSuccess().accept(value);
		else callback.accept(value);
	}

	protected static void doFailure(Consumer<? super ApiFailure> callback, ApiFailure failure) {
		if (callback == null) SLSAction.getDefaultFailure().accept(failure);
		else callback.accept(failure);
	}

	protected static void doFailureCoerced(Consumer<? super ApiFailure> callback, Throwable throwable) {
		doFailure(callback, ApiError.coerce(throwable));
	}

	@Override
	public SLSAction<O> deadline(long timestamp) {
		this.deadline = timestamp;
		action.deadline(timestamp);
		return this;
	}

	@Override
	public S4J getS4J() {
		return action.getS4J();
	}
}
