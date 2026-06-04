package com.example.demo.iface.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.demo.application.dispatcher.SnapshotAsyncDispatcher;
import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.port.EventStorerPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Event Store Handler (時光機 - 事件儲存與快照觸發攔截器)
 *
 * <pre>
 * 這是系統實踐 Event Sourcing (事件溯源) 的絕對核心元件，負責將所有的領域變更，轉化為不可變的歷史軌跡寫入 Event Store
 * 之中。 
 * 
 * <b>架構設計極大差異</b>： 注意此處使用的是 {@code @EventListener} 而非 {@code @TransactionalEventListener}。 
 * 這是為了保證「寫入業務資料表(若為混合式架構)」與「寫入歷史事件」綁定在同一個資料庫 Transaction 中。 
 * 如果事件寫入失敗，整個 Command 必須強制 Rollback，實現真正的強一致性 (Strong Consistency)。
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventStoreHandler {

	private final EventStorerPort eventStorerPort;

	// 注入的是 Infra 層的非同步任務分派器 (Async Dispatcher)，而不是直接注入 Command Service，
	// 避免快照產生的高 I/O 成本阻塞了當下的 API Request。
	private final SnapshotAsyncDispatcher snapshotDispatcher;

	/**
	 * 快照觸發門檻值：每累積 2 個新事件，即自動產生一份時光快照 (實務上通常設定為 50 或 100)
	 */
	private static final int SNAPSHOT_THRESHOLD = 2;

	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void handleDomainEvent(DomainEvent event) {
		log.info("[EventStore Handler] Intercepted event [{}] for Aggregate [{}]. Appending to EventStore...",
				event.eventType(), event.aggregateId());

		// 1. 寫入真理表 (Event Store)
		eventStorerPort.append(event);

		// 2. 補回遺失的快照觸發邏輯！
		// 統計目前該聚合根已經累積了幾顆事件
		long eventCount = eventStorerPort.countEvents(event.getTenantId(), event.aggregateType(), event.aggregateId());

		// 如果達到我們設定的門檻 (例如 2, 4, 6...)
		if (eventCount > 0 && eventCount % SNAPSHOT_THRESHOLD == 0) {
			log.info(
					"[EventStore Handler] Snapshot threshold reached ({}) for Aggregate [{}:{}]. Dispatching async snapshot task.",
					eventCount, event.aggregateType(), event.aggregateId());

			// 3. 呼叫 Dispatcher，把耗時的快照運算丟到背景非同步執行緒！
			snapshotDispatcher.dispatch(event.getTenantId(), event.aggregateId(), event.getOccurredAt());
		}
	}
}