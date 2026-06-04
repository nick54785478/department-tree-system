package com.example.demo.iface.schedule;

import java.time.Duration;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.port.DistributedLockManagerPort;
import com.example.demo.infra.event.registry.DomainEventRegistry;
import com.example.demo.infra.outbox.OutboxEvent;
import com.example.demo.infra.outbox.vo.OutboxStatus;
import com.example.demo.infra.persistence.OutboxEventPersistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 發件匣事件處理器 (Outbox Event Processor)
 *
 * <pre>
 * <b>核心職責</b>：作為背景排程任務 (Poller)，定期掃描 outbox_events 表中狀態為 PENDING 的領域事件， 並將其反序列化後，派發給
 * Spring 應用程式事件匯流排 (ApplicationEventPublisher)。 
 * 
 * <b>架構亮點 (保證遞交 Exactly-Once / At-Least-Once)</b>： 
 * 1. 叢集安全：透過 DistributedLockManagerPort 確保多個 Pod 同時運作時，只會有一個節點在撈取資料。 
 * 2. 邊界精準：嚴格遵守「鎖包覆交易 (Lock surrounds Transaction)」原則，避免 Transaction 還沒 Commit 就解鎖導致的幻讀問題。
 * </pre>
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OutboxEventProcessor {

	private final OutboxEventPersistence persistence;
	private final ApplicationEventPublisher publisher;
	private final ObjectMapper objectMapper;
	private final DomainEventRegistry eventRegistry;
	private final DistributedLockManagerPort distributedLockManager;
	private final TransactionTemplate transactionTemplate;

	// 分散式鎖的唯一識別碼
	private static final String LOCK_KEY = "OUTBOX_PROCESSOR_LOCK";

	/**
	 * 定期執行發件匣掃描 (預設每 3 秒執行一次)
	 */
	@Scheduled(fixedDelay = 3000)
	public void process() {
		log.trace("OutboxEventProcessor checking for pending events...");

		// 💡 1. 範圍最大 (Outer Scope)：取得分散式鎖
		// 確保同一時間叢集中只有一台機器能進入這段邏輯。
		// 設定 2 分鐘的超時時間 (Lock Duration)，防止這台機器中途崩潰 (Crash) 導致死鎖。
		distributedLockManager.executeWithLock(LOCK_KEY, Duration.ofMinutes(2), () -> {

			log.debug("Distributed lock [{}] acquired, starting event processing.", LOCK_KEY);

			// 💡 2. 範圍居中 (Inner Scope)：開啟資料庫交易 (Database Transaction)
			// 使用 TransactionTemplate 而非 @Transactional，是為了精準控制交易的啟動與提交時機。
			transactionTemplate.executeWithoutResult(status -> {

				// 撈取最舊的 100 筆待處理事件 (批次處理防 OOM)
				List<OutboxEvent> events = persistence.findTop100ByStatusOrderByOccurredAt(OutboxStatus.PENDING);
				if (events.isEmpty()) {
					return;
				}

				log.info("Found {} pending outbox events to process.", events.size());

				for (OutboxEvent outbox : events) {
					try {
						// 將 JSON Payload 反序列化為具體的 DomainEvent 子類別
						Object event = deserialize(outbox);
						log.debug("Publishing event: {}", event.getClass().getSimpleName());

						// 💡 3. 發布事件 (Publish)
						// 由於此時我們正處於 Transaction 內部，當我們呼叫 publishEvent 時，
						// 那些帶有 @TransactionalEventListener(phase = AFTER_COMMIT) 的下游監聽器 (如 View
						// Projection)
						// 會成功把任務「掛載」到這個 Transaction 的 Commit 鉤子上，等待最後一刻執行。
						publisher.publishEvent(event);

						// 標記為已處理並更新資料庫
						outbox.markProcessed();
						persistence.save(outbox);

					} catch (Exception e) {
						// 💡 隔離錯誤：單一事件處理失敗不該導致整批 100 筆資料 Rollback。
						// 標記為 FAILED 後繼續處理下一筆，交由後續的重試機制或人工介入處理。
						log.error("Failed to process outbox event id: {}. Marking as FAILED.", outbox.getId(), e);
						outbox.markFailed();
						persistence.save(outbox);
					}
				}

			}); // ⬅️ Inner Scope 結束：資料庫交易在這裡安全 Commit，接著下游的 AFTER_COMMIT Listener 會在此刻被喚醒執行！

		}); // ⬅️ Outer Scope 結束：交易與下游監聽器都確定執行完畢後，這裡才釋放分散式鎖 (絕對安全)
	}

	/**
	 * 將 Outbox 內的 JSON Payload 反序列化為真實的領域事件物件
	 */
	private Object deserialize(OutboxEvent outbox) throws Exception {
		// 透過啟動時自動掃描建立的 Registry，利用 eventType 字串反查真正的 Java Class
		Class<? extends DomainEvent> targetClass = eventRegistry.getType(outbox.getEventType())
				.orElseThrow(() -> new IllegalStateException("Unknown event type: " + outbox.getEventType()));

		return objectMapper.readValue(outbox.getPayload(), targetClass);
	}
}