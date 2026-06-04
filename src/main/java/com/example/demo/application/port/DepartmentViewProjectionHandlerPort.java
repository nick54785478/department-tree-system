package com.example.demo.application.port;

/**
 * Department View Projection Port (寫入端 - 扁平視圖維護合約)
 *
 * <pre>
 * 專責維護 department_views 扁平視圖表的資料寫入、欄位更新與狀態同步。 💡 本介面與幾何結構的 Tree Projection
 * 完全解耦。它不關心複雜的樹狀路徑計算， 只專注於單一部門基本資料（名稱、代碼、排序）與多維度生命週期狀態的即時更新。
 * </pre>
 */
public interface DepartmentViewProjectionHandlerPort {

	/**
	 * 新增一筆讀取端的部門扁平視圖資料。
	 * <p>
	 * 初始化建立時，基於效能考量，該部門的直屬人數 (direct_headcount) 與總人數 (total_headcount) 皆預設為 0，
	 * 後續由人員分配事件 (EmployeeAssigned) 驅動異步滾動計數。
	 * </p>
	 *
	 * @param tenantId  租戶識別碼
	 * @param id        部門唯一識別碼
	 * @param parentId  父部門識別碼 (若為一級部門則傳入 null)
	 * @param code      部門業務代碼 (如 "HR-001")
	 * @param name      部門顯示名稱
	 * @param status    部門初始狀態 (預設通常為 "ACTIVE")
	 * @param sortOrder 前端 UI 顯示的排序權重
	 */
	void insertDepartmentView(String tenantId, String id, String parentId, String code, String name, String status,
			int sortOrder);

	/**
	 * 更新視圖表中的父部門 ID 指向。
	 * <p>
	 * 當部門執行移動 (Move) 行為時被呼叫，用以同步維護扁平視圖表中的直接上下級從屬關係。
	 * </p>
	 *
	 * @param tenantId    租戶識別碼
	 * @param id          欲移動的部門唯一識別碼
	 * @param newParentId 新的直接父部門識別碼 (若提升為頂層部門則傳入 null)
	 */
	void updateDepartmentViewParent(String tenantId, String id, String newParentId);

	/**
	 * 移除或註銷該部門的視圖資料。
	 * <p>
	 * <strong>架構意圖與技術實作分离原則：</strong> 對於 Application Layer 而言，此方法的業務語意是「刪除該部門視圖」。
	 * 但在 Infrastructure Layer 的 Adapter 實作中，<strong>為了完美支援時光機復原 (Undelete/Restore)
	 * 機制，底層應採用邏輯刪除 (即執行 UPDATE 語法將 status 變更為 'DELETED')</strong>，而非物理移除該筆 Row 紀錄。
	 * </p>
	 *
	 * @param tenantId 租戶識別碼
	 * @param id       欲刪除的部門唯一識別碼
	 */
	void deleteDepartmentView(String tenantId, String id);

	/**
	 * 更新部門顯示名稱。
	 * <p>
	 * 當更名 (Rename) 事件發生時被非同步觸發，用以維持讀寫端資料的最終一致性。
	 * </p>
	 *
	 * @param tenantId 租戶識別碼
	 * @param id       部門唯一識別碼
	 * @param newName  精心驗證過的新部門名稱
	 */
	void updateDepartmentName(String tenantId, String id, String newName);

	/**
	 * 變更部門的生命週期業務狀態。
	 * <p>
	 * 當部門啟用、停用 (Disable) 或 <strong>從邏輯刪除中復活 (Restore)</strong> 時，皆透過此通道更新讀取端狀態，
	 * 傳入的字串通常對應寫入端領域模型的 {@code DepartmentStatus} 列舉名稱。
	 * </p>
	 *
	 * @param tenantId 租戶識別碼
	 * @param id       部門唯一識別碼
	 * @param status   目標更新狀態字串 (如 "ACTIVE", "DISABLED", "DELETED")
	 */
	void updateDepartmentStatus(String tenantId, String id, String status);

	/**
	 * 更新部門在當前層級下的前端 UI 顯示排序權重。
	 *
	 * @param tenantId  租戶識別碼
	 * @param id        部門唯一識別碼
	 * @param sortOrder 新的排序數值 (數值越小通常排在越前面)
	 */
	void updateDepartmentSortOrder(String tenantId, String id, int sortOrder);
}