package com.example.demo.infra.adapter;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.demo.application.port.DepartmentTreeReaderPort;
import com.example.demo.infra.shared.dto.DepartmentNode;

import lombok.RequiredArgsConstructor;

/**
 * Department Query Adapter (Infrastructure Layer - JdbcTemplate 實作)
 *
 * <pre>
 * 專為高效能讀取設計，負責執行原生 SQL 並將 ResultSet 映射至 DTO。 
 * 
 * 架構安全約定：所有的查詢皆已全面加上邏輯刪除過濾條件 (status != 'DELETED')。 
 * 這是為了確保在時光機架構下，被標記為刪除但尚未被物理清除的幽靈節點，不會外洩給前端顯示。
 * </pre>
 */
@Repository
@RequiredArgsConstructor
class DepartmentTreeReaderAdapter implements DepartmentTreeReaderPort {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	/**
	 * 查詢子樹 (結合樹狀幾何與視圖資料)
	 */
	@Override
	public List<DepartmentNode> getSubtree(String tenantId, String rootId, boolean includeDisabled) {
		StringBuilder sql = new StringBuilder("""
				SELECT
				    v.tenant_id,
				    v.id, v.parent_id, v.code, v.name, v.status, v.sort_order,
				    v.direct_headcount, v.total_headcount,
				    dt.depth
				FROM department_tree dt
				JOIN department_views v ON dt.descendant_id = v.id AND dt.tenant_id = v.tenant_id
				WHERE dt.tenant_id = :tenantId
				  AND dt.ancestor_id = :rootId
				  AND v.status != 'DELETED'
				""");

		// 動態條件過濾 (Dynamic Filtering)
		if (!includeDisabled) {
			// 如果前端不要看 DISABLED，我們就硬性加上狀態必須是 ACTIVE 的條件
			sql.append(" AND v.status = 'ACTIVE' ");
		}

		// 加上排序條件
		sql.append(" ORDER BY dt.depth ASC, v.sort_order ASC");

		MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("rootId",
				rootId);

		return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> mapRowToNode(rs));
	}

	/**
	 * 查詢麵包屑路徑 (向上追溯直系祖先)
	 */
	@Override
	public List<DepartmentNode> getBreadcrumbPath(String tenantId, String departmentId) {
		String sql = """
				SELECT
				    v.tenant_id,
				    v.id, v.parent_id, v.code, v.name, v.status, v.sort_order,
				    v.direct_headcount, v.total_headcount,
				    dt.depth
				FROM department_tree dt
				JOIN department_views v ON dt.ancestor_id = v.id AND dt.tenant_id = v.tenant_id
				WHERE dt.tenant_id = :tenantId
				  AND dt.descendant_id = :deptId
				  AND v.status != 'DELETED' -- 🌟 核心防護：過濾邏輯刪除的幽靈節點
				ORDER BY dt.depth DESC
				""";

		MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("deptId",
				departmentId);

		return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapRowToNode(rs));
	}

	/**
	 * 全域關鍵字模糊搜尋
	 */
	@Override
	public List<DepartmentNode> searchDepartments(String tenantId, String keyword) {
		String sql = """
				SELECT
				    v.tenant_id,
				    v.id, v.parent_id, v.code, v.name, v.status, v.sort_order,
				    v.direct_headcount, v.total_headcount,
				    0 as depth
				FROM department_views v
				WHERE v.tenant_id = :tenantId
				  AND (v.name LIKE :keyword OR v.code LIKE :keyword)
				  AND v.status != 'DELETED' -- 🌟 核心防護：過濾邏輯刪除的幽靈節點
				ORDER BY v.sort_order ASC
				LIMIT 50
				""";

		MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("keyword",
				"%" + keyword + "%");

		return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapRowToNode(rs));
	}

	/**
	 * 提取共用的 RowMapper 邏輯，保持程式碼 DRY (Don't Repeat Yourself)
	 */
	private DepartmentNode mapRowToNode(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new DepartmentNode(rs.getString("tenant_id"), rs.getString("id"), rs.getString("parent_id"),
				rs.getString("code"), rs.getString("name"), rs.getString("status"), rs.getInt("sort_order"),
				rs.getInt("depth"), rs.getInt("direct_headcount"), rs.getInt("total_headcount"));
	}
}