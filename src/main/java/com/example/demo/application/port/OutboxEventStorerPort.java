package com.example.demo.application.port;

/**
 * Outbox Event Storer Port (基礎設施 - 發件匣模式合約)
 *
 * <pre>
 * 定義將領域事件存入「發件匣表 (Outbox Table)」的持久化合約。 
 * 實踐 Transactional Outbox Pattern 的核心介面。 
 * 
 * 確保「寫入業務資料庫 (Aggregate State)」與「寫入事件紀錄 (Event Payload)」這兩個動作，
 * 能夠完美綁定在同一個關聯式資料庫的 Local Transaction 內 Commit。 
 * 藉此徹底消滅跨系統呼叫所產生的雙寫不一致， 達成 100% Guaranteed Delivery (保證遞交) 給 Message Broker。
 * </pre>
 */
public interface OutboxEventStorerPort {

	/**
	 * 儲存準備非同步派發的事件至 Outbox 資料表。
	 *
	 * @param tenantId      租戶識別碼 (可用於未來 Kafka Topic 的分區路由 / Partition Routing)
	 * @param aggregateType 聚合根類型 (例如: "Department")
	 * @param aggregateId   聚合唯一識別碼
	 * @param event         具體的領域事件物件 (Adapter 底層通常會將其序列化為 JSON Payload 存入文字欄位)
	 */
	void store(String tenantId, String aggregateType, String aggregateId, Object event);

}