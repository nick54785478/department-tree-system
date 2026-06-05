package com.example.demo.infra.adapter;

import org.springframework.stereotype.Component;

import com.example.demo.application.port.OutboxEventStorerPort;
import com.example.demo.infra.outbox.OutboxEvent;
import com.example.demo.infra.persistence.OutboxEventPersistence;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Outbox Event Storer Adapter (Infrastructure Layer)
 *
 * <pre>
 * 將領域事件轉化為 Outbox (發件匣) 實體並寫入資料庫的具體實作。
 * 
 * <strong>架構約定</strong>： 此方法通常配合 @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT) 使用。 
 * 確保「領域事件 Payload 寫入 Outbox 表」與「Aggregate 狀態變更」在同一個資料庫 Transaction 中完成， 
 * 達成完美的 Local Transaction 保證遞交 (Guaranteed Delivery) 基礎。
 * </pre>
 */
@Component
@RequiredArgsConstructor
class OutboxEventStorerAdapter implements OutboxEventStorerPort {

	private final ObjectMapper objectMapper;
	private final OutboxEventPersistence repository;

	@Override
	public void store(String tenantId, String aggregateType, String aggregateId, Object event) {
		try {
			// 1. 將整個事件序列化為 JSON 字串 Payload
			String payload = objectMapper.writeValueAsString(event);

			// 2. 封裝為 OutboxEvent 持久化實體
			OutboxEvent outbox = OutboxEvent.create(tenantId, aggregateType, aggregateId,
					event.getClass().getSimpleName(), payload);

			// 3. 儲存至資料庫
			repository.save(outbox);

		} catch (Exception e) {
			// 致命錯誤：序列化失敗必須拋出 RuntimeException！
			// 這樣才能觸發 Spring 的 Transaction Rollback，防止業務資料寫入了，事件卻丟失的雙寫不一致災難。
			throw new RuntimeException("Failed to serialize and store outbox event for ID: " + aggregateId, e);
		}
	}
}