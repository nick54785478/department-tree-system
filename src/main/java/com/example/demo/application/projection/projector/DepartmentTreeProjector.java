package com.example.demo.application.projection.projector;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.port.IdempotencyHandlerPort;
import com.example.demo.application.projection.strategy.tree.TreeProjectionStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * Department Tree Projector (讀取端 - 樹狀結構投影機 / 策略分派中心)
 *
 * <pre>
 * 專責處理「部門幾何結構異動」的領域事件，並維護 department_tree 閉包表 (Closure Table)。 
 * 
 * <b>架構設計亮點</b>： 
 * 1. 統一的防護網：將交易邊界 (Transaction REQUIRES_NEW) 與冪等性防護 (Idempotency) 集中於此，避免重複代碼。 
 * 2. 策略工廠模式 (Strategy Factory)：利用 Spring 容器自動收集所有 {@link TreeProjectionStrategy} 的實作，
 * 動態註冊進 Map 中。未來新增任何部門事件，本類別完全不需要修改 (完美符合 OCP 開閉原則)。
 * </pre>
 */
@Slf4j
@Component
public class DepartmentTreeProjector {

	private final IdempotencyHandlerPort idempotencyPort;

	/**
	 * 策略路由表：Key 為事件的 Class 型別，Value 為具體的執行策略
	 */
	private final Map<Class<? extends DomainEvent>, TreeProjectionStrategy> strategyMap;

	/**
	 * 樹狀投影專屬的冪等性 Key 前綴，防止與其他 Projector 發生鎖定衝突
	 */
	private static final String IDEMPOTENCY_PREFIX = "TREE_PROJECTION_";

	/**
	 * 透過建構子注入，Spring 容器啟動時會自動收集所有實作 TreeProjectionStrategy 的 Bean
	 */
	public DepartmentTreeProjector(IdempotencyHandlerPort idempotencyPort, List<TreeProjectionStrategy> strategies) {
		this.idempotencyPort = idempotencyPort;

		// 將 List 轉為以 Event Class 為 Key 的 Map，提供 O(1) 的超高速路由
		this.strategyMap = strategies.stream()
				.collect(Collectors.toMap(TreeProjectionStrategy::supportedEvent, s -> s));
	}

	// =========================================================
	// 統一處理入口 (Single Entry Point)
	// =========================================================

	/**
	 * 接收事件並分派給對應的策略執行。
	 * <p>
	 * 開啟獨立交易邊界 (REQUIRES_NEW)，確保投影失敗不會導致寫入端 (Command Side) 的業務資料 Rollback。
	 * </p>
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void project(DomainEvent event) {

		// 1. 策略路由 (Strategy Routing)
		TreeProjectionStrategy strategy = strategyMap.get(event.getClass());
		if (strategy == null) {
			// 優雅降級：遇到不支援的事件 (如 DepartmentRenamedEvent，不影響樹狀結構)，直接放行略過
			return;
		}

		// 2. 共用的分散式冪等性防護 (Idempotency Guard)
		// 防止 Message Broker 的重試機制導致閉包表被重複寫入
		if (!idempotencyPort.tryProcess(IDEMPOTENCY_PREFIX + event.getEventId())) {
			log.trace("Tree Projector: Event {} already processed. Skipping.", event.getEventId());
			return;
		}

		// 3. 委派執行 (Delegation)
		// 壓制 unchecked 警告，因為我們在 strategyMap 放入時已確保了 Event 與 Strategy 的型別匹配
		strategy.execute(event);

		log.debug("Tree Projector: Executed strategy for node {} via {}", event.aggregateId(), event.eventType());
	}
}