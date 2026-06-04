package com.example.demo.infra.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.infra.event.sourcing.StoredEvent;

/**
 * Event Store Persistence (基礎設施層 - 時光機事件儲存操作介面)
 *
 * <pre>
 * 專為 RDBMS 模擬高階 Event Store 所打造的物理讀寫介面。 
 * 
 * <b>型別注意：</b> 
 * 主鍵 globalPosition 的型別為 {@link Long}。 內部所有的查詢皆強烈遵循 OrderByGlobalPositionAsc 規則，
 * 確保事件重播時的時間線絕對正確。
 * </pre>
 */
public interface EventStorePersistence extends JpaRepository<StoredEvent, Long> {

	/**
	 * 1. 取得單一聚合根從古至今的完整歷史事件流。
	 * <p>
	 * 核心場景：用於寫入端或純溯源端在記憶體中完整重建 (Rehydrate) 聚合根的最新當前狀態。
	 * </p>
	 *
	 * @param tenantId      租戶識別碼
	 * @param aggregateType 聚合類型 (如 "Department")
	 * @param aggregateId   聚合唯一識別碼
	 * @return 按歷史發生順序升序排列的 StoredEvent 實體集合
	 */
	List<StoredEvent> findByTenantIdAndAggregateTypeIgnoreCaseAndAggregateIdOrderByGlobalPositionAsc(String tenantId,
			String aggregateType, String aggregateId);

	/**
	 * 2. 取得單一聚合根在指定歷史時間點之前的事件流。
	 * <p>
	 * 核心場景：時光機精確回溯查詢。將時間線截斷在 {@code upToTimestamp} 歷史瞬間，還原歷史真實斷面。
	 * </p>
	 *
	 * @param tenantId      租戶識別碼
	 * @param aggregateType 聚合類型
	 * @param aggregateId   聚合唯一識別碼
	 * @param upToTimestamp 時光截止截止時間戳記 (包含此時間點)
	 * @return 截至該時刻的升序歷史事件流
	 */
	List<StoredEvent> findByTenantIdAndAggregateTypeAndAggregateIdAndOccurredAtLessThanEqualOrderByGlobalPositionAsc(
			String tenantId, String aggregateType, String aggregateId, Instant upToTimestamp);

	/**
	 * 3. 取得全系統「所有」歷史事件流。
	 * <p>
	 * 核心場景：全域讀取端投影大重建 (Global Read Model Rebuild)。 透過 {@code GlobalPosition} (Auto
	 * Increment ID) 作為全域時鐘指標，保證重播順序的 100% 絕對先後防護。
	 * </p>
	 *
	 * @return 全系統唯一的不可變全量事件軌跡流
	 */
	List<StoredEvent> findAllByOrderByGlobalPositionAsc();

	/**
	 * 4. 取得在某個快照版本之後，且在特定時間點之前的「差異事件流 (Delta Events)」。
	 * <p>
	 * 核心場景：時光機優化重播。利用 {@code GlobalPositionGreaterThan} 完美跳過快照本身的舊事件，
	 * 只撈取快照存檔點之後到截止時間點之間的差額，將大聚合根的溯源成本壓縮至極致。
	 * </p>
	 *
	 * @param tenantId      租戶識別碼
	 * @param aggregateType 聚合類型
	 * @param aggregateId   聚合唯一識別碼
	 * @param fromVersion   快照所結算定格的事件版本號位置 (排除此版本)
	 * @param upToTimestamp 時光截止歷史時間點
	 * @return 區間內的歷史差異事件流
	 */
	List<StoredEvent> findByTenantIdAndAggregateTypeAndAggregateIdAndGlobalPositionGreaterThanAndOccurredAtLessThanEqualOrderByGlobalPositionAsc(
			String tenantId, String aggregateType, String aggregateId, Long fromVersion, Instant upToTimestamp);

	/**
	 * 5. 統計特定聚合根目前已累積的歷史事件總筆數。
	 * <p>
	 * 利用 Spring Data JPA 底層優化發出 {@code COUNT(*)} 查詢，極速回傳且不耗費任何記憶體，專供自動快照閾值判定使用。
	 * </p>
	 *
	 * @param tenantId      租戶識別碼
	 * @param aggregateType 聚合類型
	 * @param aggregateId   聚合唯一識別碼
	 * @return 累積的事件數量
	 */
	long countByTenantIdAndAggregateTypeAndAggregateId(String tenantId, String aggregateType, String aggregateId);
}