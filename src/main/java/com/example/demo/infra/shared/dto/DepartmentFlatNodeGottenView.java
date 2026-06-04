package com.example.demo.infra.shared.dto;

/**
 * Department Flat Node Gotten View (扁平節點視圖)
 *
 * <p>
 * 專供前端 API 渲染「不需階層結構」的場景使用。 例如：麵包屑導覽列 (Breadcrumb)、下拉式選單 (Dropdown)、或關鍵字模糊搜尋結果。
 * 為了保持輕量，此視圖通常不包含子節點清單與人數統計。
 * </p>
 *
 * @param tenantId  租戶識別碼
 * @param id        部門唯一識別碼
 * @param parentId  父部門 ID
 * @param code      部門代碼
 * @param name      部門名稱
 * @param status    部門狀態
 * @param sortOrder 排序權重
 * @param depth     節點深度 (用於麵包屑排序)
 */
public record DepartmentFlatNodeGottenView(String tenantId, String id, String parentId, String code, String name,
		String status, int sortOrder, int depth) {
}