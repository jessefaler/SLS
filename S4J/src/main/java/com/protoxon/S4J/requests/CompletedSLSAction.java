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

import com.protoxon.S4J.entites.S4J;
import com.protoxon.S4J.SLSAction;
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

	public void executeAsync(Consumer<? super T> success, Consumer<? super Throwable> failure) {
		if (error == null) {
			if (success == null) SLSAction.getDefaultSuccess().accept(value);
			else success.accept(value);
		} else {
			if (failure == null) SLSAction.getDefaultFailure().accept(error);
			else failure.accept(error);
		}
	}

	public SLSAction<T> deadline(long timestamp) {
		return this;
	}
}





