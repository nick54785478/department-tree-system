package com.example.demo.application.shared.dto;

import java.util.List;

import com.example.demo.application.service.DepartmentQueryService;
import com.example.demo.infra.shared.dto.DepartmentNode;

/**
 * Department Tree Node Gotten View (讀取端 - 樹狀階層節點視圖)
 *
 * <pre>
 * 專供前端表現層 (Interface Layer / UI) 渲染「組織架構圖 (Organization Chart)」或「樹狀導覽列」使用。
 *
 * <b>架構設計特點</b>： 
 * 1. <b>遞迴巢狀結構：</b> 透過 children 屬性形成對自身的遞迴參照，完美對接前端多層級組件。 
 * 2. <b>唯讀端資料豐富化 (Data Enrichment)：</b> 包含 directHeadcount 與 totalHeadcount 等預先滾動計算好的人數統計。
 * 3. <b>效能解耦：</b> 此視圖的組裝是由 {@link DepartmentQueryService} 在 Java 記憶體中，將一維的 {@link DepartmentNode}
 * 優化組裝而成， 不佔用寫入端 (Command Side) 的任何併發運算資源。
 * </pre>
 *
 * @param tenantId        租戶識別碼 (落實前端展示層的多租戶資料隔離防護)
 * @param id              部門唯一識別碼 (唯讀視圖 ID)
 * @param parentId        父部門唯一識別碼 (若為一級部門根節點則為 null)
 * @param code            部門業務代碼 (例如：IT-001，用於前端畫面識別與檢索)
 * @param name            部門顯示名稱
 * @param status          部門當前生命週期狀態 (通常對應 ACTIVE, DISABLED)
 * @param sortOrder       同層級節點之間的顯示排序權重 (數值越小前端排越前面)
 * @param depth           節點在當前子樹下的相對或絕對階層深度
 * @param directHeadcount 直屬人數 (僅隸屬於此部門、不含下屬單位的實際正式編制人數)
 * @param totalHeadcount  總人數 (向上滾動加總後的總兵力，包含此部門自身以及轄下所有子孫部門的人數總和)
 * @param children        子部門視圖清單 (前端接收後以此屬性進行遞迴組件渲染，保證為 non-null 清單)
 */
public record DepartmentTreeNodeGottenResult(String tenantId, String id, String parentId, String code, String name,
		String status, int sortOrder, int depth, int directHeadcount, int totalHeadcount,
		List<DepartmentTreeNodeGottenResult> children) {
}