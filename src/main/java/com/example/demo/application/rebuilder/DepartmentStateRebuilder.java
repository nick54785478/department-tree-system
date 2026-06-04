package com.example.demo.application.rebuilder;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.port.EventStorerPort;
import com.example.demo.application.port.SnapshotSerializerPort;
import com.example.demo.application.shared.dto.DepartmentTemporalState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Department State Rebuilder (讀取端/時光機 - 部門狀態重建引擎)
 *
 * <pre>
 * 專門負責時光溯源演算法的核心無狀態 (Stateless) 元件。
 *
 * <b>演算法精髓</b>：
 * 【先找快照，再補差額】 (Snapshot-Based Event Rehydration) 如果每次時光查詢都要從盤古開天時期的第 1 顆事件開始重播，系統將隨著時間演進而逐漸崩潰。 
 * 
 * 本重構引擎透過以下三段式演算法，將還原狀態的時間複雜度從 $O(N)$ 優化至趨近 $O(1)$： 
 * 1. 尋找指定歷史時間點之前，最新的一份有效快照存檔。 
 * 2. 若有快照，反序列化作為基底狀態，並將版本號設定為 startVersion。 
 * 3. 僅撈取高於 startVersion 且低於目標時間點之間的「差異事件 (Delta Events)」，逐一 apply 還原最終狀態。
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepartmentStateRebuilder {

	private final EventStorerPort eventStorer;
	private final SnapshotSerializerPort snapshotSerializer;

	/**
	 * 聚合根類型定義，與寫入端保持絕對一致的名稱對齊
	 */
	private static final String AGGREGATE_TYPE = "Department";

	/**
	 * 重建並還原特定部門聚合根在「歷史任意時間點」的精確狀態。
	 *
	 * @param tenantId     租戶識別碼
	 * @param departmentId 目標部門的唯一識別碼
	 * @param upTo         時光機指定的截止時間點 (還原結果將包含此精確時間點之前的歷史變更)
	 * @return 還原後的部門歷史時光狀態物件；若該時間點該部門尚未出生，則回傳 {@code null}
	 */
	public DepartmentTemporalState rebuildStateAt(String tenantId, String departmentId, Instant upTo) {
		DepartmentTemporalState state;
		Long startVersion = 0L;

		// 1. 嘗試尋找目標時間點之前的最新一份快照
		var snapshotOpt = eventStorer.loadLatestSnapshotBefore(tenantId, AGGREGATE_TYPE, departmentId, upTo);

		if (snapshotOpt.isPresent()) {
			try {
				// 完美運用 Port 進行解耦，Adapter 內部隱藏了 ObjectMapper 反序列化的底層細節
				state = snapshotSerializer.deserialize(snapshotOpt.get().payload());

				// 將重新 Rehydrate (回水) 後的起點版本號拉高到快照版本
				startVersion = snapshotOpt.get().version();
				log.debug("Loaded snapshot for {} at version {}", departmentId, startVersion);
			} catch (Exception e) {
				// 容錯與降級機制 (Graceful Degradation)：
				// 若快照因結構演進 (Schema Evolution) 導致反序列化慘遭失敗，
				// 立即啟動安全安全防護網：降級為「全量重播策略」——從第 0 版事件開始重播，確保系統絕對不停機。
				log.error("Failed to deserialize snapshot, falling back to full replay", e);
				state = new DepartmentTemporalState();
			}
		} else {
			// 盤古開天情境：沒有快照，初始化一個乾淨的空狀態物件，準備從第 0 版事件開始全量重播
			state = new DepartmentTemporalState();
		}

		// 2. 精準撈取「快照版本之後」且在「目標時間點之前」的歷史差異事件 (Delta Events)
		List<DomainEvent> deltaEvents = eventStorer.loadEventsBetween(tenantId, AGGREGATE_TYPE, departmentId,
				startVersion, upTo);

		// 孤兒/不存在判定：如果系統找不到快照，同時在該區間內也完全沒有任何事件紀錄，代表該部門在該歷史時間點「尚未出生」
		if (deltaEvents.isEmpty() && startVersion == 0L) {
			return null;
		}

		// 3. 狀態還原 (State Rehydration)：逐一將差異事件套用 (Apply) 進狀態物件中，補齊剩餘進度
		for (DomainEvent event : deltaEvents) {
			state.apply(event); // 觸發各事件專屬的狀態修改邏輯
		}

		// 回傳還原至該歷史時刻、最具 business-accuracy 的部門狀態
		return state;
	}
}