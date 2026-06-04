package com.example.demo.infra.adapter;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.example.demo.application.port.IdempotencyHandlerPort;
import com.example.demo.infra.persistence.ProcessedEventPersistence;

import lombok.RequiredArgsConstructor;

/**
 * Idempotency Handler Adapter (Infrastructure Layer)
 *
 * <pre>
 * 基於關聯式資料庫 (RDBMS) 的冪等性防護實作。
 * 
 * <strong>技術選型</strong>：利用資料庫原生的 Unique Constraint 與 UPSERT (或 INSERT IGNORE)
 * 語法進行原子性操作。 這是在單一資料庫架構下，效能極佳且絕對不會產生 Race Condition 的防護方案。
 * </pre>
 */
@Component
@RequiredArgsConstructor
class IdempotencyHandlerAdapter implements IdempotencyHandlerPort {

	private final ProcessedEventPersistence persistence;

	/**
	 * 嘗試寫入處理紀錄以霸佔事件的處理權。
	 */
	@Override
	public boolean tryProcess(String eventId) {
		// 透過底層原生 SQL 嘗試寫入 (如 INSERT IGNORE)，若受影響的行數 > 0 代表成功搶到首次處理權
		int rows = persistence.tryInsertEvent(eventId, Instant.now());
		return rows > 0;
	}
}