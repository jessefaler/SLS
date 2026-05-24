

package com.protoxon.S4J.requests.action.operator;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.exceptions.ApiFailure;

import java.util.function.Consumer;
import java.util.function.Function;

// big thanks to JDA for this tremendous code

public class MapSLSAction<I, O> extends SLSActionOperator<I, O> {

	private final Function<? super I, ? extends O> function;

	public MapSLSAction(SLSAction<I> action, Function<? super I, ? extends O> function) {
		super(action);
		this.function = function;
	}

	@Override
	public void executeAsync(Consumer<? super O> success, Consumer<? super ApiFailure> failure) {
		action.executeAsync(
				(result) -> doSuccess(success, function.apply(result)), (error) -> doFailure(failure, error));
	}

	@Override
	public O execute(boolean shouldQueue) {
		return function.apply(action.execute(shouldQueue));
	}
}
