package com.example.demo.application.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.port.EventStorerPort;
import com.example.demo.application.shared.dto.DepartmentTemporalState;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Department Temporal Query Service (應用層 - 部門時光機查詢服務)
 *
 * <pre>
 * 本類別為純讀取服務 (Query Service)，是系統時光機機制的外部查詢門面。 
 * <b>架構設計意圖</b>： 為了不對現有的常規視圖表 (department_views) 造成壓力，本服務繞過 Read Model 的常規表， 
 * <b>直接與 Event Store 互動</b> ，在 Java 記憶體中動態還原出部門在歷史上任何一個指定時間點的絕對精確狀態。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 宣告全唯讀事務，通知底層連線池進行查詢優化
public class DepartmentTemporalQueryService {

	/**
	 * 注入事件儲存倉儲 Port 通道
	 */
	private final EventStorerPort eventStorerPort;

	/**
	 * 此處的 ObjectMapper 專用於此 Query Service 內部臨時對舊快照 Payload 進行解析降級
	 */
	private final ObjectMapper objectMapper;

	/**
	 * 設定全域統一的聚合類型標籤
	 */
	private static final String AGGREGATE_TYPE = "Department";

	// =========================================================
	// 1. 取得歷史軌跡 (Audit Log)
	// =========================================================

	/**
	 * 取得部門從古至今的所有異動事件 (完整歷史流)。
	 * <p>
	 * 通常用於系統審計、合規性檢查、或是組織架構變更履歷的 UI 展示。
	 * </p>
	 *
	 * @param tenantId     租戶識別碼
	 * @param departmentId 目標部門識別碼
	 * @return 依照發生時間與全域位置升序排列的領域事件列表
	 */
	public List<DomainEvent> getEventHistory(String tenantId, String departmentId) {
		return eventStorerPort.loadEvents(tenantId, AGGREGATE_TYPE, departmentId);
	}

	// =========================================================
	// 2. 時光機重建 (State Replay)
	// =========================================================

	/**
	 * 查詢部門在「目前」最新時刻的狀態 (純 Event Sourcing 記憶體重播版)。
	 * <p>
	 * 此方法不依賴快照，而是從白紙開始重播所有事件。通常用於與 Read Model 進行資料強一致性校驗的核對任務。
	 * </p>
	 *
	 * @param tenantId     租戶識別碼
	 * @param departmentId 目標部門識別碼
	 * @return 重建完畢的部門時光狀態物件
	 */
	public DepartmentTemporalState getCurrentState(String tenantId, String departmentId) {
		List<DomainEvent> events = getEventHistory(tenantId, departmentId);
		// 呼叫狀態物件的充血工廠方法，在記憶體內順序 Apply 還原狀態
		return DepartmentTemporalState.rebuild(events);
	}

	/**
	 * 智能歷史時光機查詢 (自動套用 Snapshot 優化機制)
	 * <p>
	 * 能夠在 $O(1)$ 級別的效能下，精準還原出部門在過去歷史某一精確時刻（{@code upTo}）的組織與人員狀態。
	 * </p>
	 *
	 * @param tenantId     租戶識別碼
	 * @param departmentId 目標部門識別碼
	 * @param upTo         時光截止點 (還原結果包含此精確時刻前發生的變更)
	 * @return 該歷史時刻的部門狀態；若該時刻部門尚未出生則回傳 {@code null}
	 */
	public DepartmentTemporalState getStateAt(String tenantId, String departmentId, Instant upTo) {

		DepartmentTemporalState state;
		Long startVersion = 0L;

		// 1. 嘗試尋找目標歷史時間點前的最新一筆有效快照存檔
		var snapshotOpt = eventStorerPort.loadLatestSnapshotBefore(tenantId, AGGREGATE_TYPE, departmentId, upTo);

		if (snapshotOpt.isPresent()) {
			try {
				// 從歷史快照的 JSON Payload 中直接還原為該時刻的基礎狀態
				state = objectMapper.readValue(snapshotOpt.get().payload(), DepartmentTemporalState.class);
				// 將重播起點的版本號拉高至快照版本
				startVersion = snapshotOpt.get().version();
				log.debug("Loaded snapshot at version {}", startVersion);
			} catch (Exception e) {
				// 容錯防禦：若快照反序列化因任何意外失敗，降級為全量事件重播，確保時光機不當機
				log.error("Failed to deserialize snapshot, falling back to full replay", e);
				state = new DepartmentTemporalState();
			}
		} else {
			// 盤古開天情境：歷史上在此時間前沒有任何快照存檔，從白紙狀態物件開始重播
			state = new DepartmentTemporalState();
		}

		// 2. 補齊差額：僅撈取「快照版本之後 ~ 目標指定歷史時間點」這段期間的歷史差異事件 (Delta Events)
		List<DomainEvent> deltaEvents = eventStorerPort.loadEventsBetween(tenantId, AGGREGATE_TYPE, departmentId,
				startVersion, upTo);

		// 不存在判定：若沒有快照基底，且在指定區間內也沒有任何歷史事件，代表該時刻此部門在系統中根本還不存在
		if (deltaEvents.isEmpty() && startVersion == 0L) {
			return null;
		}

		// 3. 套用差異事件：將落後的歷史進度重播補齊
		for (DomainEvent event : deltaEvents) {
			state.apply(event);
		}

		// 回傳歷史當下的精確業務狀態
		return state;
	}
}