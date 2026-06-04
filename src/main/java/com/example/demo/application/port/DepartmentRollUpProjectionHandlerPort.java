package com.example.demo.application.port;

/**
 * Department Roll-Up Projection Handler Port (讀取端 - 統計滾動加總合約)
 *
 * <pre>
 * 專責處理組織樹狀結構中統計指標 (如：部門人數) 的滾動加總 (Roll-up Aggregations)。 
 * 
 * <strong>架構意圖</strong>： 在 CQRS 與 DDD 架構中，為了避免高併發下的資料庫鎖競爭 (Lock Contention)， 
 * 聚合根 (Aggregate Root) 內部通常「不維護」跨節點的統計狀態 (例如總人數)。 
 * 
 * 統計資訊的維護全權交由本介面在讀取端 (Read Model) 接收到領域事件後進行同步或非同步的更新， 
 * 藉此達成寫入端的極致輕量化，同時滿足讀取端的高效檢視需求。
 * </pre>
 */
public interface DepartmentRollUpProjectionHandlerPort {

	/**
	 * 更新單一部門的「直屬人數」 (Direct Headcount)。
	 * <p>
	 * 當員工被分配到該部門，或從該部門移出時呼叫。
	 * </p>
	 *
	 * @param tenantId     租戶識別碼
	 * @param departmentId 目標部門的唯一識別碼
	 * @param delta        人數變化量 (增加傳入正數如 1，減少傳入負數如 -1)
	 */
	void incrementDirectHeadcount(String tenantId, String departmentId, int delta);

	/**
	 * 更新自身與所有直系祖先的「總人數」 (Total Headcount)。
	 * <p>
	 * 組織樹狀結構的核心統計方法。當底層子節點人數發生變化時， 該變化量必須「向上滾動 (Roll-up)」傳遞給所有的祖先節點 (包含自己)。
	 * </p>
	 *
	 * @param tenantId     租戶識別碼
	 * @param descendantId 發生人數變化的底層部門唯一識別碼
	 * @param delta        人數變化量 (增加傳入正數如 1，減少傳入負數如 -1)
	 */
	void incrementTotalHeadcountForAncestors(String tenantId, String descendantId, int delta);
}