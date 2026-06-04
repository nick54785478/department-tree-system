package com.example.demo.iface.rest;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.service.DepartmentTemporalQueryService;
import com.example.demo.application.shared.dto.DepartmentTemporalState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Department Temporal Controller (基礎設施層 - 部門時光機查詢 API 控制器)
 *
 * <pre>
 * 專責處理所有關於「歷史事件軌跡查詢」與「任意時間點狀態還原」的時光機端 API 入口。 
 * 
 * <b>架構設計特點</b>： 
 * 本控制器屬於純粹的溯源查詢（Pure Event Sourcing Query），直接與事件商店服務互動。 前端可利用此處暴露的 API
 * 渲染出極具業務價值的變更時間軸（Timeline）介面，或是讓管理員一鍵穿梭至過去的某個特定時刻， 檢視當時的組織架構與人員編制狀態。
 * </pre>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/departments/{tenantId}/{id}")
public class DepartmentTemporalController {

	/**
	 * 注入時光機查詢核心應用服務
	 */
	private final DepartmentTemporalQueryService temporalService;

	// =========================================================
	// API 1: 取得事件稽核軌跡 (Audit Log)
	// =========================================================

	/**
	 * 取得部門從古至今的所有異動事件歷史流 (Audit Trail)。
	 * 
	 * <pre>
	 * <b>API 通道:</b> {@code GET /departments/{tenantId}/{id}/events} <br>
	 * <b>前端應用場景:</b> 可用於繪製「組織變更履歷操作紀錄 (Timeline)」介面，展示何時更名、何時移動、何時停用。
	 * </pre>
	 *
	 * @param tenantId 租戶識別碼
	 * @param id       部門唯一識別碼
	 * @return 該部門專屬的升序歷史領域事件列表
	 */
	@GetMapping("/events")
	public ResponseEntity<List<DomainEvent>> getEventHistory(@PathVariable String tenantId, @PathVariable String id) {
		// 架構美學提醒：得益於我們在基底 DomainEvent 加上了 Jackson 混入組態 (@JsonTypeInfo)，
		// Jackson 序列化時會自動在 JSON Payload 中帶入 "eventType": "DepartmentRenamedEvent"
		// 等多型標籤，
		// 前端接收後極好解包並對應成不同的時間軸 Icon 與客製化文本敘述！
		// 🌟 終極防禦：強制將 Path 變數轉為大寫，徹底抹平前端傳小寫導致 JPA 查不到資料的漏洞！
		List<DomainEvent> history = temporalService.getEventHistory(tenantId, id);
		return ResponseEntity.ok(history);
	}

	// =========================================================
	// API 2: 時光機歷史斷面查詢
	// =========================================================

	/**
	 * 查詢部門在「歷史指定時間點」定格的充血業務狀態。
	 * <p>
	 * <b>API 通道:</b>
	 * {@code GET /departments/{tenantId}/{id}/state/at?timestamp=2026-05-29T10:00:00Z}
	 * <br>
	 * <b>前端應用場景:</b> 傳入符合 ISO-8601 標準的時間字串，後端自動套用【歷史快照 + 差異事件】優化重播引擎，吐出該瞬間的精確斷面狀態。
	 * </p>
	 *
	 * @param tenantId  租戶識別碼
	 * @param id        部門唯一識別碼
	 * @param timestamp 歷史截止時間戳記（由 Spring 依據 ISO-8601 格式解包為 Instant 物件）
	 * @return {@code 200 OK} 夾帶該時刻狀態；若該歷史時刻前部門尚未建立，則回傳 {@code 404 Not Found}
	 */
	@GetMapping("/state/at")
	public ResponseEntity<DepartmentTemporalState> getStateAt(@PathVariable String tenantId, @PathVariable String id,
			@RequestParam("timestamp") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant timestamp) {
		// 調用智能重播引擎
		DepartmentTemporalState state = temporalService.getStateAt(tenantId, id, timestamp);

		if (state == null) {
			// 防禦網：如果在該歷史時間點之前，此部門在系統中根本還沒出生，優雅回傳 404
			return ResponseEntity.notFound().build();
		}

		return ResponseEntity.ok(state);
	}

	// =========================================================
	// API 3: 取得最新即時狀態
	// =========================================================

	/**
	 * 透過純事件溯源（Event Sourcing Replay），即時還原並取得該部門在當前時刻的最新狀態。
	 * <p>
	 * <b>API 通道:</b> {@code GET /departments/{tenantId}/{id}/state/current} <br>
	 * 通常用於技術檢測、強一致性審計、或是用來核對讀取端扁平視圖（Read Model View）是否因非同步延遲而產生微小落差。
	 * </p>
	 *
	 * @param tenantId 租戶識別碼
	 * @param id       部門唯一識別碼
	 * @return 當前最新狀態物件
	 */
	@GetMapping("/state/current")
	public ResponseEntity<DepartmentTemporalState> getCurrentState(@PathVariable String tenantId,
			@PathVariable String id) {
		DepartmentTemporalState state = temporalService.getCurrentState(tenantId, id);
		if (state == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(state);
	}
}