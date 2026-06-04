package com.example.demo.application.shared.dto;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Department Node (讀取端 - 資料庫底層查詢結果 DTO)
 *
 * <pre>
 * 這是寫入端基礎設施層 (Infrastructure Adapter) 與應用層 (Application Service) 之間流通的純粹資料載體。
 *
 * <b>技術架構定位</b>： 
 * 1. <b>ResultSet 映射器：</b> 專供 {@link NamedParameterJdbcTemplate} 的 RowMapper 執行對應，將底層數據轉為 Java 紀錄。 
 * 2. <b>多表聚合：</b> 此 DTO 是唯讀端多表聯查 (JOIN) 的結晶。它在單次資料庫 Round-trip 中，一口氣聚合了 {@link department_views} (基本資料、直屬人數、總人數) 
 * 與 {@link department_tree} (閉包表空間絕對深度) 的數據。 
 * 3. <b>不帶任何行為：</b> 作為標準的 DTO，它沒有任何業務行為，生命週期在 Query Service 將其組裝為 View 後即宣告結束。
 * </pre>
 *
 * @param tenantId        租戶識別碼，用於實行物理或邏輯的多租戶資料分流與隔離
 * @param id              部門唯一識別碼
 * @param parentId        父部門識別碼 (若此部門為一級頂層部門則為 null)
 * @param code            部門代碼
 * @param name            部門名稱
 * @param status          部門生命週期狀態 (ACTIVE, DISABLED)
 * @param sortOrder       同層級內部的前端顯示排序權重
 * @param depth           節點在當前幾何查詢範圍內的絕對樹狀深度 (0 代表本身，數字越大代表越往下層延伸)
 * @param directHeadcount 直屬編制人數
 * @param totalHeadcount  包含所有子孫組織在內的滾動加總總人數
 */
public record DepartmentNode(String tenantId, String id, String parentId, String code, String name, String status,
		int sortOrder, int depth, int directHeadcount, int totalHeadcount) {
}