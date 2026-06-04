package com.example.demo.infra.adapter;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.demo.application.port.DepartmentRollUpProjectionHandlerPort;

import lombok.RequiredArgsConstructor;

/**
 * Department Roll-Up Projection Handler Adapter (Infrastructure Layer)
 *
 * <pre>
 * 負責執行部門人數統計滾動加總的具體實作。 
 * 
 * <strong>技術選型與演算法優勢</strong>： 充分利用 Closure Table (department_tree) 的幾何映射優勢，
 * 搭配 {@link NamedParameterJdbcTemplate} 執行原生 SQL。 透過子查詢 (Subquery) 一次性鎖定並更新整條祖先路徑的總人數， 
 * 徹底免除了在 Java 記憶體中執行 N+1 遞迴查詢與更新的效能瓶頸，實現 $O(1)$ 網路往返的高效聚合。
 * </pre>
 */
@Repository
@RequiredArgsConstructor
class DepartmentRollUpProjectionHandlerAdapter implements DepartmentRollUpProjectionHandlerPort {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	/**
	 * 更新單一部門的直屬人數。
	 */
	@Override
	public void incrementDirectHeadcount(String tenantId, String departmentId, int delta) {
		String sql = """
				UPDATE department_views
				SET direct_headcount = direct_headcount + :delta
				WHERE tenant_id = :tenantId AND id = :departmentId
				""";

		jdbcTemplate.update(sql, new MapSqlParameterSource().addValue("tenantId", tenantId)
				.addValue("departmentId", departmentId).addValue("delta", delta));
	}

	/**
	 * 更新自身與所有祖先的總人數。
	 */
	@Override
	public void incrementTotalHeadcountForAncestors(String tenantId, String descendantId, int delta) {
		// 演算法精髓：利用 department_tree 找出 descendantId 的所有祖先 (包含自己因為有 depth=0 的自交紀錄)，
		// 然後一次性對這些祖先在 department_views 中的 total_headcount 加上 delta。
		String sql = """
				UPDATE department_views
				SET total_headcount = total_headcount + :delta
				WHERE tenant_id = :tenantId
				  AND id IN (
				      SELECT ancestor_id
				      FROM department_tree
				      WHERE tenant_id = :tenantId
				        AND descendant_id = :descendantId
				  )
				""";

		jdbcTemplate.update(sql, new MapSqlParameterSource().addValue("tenantId", tenantId)
				.addValue("descendantId", descendantId).addValue("delta", delta));
	}
}