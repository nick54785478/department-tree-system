package com.example.demo.infra.shared.dto;

import java.util.List;

/**
 * Department Tree Node Gotten View (樹狀節點視圖)
 *
 * <p>
 * 專供前端 API 渲染「完整組織樹狀圖 (Organization Chart)」使用。 透過 children
 * 屬性形成遞迴的巢狀結構，並包含預先計算好的人數統計。 這些資料在 Query Service 中由扁平的 DepartmentNode 組合而成。
 * </p>
 *
 * @param tenantId        租戶識別碼
 * @param id              部門唯一識別碼
 * @param parentId        父部門 ID
 * @param code            部門代碼
 * @param name            部門名稱
 * @param status          部門狀態
 * @param sortOrder       排序權重
 * @param depth           節點深度
 * @param directHeadcount 直屬人數 (可供前端顯示：本部門實際編制)
 * @param totalHeadcount  總人數 (可供前端顯示：包含轄下單位的總兵力)
 * @param children        子部門清單 (前端透過此屬性遞迴渲染樹狀 UI)
 */
public record DepartmentTreeNodeGottenView(String tenantId, String id, String parentId, String code, String name,
		String status, int sortOrder, int depth, int directHeadcount, int totalHeadcount,
		List<DepartmentTreeNodeGottenView> children) {
}