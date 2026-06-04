package com.example.demo.application.shared.view;

/**
 * Department Flat Node Gotten View (讀取端 - 扁平化單一節點視圖)
 *
 * <pre>
 * 專供前端表現層在「不需要展示階層結構」的輕量場景下使用。
 *
 * <b>核心應用場景</b>： 
 * 1. 導覽路徑：麵包屑導覽列 (Breadcrumb) 的歷史軌跡渲染。 
 * 2. 下拉組件：全域打平的部門下拉式選擇清單 (Dropdown / Select Items)。
 * 3. 高效搜尋：全域關鍵字模糊搜尋結果的快速條列呈現。
 *
 * <b>效能優化直覺</b>： 為保持高度的傳輸輕量化與高速回應，此視圖<b>不包含</b>子節點清單 (children) 與人數統計欄位， 避免了不必要的網路 I/O
 * 負擔。
 * </pre>
 *
 * @param tenantId  租戶識別碼
 * @param id        部門唯一識別碼
 * @param parentId  父部門唯一識別碼
 * @param code      部門業務代碼
 * @param name      部門顯示名稱
 * @param status    部門狀態
 * @param sortOrder 排序權重
 * @param depth     節點絕對深度 (特別用於麵包屑組件中，讓前端能依此數值進行正確的由左至右順序排列)
 */
public record DepartmentFlatNodeGottenView(String tenantId, String id, String parentId, String code, String name,
		String status, int sortOrder, int depth) {
}