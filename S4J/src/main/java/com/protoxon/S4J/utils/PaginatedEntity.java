package com.protoxon.S4J.utils;

import org.json.JSONObject;

public class PaginatedEntity {

	private final int total;
	private final int entitiesPerPage;
	private final int currentPage;
	private final int totalPages;

	private PaginatedEntity(int total, int entitiesPerPage, int currentPage, int totalPages) {
		this.total = total;
		this.entitiesPerPage = entitiesPerPage;
		this.currentPage = currentPage;
		this.totalPages = totalPages;
	}

	public static PaginatedEntity create(JSONObject json) {
		JSONObject object = json.getJSONObject("meta").getJSONObject("pagination");
		return new PaginatedEntity(
				object.getInt("total"),
				object.getInt("per_page"),
				object.getInt("current_page"),
				object.getInt("total_pages"));
	}

	public int getTotal() {
		return total;
	}

	public int getEntitiesPerPage() {
		return entitiesPerPage;
	}

	public int getCurrentPage() {
		return currentPage;
	}

	public int getTotalPages() {
		return totalPages;
	}
}
