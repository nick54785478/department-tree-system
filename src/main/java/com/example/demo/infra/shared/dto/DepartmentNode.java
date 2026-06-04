package com.example.demo.infra.shared.dto;

/**
 * Department Node (資料庫查詢結果 DTO)
 *
 * <p>
 * 這是內部流通的扁平資料載體，專供 Infrastructure Layer (如 JdbcTemplate) 將 SQL ResultSet 映射為
 * Java 物件，再交給 Application Layer 進行組裝。 結合了 department_views (基本資料與統計) 與
 * department_tree (階層深度)。
 * </p>
 *
 * @param tenantId        租戶識別碼 (用於資料隔離)
 * @param id              部門唯一識別碼
 * @param parentId        父部門 ID (若為 Root 則為 null)
 * @param code            部門代碼
 * @param name            部門名稱
 * @param status          部門狀態 (如 ACTIVE, DISABLED)
 * @param sortOrder       同層級間的排序權重
 * @param depth           節點在樹狀結構中的絕對深度 (0 代表本身，數字越大代表越底層)
 * @param directHeadcount 直屬人數 (僅隸屬於此部門的人數)
 * @param totalHeadcount  總人數 (包含此部門及所有子孫部門的人數加總)
 */
public record DepartmentNode(String tenantId, String id, String parentId, String code, String name, String status,
		int sortOrder, int depth, int directHeadcount, int totalHeadcount) {
}