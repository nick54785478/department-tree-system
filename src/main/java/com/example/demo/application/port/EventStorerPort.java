package com.example.demo.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.shared.dto.SnapshotData;

/**
 * Event Store Port (寫入端 - 事件儲存與時光機合約)
 *
 * <pre>
 * 定義系統最高層級的「絕對真相來源 (Single Source of Truth)」，採用 Append-Only (僅限附加) 的不可變資料流設計，
 * 所有的業務變更都必須化為事件儲存於此。 
 * 
 * 架構意圖：呼叫端不需知曉底層是用關聯式資料庫 (RDBMS)、MongoDB 還是原生的 EventStoreDB 實作。 
 * 提供強大的時光倒流 (Time-Travel) 與快照 (Snapshot) 查詢能力。
 * </pre>
 */
public interface EventStorerPort {

	/**
	 * 將新產生的領域事件附加 (Append) 到該聚合根的專屬事件流中。
	 * <p>
	 * 底層實作必須保證事件寫入的原子性，並處理併發寫入時的樂觀鎖 (Optimistic Locking) 衝突。
	 * </p>
	 *
	 * @param event 繼承自 DomainEvent 的具體領域事件
	 */
	void append(DomainEvent event);

	/**
	 * 載入特定聚合根從古至今的「所有」事件。
	 * <p>
	 * 主要用於記憶體中完整重建 (Rehydrate) 聚合根的當前最新狀態。
	 * </p>
	 *
	 * @param tenantId      租戶識別碼
	 * @param aggregateType 聚合根類型 (例如: "Department")
	 * @param aggregateId   聚合唯一識別碼
	 * @return 依發生時間先後排序的領域事件列表
	 */
	List<DomainEvent> loadEvents(String tenantId, String aggregateType, String aggregateId);

	/**
	 * 時光機查詢核心 API : 載入該聚合根在「指定歷史時間點之前」發生的所有事件。
	 * <p>
	 * 用於還原過去某一刻的業務狀態，執行審計 (Audit) 或是撤銷/復原 (Undo/Redo) 邏輯。
	 * </p>
	 *
	 * @param tenantId      租戶識別碼
	 * @param aggregateType 聚合根類型
	 * @param aggregateId   聚合唯一識別碼
	 * @param upToTimestamp 時間截止點 (查詢結果將包含此精確時間點發生的事件)
	 * @return 該時間點之前的歷史事件列表
	 */
	List<DomainEvent> loadEventsUpTo(String tenantId, String aggregateType, String aggregateId, Instant upToTimestamp);

	/**
	 * 系統重建專用 API (System Replay) : 載入整個系統從古至今「所有」的跨聚合事件，並嚴格依照全域發生順序 (Global
	 * Position) 排列。
	 * <p>
	 * 警告：這是一個極度耗費資源的操作，僅限於災難復原、讀取端結構大改版， 或是啟動全新的 Projector 從頭建立唯讀視圖時使用。
	 * </p>
	 *
	 * @return 全域排序的領域事件流
	 */
	List<DomainEvent> loadAllEventsOrderedByGlobalPosition();

	/**
	 * 儲存聚合根狀態的二進位或文本快照 (Snapshot)。
	 * <p>
	 * 當聚合根事件數量過於龐大時，儲存快照可大幅縮短未來狀態重建的時間 (效能優化)。
	 * </p>
	 *
	 * @param tenantId      租戶識別碼
	 * @param aggregateType 聚合根類型
	 * @param aggregateId   聚合唯一識別碼
	 * @param version       該快照對應的事件版本號
	 * @param occurredAt    快照建立的基準時間
	 * @param payloadJson   序列化後的狀態資料 (通常為 JSON 格式)
	 */
	void saveSnapshot(String tenantId, String aggregateType, String aggregateId, Long version, Instant occurredAt,
			String payloadJson);

	/**
	 * 取得該聚合根在「指定時間點之前」的最新一份有效快照。
	 * <p>
	 * 時光機優化機制：先讀取此快照，再利用 {@link #loadEventsBetween} 補齊差異事件，
	 * 避免每次時光倒流都要從系統盤古開天時期開始重播。
	 * </p>
	 *
	 * @param tenantId      租戶識別碼
	 * @param aggregateType 聚合根類型
	 * @param aggregateId   聚合唯一識別碼
	 * @param upToTimestamp 時間截止點
	 * @return 封裝了快照資料的 Optional，若該期間無快照則回傳 empty
	 */
	Optional<SnapshotData> loadLatestSnapshotBefore(String tenantId, String aggregateType, String aggregateId,
			Instant upToTimestamp);

	/**
	 * 取得在某個版本之後，且在特定時間點之前的「差異事件 (Delta Events)」。
	 * <p>
	 * 專門搭配快照機制使用，只撈取快照產生之後到目標時間點之間所發生的事件。
	 * </p>
	 *
	 * @param tenantId      租戶識別碼
	 * @param aggregateType 聚合根類型
	 * @param aggregateId   聚合唯一識別碼
	 * @param fromVersion   起始版本號 (不包含此版本)
	 * @param upToTimestamp 時間截止點
	 * @return 差異領域事件列表
	 */
	List<DomainEvent> loadEventsBetween(String tenantId, String aggregateType, String aggregateId, Long fromVersion,
			Instant upToTimestamp);

	/**
	 * 查詢特定聚合根目前累積的事件總計數量。
	 * <p>
	 * 通常用來觸發快照機制的閾值判斷 (例如：每累積 100 個事件自動排程產生一次快照)。
	 * </p>
	 *
	 * @param tenantId      租戶識別碼
	 * @param aggregateType 聚合根類型
	 * @param aggregateId   聚合唯一識別碼
	 * @return 歷史事件總數
	 */
	long countEvents(String tenantId, String aggregateType, String aggregateId);
}