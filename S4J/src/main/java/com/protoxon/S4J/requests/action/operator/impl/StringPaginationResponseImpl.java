package com.protoxon.S4J.requests.action.operator.impl;

import com.protoxon.S4J.entities.S4J;
import com.protoxon.S4J.requests.Request;
import com.protoxon.S4J.requests.Response;
import com.protoxon.S4J.requests.Route;
import com.protoxon.S4J.utils.PaginatedEntity;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;

public class StringPaginationResponseImpl extends PaginationActionImpl<String> {

	private StringPaginationResponseImpl(S4J api, Route.CompiledRoute route) {
		super(api, route);
		limit.set(50);
	}

	public static StringPaginationResponseImpl onPagination(S4J api, Route.CompiledRoute route) {
		return new StringPaginationResponseImpl(api, route);
	}

	@Override
	public void handleSuccess(Response response, Request<List<String>> request) {
		JSONObject object = response.getObject();

		PaginatedEntity paginatedEntity = PaginatedEntity.create(object);
		total = paginatedEntity.getTotal();
		totalPages = paginatedEntity.getTotalPages();

		List<String> lines = new LinkedList<>();
		JSONArray dataArray = object.optJSONArray("data");
		if (dataArray == null) {
			dataArray = new JSONArray();
		}
		for (int i = 0; i < dataArray.length(); i++) {
			String line = dataArray.getString(i);
			lines.add(line);

			if (useCache) cached.add(line);

			last = line;
		}

		PAGINATION_LOG.trace("Successfully retrieved {} log lines", lines.size());

		if (useCache)
			PAGINATION_LOG.debug("Cache enabled: caching {} log lines, cache size: {}", lines.size(), cached.size());

		currentPage = getCurrentPage() + 1;
		request.onSuccess(lines);
	}
}
