package com.example.demo.application.shared.command;

import java.util.List;

/**
 * CreateDepartmentTreeCommand (應用層 - 批次遞迴建立部門樹指令)
 *
 * <pre>
 * 專為大規模組織架構初始化、Excel 大量匯入、或外部系統（如 HR 元資產系統）同步設計的<b>巢狀遞迴結構指令</b>。
 *
 * <b>遞迴演算法整合說明：</b> 應用服務層的 {@code DepartmentCommandService#createDepartmentTree} 
 * 在接收到本指令後， 會啟動深度優先搜尋 (DFS) 演算法，由上至下依序 INSERT 寫入資料庫，並非同步驅動閉包表（Closure Table）投影，
 * 確保父子層級的外鍵關係與空間座標在單次大事務中原子性建立。
 * </pre>
 *
 * @param tenantId   多租戶識別碼
 * @param operatorId 執行此批次建立動作的操作管理員識別碼 (維持系統審計上下文的完整性)
 * @param id         當前節點部門的唯一識別碼
 * @param parentId   當前節點部門的直接上級父部門 ID (若為這棵樹的頂層起點則傳入 {@code null})
 * @param code       當前節點部門的業務編碼
 * @param name       當前節點部門的顯示名稱
 * @param children   轄下更深層級的子部門指令巢狀清單 (透過此屬性形成遞迴樹狀 DTO，若無子節點則為空清單或 null)
 */
public record CreateDepartmentTreeCommand(String tenantId, String operatorId, String id, String parentId, String code,
		String name, List<CreateDepartmentTreeCommand> children) {
}