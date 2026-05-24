

package com.protoxon.S4J.requests.action.operator;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.exceptions.ApiError;
import com.protoxon.S4J.exceptions.ApiFailure;
import com.protoxon.S4J.exceptions.SLSException;
import com.protoxon.S4J.utils.ExceptionUtils;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

// big thanks to JDA for this tremendous code

public class MapErrorSLSAction<T> extends SLSActionOperator<T, T> {

	private final Predicate<? super ApiError> check;
	private final Function<? super ApiError, ? extends T> map;

	public MapErrorSLSAction(
			SLSAction<T> action, Predicate<? super ApiError> check, Function<? super ApiError, ? extends T> map) {
		super(action);
		this.check = check;
		this.map = map;
	}

	@Override
	public void executeAsync(Consumer<? super T> success, Consumer<? super ApiFailure> failure) {
		action.executeAsync(
				success,
				(err) -> {
					ApiError error = (ApiError) err;
					try {
						if (check.test(error)) doSuccess(success, map.apply(error));
						else doFailure(failure, error);
					} catch (Throwable e) {
						doFailureCoerced(failure, ExceptionUtils.appendCause(e, error));
					}
				});
	}

	@Override
	public T execute(boolean shouldQueue) {
		try {
			return action.execute(shouldQueue);
		} catch (Throwable error) {
			try {
				ApiError err = ApiError.coerce(error);
				if (check.test(err)) return map.apply(err);
			} catch (Throwable e) {
				fail(ExceptionUtils.appendCause(e, error));
			}
			fail(error);
		}
		throw new AssertionError("Unreachable");
	}

	private void fail(Throwable error) {
		if (error instanceof SLSException) throw (SLSException) error;
		else throw new RuntimeException(error);
	}
}
