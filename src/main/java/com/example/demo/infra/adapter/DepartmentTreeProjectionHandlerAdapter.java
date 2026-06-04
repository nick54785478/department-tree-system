package com.example.demo.infra.adapter;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.port.DepartmentTreeProjectionHandlerPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Department Tree Projection Handler Adapter (Infrastructure Layer)
 *
 * <pre>
 * 負責維護 department_tree 閉包表 (Closure Table) 幾何結構的具體實作。 

 * <strong>架構技術選型</strong>：全面棄用 JPA EntityManager，改採 {@link NamedParameterJdbcTemplate}。
 * 
 * 理由：閉包表的操作涉及大量的笛卡爾乘積 (Cartesian Product) 與子查詢，這些都是 JPA Entity 的弱項。
 * 直接使用 Spring JDBC 可以完全免除 Hibernate 一級快取的記憶體負擔，提供最純粹、最高效的 SQL 批量寫入與刪除效能。
 * 
 * 註：雖然此處標註了 @Transactional，但在正確的六角架構下， 交易的絕對邊界通常由上層的 Projection Handler 掌控。
 * </pre>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
class DepartmentTreeProjectionHandlerAdapter implements DepartmentTreeProjectionHandlerPort {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	@Override
	@Transactional
	public void insertRelation(String tenantId, String ancestorId, String descendantId, int depth) {
		String sql = """
				    INSERT INTO department_tree (tenant_id, ancestor_id, descendant_id, depth)
				    VALUES (:tenantId, :a, :d, :depth)
				""";

		MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId)
				.addValue("a", ancestorId).addValue("d", descendantId).addValue("depth", depth);

		jdbcTemplate.update(sql, params);
	}

	@Override
	@Transactional
	public void insertSelfRelation(String tenantId, String id) {
		String sql = """
				    INSERT INTO department_tree (tenant_id, ancestor_id, descendant_id, depth)
				    VALUES (:tenantId, :id, :id, 0)
				""";

		MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id);

		jdbcTemplate.update(sql, params);
	}

	@Override
	@Transactional
	public void deleteRelationsByDescendant(String tenantId, String descendantId) {
		// 演算法精髓：從舊有祖先樹中斷開，但「不刪除」目標節點本身與其子孫之間的關係。
		// 這確保了一整棵子樹被「拔起」時，內部結構依然完好無缺。
		String sql = """
				    DELETE FROM department_tree dt
				    WHERE dt.tenant_id = :tenantId
				      AND dt.descendant_id IN (
				        SELECT descendant_id
				        FROM department_tree
				        WHERE tenant_id = :tenantId AND ancestor_id = :id
				      )
				      AND dt.ancestor_id NOT IN (
				        SELECT descendant_id
				        FROM department_tree
				        WHERE tenant_id = :tenantId AND ancestor_id = :id
				      )
				""";

		MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id",
				descendantId);

		jdbcTemplate.update(sql, params);
	}

	@Override
	@Transactional
	public void insertInheritedRelations(String tenantId, String newParentId, String movedSubtreeRootId) {
		// 演算法精髓：利用笛卡爾乘積 (Cross Join) 原理。
		// 將新父節點 (newParentId) 的所有祖先路徑，完美複製給「被移動的子樹」中的所有節點。
		String sql = """
				  INSERT INTO department_tree (tenant_id, ancestor_id, descendant_id, depth)
				  SELECT
				    :tenantId,
				    p.ancestor_id,
				    c.descendant_id,
				    p.depth + c.depth + 1
				  FROM department_tree p
				  JOIN department_tree c
				    ON c.ancestor_id = :subtreeRoot AND c.tenant_id = :tenantId
				  WHERE p.descendant_id = :newParent AND p.tenant_id = :tenantId
				""";

		MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId)
				.addValue("newParent", newParentId).addValue("subtreeRoot", movedSubtreeRootId);

		jdbcTemplate.update(sql, params);
	}

	@Override
	@Transactional
	public void deleteNodeAndRelations(String tenantId, String id) {
		// 物理清除 Closure Table 中跟這個節點有牽連的所有路徑 (不論是作為祖先或作為子孫)
		String sql = """
				    DELETE FROM department_tree
				    WHERE tenant_id = :tenantId
				      AND (ancestor_id = :id OR descendant_id = :id)
				""";

		MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id);

		jdbcTemplate.update(sql, params);
	}
}