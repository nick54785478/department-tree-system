package com.example.demo.application.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.port.EventStorerPort;
import com.example.demo.application.port.ProjectionCleanupHandlerPort;
import com.example.demo.infra.event.dispatcher.ProjectionDispatcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Projection Rebuild Service (應用層 - 讀取端投影重建引擎)
 *
 * <pre>
 * 專責執行系統災難復原、讀取端結構大改版時的「全域事件重播與投影重建 (Global Event Replay)」。
 *
 * <b>極度重要架構設計警告：</b>
 * 本類別與方法 <b>絕對不可標註 `@Transactional`</b> 註解！
 * 
 * 理由：重播流程涉及全系統從古至今的成千上萬顆事件，若外層包裹了 Spring 的大交易，
 * 會迫使所有被重播更新的視圖資料表行鎖 (Row Lock) 鎖死在同一個 Transaction 內直到結束，這會瞬間撐爆記憶體並導致資料庫全面死鎖。
 * 因此，我們必須讓迴圈內部的每個事件分派，各自獨立去享用 Projector 身上宣告的 {@code @Transactional(propagation = Propagation.REQUIRES_NEW)} 獨立事務邊界。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectionRebuildCommandService {

	/**
	 * 注入絕對真理來源的事件倉儲 Port
	 */
	private final EventStorerPort eventStorerPort;

	/**
	 * 注入負責 TRUNCATE 清空視圖的基礎設施 Port
	 */
	private final ProjectionCleanupHandlerPort cleanupPort;

	/**
	 * 直接注入在 Configuration 中配置妥當的單一分派中心，與各個具體的 Projector 徹底解耦
	 */
	private final ProjectionDispatcher dispatcher;

	/**
	 * 執行全域唯讀端投影資料重建流程 (System Replay Magic)。
	 * <p>
	 * 將所有 Read Model 清空，並依據 Global Position 絕對時間順序重播歷史，重振讀取端江山。
	 * </p>
	 */
	public void rebuildAllProjections() {
		log.warn("STARTING FULL PROJECTION REBUILD");

		// 1. 物理清空：徹底抹除 department_views, department_tree 幾何閉包表，以及 processed_events 冪等表
		cleanupPort.truncateReadModels();
		log.info("Read models and idempotency tables truncated.");

		// 2. 載入歷史：從 Event Store 撈出跨越所有租戶、所有部門、從古至今全量排序的不可變真理事件流
		List<DomainEvent> allHistory = eventStorerPort.loadAllEventsOrderedByGlobalPosition();
		log.info("Loaded {} events from Event Store. Starting replay...", allHistory.size());

		// 3. 依序重播 (委派給分派中心)
		int count = 0;
		for (DomainEvent event : allHistory) {

			// 核心架構美學：不論未來業務擴充了 50 種還是 100 種新事件類型，
			// 這個全域重播引擎的迴圈核心永遠只有這一行代碼！完美的 OCP 體現。
			dispatcher.dispatch(event);

			count++;
			// 每處理 1000 顆事件列印一次計數日誌，避免日誌緩衝區溢位
			if (count % 1000 == 0) {
				log.info("Replayed {} events...", count);
			}
		}

		log.warn("✅ FULL PROJECTION REBUILD COMPLETED. Total replayed: {}", count);
	}
}