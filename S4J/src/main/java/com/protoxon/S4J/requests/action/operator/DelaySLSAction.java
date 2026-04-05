

package com.protoxon.S4J.requests.action.operator;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.exceptions.ApiFailure;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

// big thanks to JDA for this tremendous code

public class DelaySLSAction<T> extends SLSActionOperator<T, T> {

	private final TimeUnit unit;
	private final long delay;
	private final ScheduledExecutorService scheduler;

	public DelaySLSAction(SLSAction<T> action, TimeUnit unit, long delay, ScheduledExecutorService scheduler) {
		super(action);
		this.unit = unit;
		this.delay = delay;
		this.scheduler = scheduler == null ? action.getS4J().getRateLimitPool() : scheduler;
	}

	@Override
	public void executeAsync(Consumer<? super T> success, Consumer<? super ApiFailure> failure) {
		action.executeAsync((result) -> scheduler.schedule(() -> doSuccess(success, result), delay, unit), failure);
	}

	@Override
	public T execute(boolean shouldQueue) {
		T result = action.execute(shouldQueue);
		try {
			unit.sleep(delay);
			return result;
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
