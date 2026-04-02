/*
 *    Copyright 2021-2022 Matt Malec, and the Pterodactyl4J contributors
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.protoxon.S4J.requests;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import com.protoxon.S4J.exceptions.ApiError;
import com.protoxon.S4J.exceptions.ApiFailure;
import com.protoxon.S4J.exceptions.HttpException;
import com.protoxon.S4J.exceptions.RateLimitedException;
import okhttp3.RequestBody;

public class Request<T> {

	private final SLSActionImpl<T> action;
	private final Consumer<? super T> onSuccess;
	private final Consumer<? super ApiFailure> failureConsumer;
	private final Route.CompiledRoute route;
	private final RequestBody requestBody;
	private final boolean shouldQueue;
	private final long deadline;

	private boolean done = false;
	private boolean isCancelled = false;

	public Request(
			SLSActionImpl<T> action,
			Consumer<? super T> onSuccess,
			Consumer<? super ApiFailure> failureConsumer,
			Route.CompiledRoute route,
			RequestBody requestBody,
			boolean shouldQueue,
			long deadline) {
		this.action = action;
		this.onSuccess = onSuccess;
		this.failureConsumer = failureConsumer;
		this.route = route;
		this.requestBody = requestBody;
		this.shouldQueue = shouldQueue;
		this.deadline = deadline;
	}

	public void onSuccess(T success) {
		if (done) return;
		done = true;
		action.getS4J().getCallbackPool().execute(() -> {
			try {
				onSuccess.accept(success);
			} catch (Throwable t) {
				System.err.printf("Encountered error while processing success consumer: %s%n", t);
				throw t;
			}
		});
	}

	public void setOnFailure(Response response) {
		if (response.isRateLimit()) {
			onFailure(RateLimitedException.fromResponse(route, response));
			return;
		}
		if (response.getCode() == Response.ERROR_CODE) {
			if (response.getException() != null) {
				String exceptionMessage = response.getException().getMessage();
				String errorMessage =
						exceptionMessage != null
								? "Unable to reach the api server: " + exceptionMessage
								: "Unable to reach the api server: "
										+ response.getException().getClass().getSimpleName();
				onFailure(new HttpException(errorMessage));
			} else {
				onFailure(new HttpException("Unable to reach the API server (unknown error)"));
			}
			return;
		}
		onFailure(ApiError.fromResponse(response));
	}

	public void onFailure(Throwable failException) {
		if (done) return;
		done = true;
		ApiError err = ApiError.coerce(failException);
		ApiError.logFailure(route, err);
		action.getS4J().getCallbackPool().execute(() -> {
			try {
				failureConsumer.accept(err);
			} catch (Throwable t) {
				System.err.printf("Encountered error while processing failure consumer: %s%n", t);
				throw t;
			}
		});
	}

	public void cancel() {
		this.isCancelled = true;
	}

	public boolean isCancelled() {
		return isCancelled;
	}

	public void onCancelled() {
		onFailure(new CancellationException("Action has been cancelled"));
	}

	public void onTimeout() {
		onFailure(new TimeoutException("Action has timed out"));
	}

	public boolean isSkipped() {
		boolean cancel = isCancelled();
		boolean timeout = isTimeout();

		if (timeout) onTimeout();

		if (cancel) onCancelled();

		return cancel || timeout;
	}

	public RequestBody getRequestBody() {
		return requestBody;
	}

	public Route.CompiledRoute getRoute() {
		return route;
	}

	public boolean shouldQueue() {
		return shouldQueue;
	}

	private boolean isTimeout() {
		return deadline > 0 && deadline < System.currentTimeMillis();
	}

	public void handleResponse(Response response) {
		action.handleResponse(response, this);
	}
}
