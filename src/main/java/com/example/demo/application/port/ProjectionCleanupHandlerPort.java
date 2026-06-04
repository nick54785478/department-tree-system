package com.example.demo.application.port;

/**
 * Projection Cleanup Port (讀取端 - 投影資料清理合約)
 *
 * <pre>
 * 提供瞬間物理清空讀取端視圖 (Read Model) 與重建防護機制的合約。
 * 
 * 警告：這是一組極具破壞性的操作，主要用於開發測試環境的重置，
 * 或是正式環境在執行「全域事件重播 (Global Event Replay)」前，用來抹除舊有投影視圖的基礎準備步驟。
 * </pre>
 */
public interface ProjectionCleanupHandlerPort {

	/**
	 * 物理清空所有與 Read Model 相關的實體資料表。
	 * <p>
	 * 底層實作通常會利用關聯式資料庫的 {@code TRUNCATE TABLE} 指令， 以極高的效能清空如視圖表
	 * (department_views)、閉包表 (department_tree) 等查詢專用表， 並且同步重置 Idempotency (冪等性)
	 * 的紀錄表，讓 Projector 視為全新啟動狀態。
	 * </p>
	 */
	void truncateReadModels();
}