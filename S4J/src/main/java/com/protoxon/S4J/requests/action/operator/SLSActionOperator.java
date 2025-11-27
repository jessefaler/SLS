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

package com.protoxon.S4J.requests.action.operator;

import com.protoxon.S4J.entites.S4J;
import com.protoxon.S4J.SLSAction;

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

	protected static void doFailure(Consumer<? super Throwable> callback, Throwable throwable) {
		if (callback == null) SLSAction.getDefaultFailure().accept(throwable);
		else callback.accept(throwable);
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
