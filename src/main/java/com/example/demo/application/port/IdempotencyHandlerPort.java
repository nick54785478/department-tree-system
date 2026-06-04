package com.example.demo.application.port;

/**
 * Idempotency Handler Port (基礎設施 - 冪等性防護合約)
 *
 * <pre>
 * 負責確保同一個操作或事件「絕對不會被重複處理」，落實 Exactly-Once (精確一次) 或至少是 Idempotent (冪等) 的業務語意。
 * 這是系統在面對網路延遲、分散式節點重啟、或 Message Broker (如 Kafka/RabbitMQ) 
 * 觸發 At-Least-Once 重試機制時，防堵重複消費的最後一道物理防線。
 * </pre>
 */
public interface IdempotencyHandlerPort {

	/**
	 * 嘗試登記事件的處理紀錄，並原子性地 (Atomically) 檢查是否已處理過。
	 * <p>
	 * 底層實作建議採用高併發安全的技術，如 Redis 的 SETNX、或是資料庫的 Unique Key Constraint。
	 * </p>
	 *
	 * @param eventId 領域事件的唯一識別碼 (通常是 UUID 或是結合 Tenant 的特製 Idempotency Key)
	 * @return {@code true} 代表這是第一次處理，已成功登記鎖定，允許放行業務邏輯； {@code false} 代表該事件已被處理過
	 *         (或正在處理中)，請呼叫端直接忽略並優雅結束。
	 */
	boolean tryProcess(String eventId);

}