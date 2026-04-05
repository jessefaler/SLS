

package com.protoxon.S4J.requests.action.operator;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.exceptions.ApiFailure;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

// big thanks to JDA for this tremendous code

public class FlatMapSLSAction<I, O> extends SLSActionOperator<I, O> {

	private final Function<? super I, ? extends SLSAction<O>> function;
	private final Predicate<? super I> condition;

	public FlatMapSLSAction(
            SLSAction<I> action,
			Predicate<? super I> condition,
			Function<? super I, ? extends SLSAction<O>> function) {
		super(action);
		this.function = function;
		this.condition = condition;
	}

	@Override
	public void executeAsync(Consumer<? super O> success, Consumer<? super ApiFailure> failure) {
		action.executeAsync(
				(result) -> {
					if (condition != null && !condition.test(result)) return;
                    SLSAction<O> then = function.apply(result);
					if (then == null) doFailureCoerced(failure, new IllegalStateException("FlatMap operand is null"));
					else then.executeAsync(success, failure);
				},
				failure);
	}

	@Override
	public O execute(boolean shouldQueue) {
		return function.apply(action.execute(shouldQueue)).execute(shouldQueue);
	}
}
