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
