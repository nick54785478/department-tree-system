package com.example.demo.application.projection.projector;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.port.IdempotencyHandlerPort;
import com.example.demo.application.projection.strategy.view.ViewProjectionStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * Department View Projector (讀取端 - 視圖投影機 / 策略分派中心)
 *
 * <pre>
 * 專責處理「部門基本資料與狀態異動」的領域事件，並維護 department_views 扁平視圖表。 
 * 
 * <b>架構設計亮點：</b> 
 * 1. 職責解耦：與 TreeProjector 徹底分離。即使樹狀幾何投影失敗，視圖的基本資料依然能獨立更新，反之亦然。 
 * 2. 統一的防護網：將獨立交易邊界 (Transaction REQUIRES_NEW) 與分散式冪等性防護 (Idempotency) 集中於此。 
 * 3. 動態路由：利用 Spring 容器自動裝配機制，實現 O(1) 的策略路由分派。
 * </pre>
 */
@Slf4j
@Component
public class DepartmentViewProjector {

	private final IdempotencyHandlerPort idempotencyPort;

	/**
	 * 策略路由表：提供 O(1) 的超高速分派效能
	 */
	private final Map<Class<? extends DomainEvent>, ViewProjectionStrategy> strategyMap;

	/**
	 * 視圖投影專屬的冪等性 Key 前綴，防止與 TreeProjector 發生鎖定衝突
	 */
	private static final String IDEMPOTENCY_PREFIX = "VIEW_PROJECTION_";

	/**
	 * Spring IoC 魔法：自動收集所有實作 ViewProjectionStrategy 介面的 Bean
	 */
	public DepartmentViewProjector(IdempotencyHandlerPort idempotencyPort, List<ViewProjectionStrategy> strategies) {
		this.idempotencyPort = idempotencyPort;

		// 將 List 轉換成以 Event Class 為 Key 的 Map
		this.strategyMap = strategies.stream()
				.collect(Collectors.toMap(ViewProjectionStrategy::supportedEvent, s -> s));
	}

	// =========================================================
	// 統一處理入口 (Single Entry Point)
	// =========================================================

	/**
	 * 接收事件並分派給對應的視圖更新策略。
	 * <p>
	 * 開啟獨立交易邊界 (REQUIRES_NEW)，確保投影失敗不會導致寫入端 (Command Side) 的業務資料 Rollback。
	 * </p>
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void project(DomainEvent event) {

		// 1. 取得對應的策略
		ViewProjectionStrategy strategy = strategyMap.get(event.getClass());
		if (strategy == null) {
			// 優雅降級：若該事件不需要更新視圖 (例如純粹的幾何移動而未改名)，直接略過
			return;
		}

		// 2. 執行冪等性防護 (Idempotency Guard)
		if (!idempotencyPort.tryProcess(IDEMPOTENCY_PREFIX + event.getEventId())) {
			log.trace("View Projector: Event {} already processed. Skipping.", event.getEventId());
			return;
		}

		// 3. 委派執行實質的資料庫寫入
		strategy.execute(event);

		log.debug("View Projector: Executed strategy for node {} via {}", event.aggregateId(), event.eventType());
	}
}