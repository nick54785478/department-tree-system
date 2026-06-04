package com.example.demo.infra.projection;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DepartmentTree (讀取端投影模型 - 組織樹幾何路徑閉包表)
 *
 * <pre>
 * 專為多租戶、海量、無限階層組織結構優化的<b>讀取端幾何投影模型 (Closure Table)</b>。 
 * 
 * <b>演算法精髓</b>：
 * 本表不再採用傳統低效的「鄰接清單 (Adjacency List，即只記 parent_id)」設計。
 * 而是空間換時間，打平並<b>記錄組織樹狀結構中，任意兩個具有直系血緣節點的完整對應關係與階層距離</b>，
 * 這使得傳統需要依賴複雜遞迴或 CTE (Common Table Expressions) 的樹狀子樹遍歷查詢，
 * 轉化為單次 $O(1)$ 複雜度的扁平 SQL JOIN 查詢，效能極高。
 * </pre>
 */
@Getter
@Entity
@Table(name = "department_tree", indexes = {
		// 核心優化索引 1：專為「向下遍歷」設計。輸入一個部門 ID 作為 ancestor，一口氣撈出其下所有子孫節點 (重組子樹視圖)
		@Index(name = "idx_tree_tenant_ancestor", columnList = "tenant_id, ancestor_id"),
		// 核心優化索引 2：專為「向上追溯」設計。輸入一個部門 ID 作為 descendant，一路向上追溯至最頂層一級根節點 (生成麵包屑導覽路徑)
		@Index(name = "idx_tree_tenant_descendant", columnList = "tenant_id, descendant_id") })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepartmentTree {

	/**
	 * 自增幾何路徑記錄主鍵
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * 多租戶安全識別碼。確保在數百萬筆幾何關係中，透過 SQL 索引在第一時間將掃描空間限縮在特定租戶之內
	 */
	@Column(name = "tenant_id", nullable = false, updatable = false, length = 50)
	private String tenantId;

	/**
	 * 上層祖先部門唯一識別碼。當 {@code depth = 0} 時，此欄位數值與 descendant_id 相同 (代表自我參照記錄)
	 */
	@Column(name = "ancestor_id", nullable = false, length = 50)
	private String ancestorId;

	/**
	 * 下層子孫部門唯一識別碼。
	 */
	@Column(name = "descendant_id", nullable = false, length = 50)
	private String descendantId;

	/**
	 * 兩個組織節點之間的幾何階層距離。
	 * <p>
	 * 0 代表自己對自己，1 代表直接上下級直屬父子關係，2 代表隔代祖孫關係，依此類推。
	 * </p>
	 */
	@Column(name = "depth", nullable = false)
	private int depth;

	/**
	 * 全參數建構子，落實幾何欄位的 non-null 業務保護。
	 */
	public DepartmentTree(String tenantId, String ancestorId, String descendantId, int depth) {
		this.tenantId = Objects.requireNonNull(tenantId, "TenantId required");
		this.ancestorId = Objects.requireNonNull(ancestorId, "AncestorId required");
		this.descendantId = Objects.requireNonNull(descendantId, "DescendantId required");
		this.depth = depth;
	}
}