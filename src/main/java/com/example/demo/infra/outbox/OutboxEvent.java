package com.example.demo.infra.outbox;

import java.time.Instant;

import com.example.demo.infra.outbox.vo.OutboxStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * OutboxEvent (基礎設施層 - 發件匣事件實體)
 *
 * <pre>
 * 實踐「事務發件匣模式 (Transactional Outbox Pattern)」的物理資料表。 
 * 
 * <b>架構設計核心意圖</b>：
 * 解決分散式系統中的「雙寫問題 (Dual-Write Problem)」。
 * 
 * 當業務狀態變更時，事件 Payload 會與業務資料 鎖在同一個本地事務 (Local Transaction) 中寫入此表。
 * Commit 成功後，再由背景排程輪詢器 (Poller) 或是 CDC (Change Data Capture) 工具非同步地將狀態為 PENDING 的事件
 * 可靠地派發至外部訊息佇列 (如 Kafka, RabbitMQ)，達成 100% 的保證遞交 (Guaranteed Delivery) 與最終一致性。
 * </pre>
 */
@Getter
@Entity
@Table(name = "outbox_events", indexes = {
		// 核心優化索引：專為背景輪詢執行緒設計，快速掃描特定租戶下待處理的歷史事件流，避免全表掃描
		@Index(name = "idx_outbox_tenant_status", columnList = "tenant_id, status, occurred_at") })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

	/**
	 * 自增主鍵，用於標記發件匣記錄的物理順序
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * 租戶識別碼。便於未來訊息佇列 (Message Broker) 依據 Tenant 進行 Topic 分流或分區路由 (Partition
	 * Routing)
	 */
	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	/**
	 * 聚合根類型 (例如: "Department")，供消費者識別業務邊界
	 */
	@Column(name = "aggregate_type", nullable = false, length = 100)
	private String aggregateType;

	/**
	 * 聚合根唯一識別碼
	 */
	@Column(name = "aggregate_id", nullable = false, length = 50)
	private String aggregateId;

	/**
	 * 領域事件具體類別名稱 (例如: "DepartmentCreatedEvent")，反序列化時的重要指引標籤
	 */
	@Column(name = "event_type", nullable = false, length = 200)
	private String eventType;

	/**
	 * 序列化後的 JSON 文本載體
	 */
	@Lob
	@Column(name = "payload", nullable = false)
	private String payload;

	/**
	 * 發件匣處理狀態 (PENDING, PROCESSED, FAILED)
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private OutboxStatus status;

	/**
	 * 事件發生時間戳記
	 */
	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	/**
	 * 非同步派發成功的時間戳記 (若尚未派發則為 null)
	 */
	@Column(name = "processed_at")
	private Instant processedAt;

	/**
	 * 工廠方法：建立一筆初始狀態為 PENDING 的發件匣事件。
	 */
	public static OutboxEvent create(String tenantId, String aggregateType, String aggregateId, String eventType,
			String payload) {
		OutboxEvent event = new OutboxEvent();
		event.tenantId = tenantId;
		event.aggregateType = aggregateType;
		event.aggregateId = aggregateId;
		event.eventType = eventType;
		event.payload = payload;
		event.status = OutboxStatus.PENDING;
		event.occurredAt = Instant.now();
		return event;
	}

	/**
	 * 將事件標記為處理成功 (已成功遞送至 Message Broker)。
	 */
	public void markProcessed() {
		this.status = OutboxStatus.PROCESSED;
		this.processedAt = Instant.now();
	}

	/**
	 * 將事件標記為處理失敗 (通常用於超過最大重試次數後的記錄，供人工介入排查)。
	 */
	public void markFailed() {
		this.status = OutboxStatus.FAILED;
		this.processedAt = Instant.now();
	}
}