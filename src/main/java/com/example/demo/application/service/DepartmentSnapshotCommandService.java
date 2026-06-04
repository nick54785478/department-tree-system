package com.example.demo.application.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.port.EventStorerPort;
import com.example.demo.application.port.SnapshotSerializerPort;
import com.example.demo.application.rebuilder.DepartmentStateRebuilder;
import com.example.demo.application.shared.dto.DepartmentTemporalState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Department Snapshot Command Service (建立快照應用服務)
 *
 * <pre>
 * 專責處理與調度部門聚合根狀態快照 (Snapshot) 建立的使用案例 (Use Case)。
 *
 * <b>架構設計意圖</b>： 在事件溯源 (Event Sourcing) 架構中，隨著時間推移，單一聚合根累積的事件量可能會破百甚至破千。
 * 本服務的職責就是定期被觸發（通常由 EventStoreHandler 的閾值機制觸發），將當前的最新狀態存成「歷史存檔點」， 藉此大幅縮短未來時光機重播時的 I/O 成本。
 *
 * <b>潔淨架構約定</b>： 為了保持 Application Layer 的純淨性，{@code @Async} 非同步執行註解已被刻意移出本層，
 * 建議標示在呼叫此方法的 Infrastructure 層 (例如背景執行緒派發中心 SnapshotAsyncDispatcher)，確保應用服務專注於業務調度本身。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentSnapshotCommandService {

	/**
	 * 注入負責還原歷史狀態的核心引擎
	 */
	private final DepartmentStateRebuilder stateRebuilder;

	/**
	 * 注入事件與快照持久化通道
	 */
	private final EventStorerPort eventStorer;

	/**
	 * 注入隔離特定 JSON 框架的序列化轉接器
	 */
	private final SnapshotSerializerPort snapshotSerializer;

	/**
	 * 定義此快照服務專屬的聚合根類型標籤
	 */
	private static final String AGGREGATE_TYPE = "Department";

	/**
	 * 執行快照建立流程。
	 * <p>
	 * 透過與當前 Request 分離的獨立 Transaction，將特定時間點重建出的充血狀態物件轉為 JSON 快照存檔。
	 * </p>
	 *
	 * @param tenantId    租戶識別碼，落實租戶資料隔離防護
	 * @param aggregateId 欲建立快照的部門聚合根唯一識別碼
	 * @param occurredAt  快照建立的時間基準點
	 */
	@Transactional
	public void execute(String tenantId, String aggregateId, Instant occurredAt) {
		try {
			log.info("[Snapshot Use Case] Triggered snapshot creation for Department: {}", aggregateId);

			// 1. 調用核心狀態還原引擎，重建出該時間點最精確的最新狀態 (充血領域模型狀態)
			DepartmentTemporalState currentState = stateRebuilder.rebuildStateAt(tenantId, aggregateId, occurredAt);
			if (currentState == null) {
				log.warn("[Snapshot Use Case] No state found for {}, aborting.", aggregateId);
				return;
			}

			// 2. 透過 Port 取得該聚合根截至目前為止的最新事件總序號 (以此全域序號作為快照的版本號，用於未來補差額判定)
			long currentVersion = eventStorer.countEvents(tenantId, AGGREGATE_TYPE, aggregateId);

			// 3. 透過 Port 序列化為文字 Payload，嚴格防禦 Jackson 註解外溢污染應用層
			String payload = snapshotSerializer.serialize(currentState);

			// 4. 透過 Port 存入歷史快照表，完成存檔動作
			eventStorer.saveSnapshot(tenantId, AGGREGATE_TYPE, aggregateId, currentVersion, occurredAt, payload);

			log.info("[Snapshot Use Case] Successfully saved snapshot for {} at version {}", aggregateId,
					currentVersion);

		} catch (Exception e) {
			// 防禦性編程：快照屬於效能優化手段 (Non-functional requirement)，其失敗絕對不能阻塞或回滾主业务 Transaction
			log.error("💥 [Snapshot Use Case] Failed to create snapshot for Department: " + aggregateId, e);
		}
	}
}