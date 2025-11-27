package com.protoxon.S4J.requests.action.operator.impl;

import com.protoxon.S4J.entites.S4J;
import com.protoxon.S4J.requests.Request;
import com.protoxon.S4J.requests.Response;
import com.protoxon.S4J.requests.Route;
import com.protoxon.S4J.utils.PaginatedEntity;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public class PaginationResponseImpl<T> extends PaginationActionImpl<T> {

	protected final Function<JSONObject, T> handler;

	private PaginationResponseImpl(S4J api, Route.CompiledRoute route, Function<JSONObject, T> handler) {
		super(api, route);
		this.handler = handler;
	}

	public static <T> PaginationResponseImpl<T> onPagination(
			S4J api, Route.CompiledRoute route, Function<JSONObject, T> handler) {
		return new PaginationResponseImpl<>(api, route, handler);
	}

	@Override
	public void handleSuccess(Response response, Request<List<T>> request) {
		JSONObject object = response.getObject();

		PaginatedEntity paginatedEntity = PaginatedEntity.create(object);
		totalPages = paginatedEntity.getTotalPages();

		List<T> entities = new LinkedList<>();
		for (Object o : object.getJSONArray("data")) {
			T entity = handler.apply(new JSONObject(o.toString()));
			entities.add(entity);

			if (useCache) cached.add(entity);

			last = entity;
		}

		PAGINATION_LOG.trace("Successfully retrieved {} entities", entities.size());

		if (useCache)
			PAGINATION_LOG.debug("Cache enabled: caching {} entities, cache size: {}", entities.size(), cached.size());

		currentPage = getCurrentPage() + 1;
		request.onSuccess(entities);
	}
}
