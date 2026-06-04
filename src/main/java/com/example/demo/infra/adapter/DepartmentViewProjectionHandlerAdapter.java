package com.example.demo.infra.adapter;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.demo.application.port.DepartmentViewProjectionHandlerPort;

import lombok.RequiredArgsConstructor;

/**
 * Department View Projection Adapter (Infrastructure Layer)
 * 
 * <pre>
 * 專責維護 department_views 扁平視圖表的寫入與更新。 統一採用 NamedParameterJdbcTemplate 執行原生
 * SQL，保持底層技術棧一致性，追求極致效能。 此處負責所有的單維度資料變更，完全不處理樹狀幾何路徑。
 * </pre>
 */
@Repository
@RequiredArgsConstructor
class DepartmentViewProjectionHandlerAdapter implements DepartmentViewProjectionHandlerPort {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	/**
	 * 新增部門視圖資料，並將人數統計初始化為 0。
	 */
	@Override
	public void insertDepartmentView(String tenantId, String id, String parentId, String code, String name,
			String status, int sortOrder) {
		String sql = """
				    INSERT INTO department_views (id, tenant_id, parent_id, code, name, status, sort_order, direct_headcount, total_headcount)
				    VALUES (:id, :tenantId, :parentId, :code, :name, :status, :sortOrder, 0, 0)
				""";

		MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id).addValue("tenantId", tenantId)
				.addValue("parentId", parentId).addValue("code", code).addValue("name", name).addValue("status", status)
				.addValue("sortOrder", sortOrder);

		jdbcTemplate.update(sql, params);
	}

	/**
	 * 更新視圖表中的直屬父節點關聯。
	 */
	@Override
	public void updateDepartmentViewParent(String tenantId, String id, String newParentId) {
		String sql = """
				    UPDATE department_views
				    SET parent_id = :newParentId
				    WHERE tenant_id = :tenantId AND id = :id
				""";

		MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id)
				.addValue("newParentId", newParentId);

		jdbcTemplate.update(sql, params);
	}

	/**
	 * 邏輯刪除部門視圖資料。
	 * 
	 * <strong>關鍵實作</strong>：雖然 Port 的意圖是 delete，但實作採用邏輯刪除 (UPDATE status =
	 * 'DELETED')， 以保留歷史痕跡供時光機引擎復原使用。
	 */
	@Override
	public void deleteDepartmentView(String tenantId, String id) {
		String sql = """
				    UPDATE department_views
				    SET status = 'DELETED'
				    WHERE tenant_id = :tenantId AND id = :id
				""";

		MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id);

		jdbcTemplate.update(sql, params);
	}

	@Override
	public void updateDepartmentName(String tenantId, String id, String newName) {
		String sql = """
				    UPDATE department_views
				    SET name = :newName
				    WHERE tenant_id = :tenantId AND id = :id
				""";

		jdbcTemplate.update(sql, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id)
				.addValue("newName", newName));
	}

	@Override
	public void updateDepartmentStatus(String tenantId, String id, String status) {
		String sql = """
				    UPDATE department_views
				    SET status = :status
				    WHERE tenant_id = :tenantId AND id = :id
				""";

		jdbcTemplate.update(sql, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id)
				.addValue("status", status));
	}

	@Override
	public void updateDepartmentSortOrder(String tenantId, String id, int sortOrder) {
		String sql = """
				    UPDATE department_views
				    SET sort_order = :sortOrder
				    WHERE tenant_id = :tenantId AND id = :id
				""";

		jdbcTemplate.update(sql, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id)
				.addValue("sortOrder", sortOrder));
	}
}