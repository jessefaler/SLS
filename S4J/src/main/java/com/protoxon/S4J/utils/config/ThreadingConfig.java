

package com.protoxon.S4J.utils.config;

import com.protoxon.S4J.utils.NamedThreadFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;

public final class ThreadingConfig {

	private ExecutorService callbackPool;
	private ExecutorService actionPool;
	private ExecutorService supplierPool;
	private ScheduledExecutorService rateLimitPool;

	public ExecutorService getCallbackPool() {
		return callbackPool;
	}

	public ExecutorService getActionPool() {
		return actionPool;
	}

	public ExecutorService getSupplierPool() {
		return supplierPool;
	}

	public ScheduledExecutorService getRateLimitPool() {
		return rateLimitPool;
	}

	public void setCallbackPool(ExecutorService callbackPool) {
		if (callbackPool == null) callbackPool = ForkJoinPool.commonPool();
		this.callbackPool = callbackPool;
	}

	public void setActionPool(ExecutorService actionPool) {
		if (actionPool == null) actionPool = Executors.newSingleThreadExecutor(new NamedThreadFactory("Action"));
		this.actionPool = actionPool;
	}

	public void setSupplierPool(ExecutorService supplierPool) {
		if (supplierPool == null) supplierPool = Executors.newFixedThreadPool(3, new NamedThreadFactory("Supplier"));
		this.supplierPool = supplierPool;
	}

	public void setRateLimitPool(ScheduledExecutorService rateLimitPool) {
		if (rateLimitPool == null)
			rateLimitPool = Executors.newScheduledThreadPool(5, new NamedThreadFactory("RateLimit"));
		this.rateLimitPool = rateLimitPool;
	}
}
