package com.example.demo.infra.event.sourcing;

import java.time.Instant;

import com.example.demo.application.domain.shared.event.DomainEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * StoredEvent (基礎設施層 - 事件儲存物理實體)
 *
 * <pre>
 * 專為關聯式資料庫 (RDBMS) 模擬 Event Store (事件商店) 所設計的底座實體。
 * 
 * <p>歷史真理防護原則</p>：
 * 
 * 本表代表系統中「不可變 (Immutable)」的絕對真相履歷。本實體映射的所有欄位皆宣告為 updatable = false。
 * 在架構約定上，本表<p>絕對禁止執行任何 UPDATE 或 DELETE 操作</p>，只能進行 Append-Only (純附加) 寫入。
 * 這是時光機回溯、災難審計與唯讀端投影重建 (Global Event Replay) 的真相來源。
 * </pre>
 */
@Getter
@Entity
@Table(name = "event_store", indexes = {
		// 核心優化索引 1：專為單一聚合根「歷史重播 (Rehydrate)」設計的複合索引，依序載入事件流
		@Index(name = "idx_es_aggregate", columnList = "tenant_id, aggregate_type, aggregate_id, occurred_at"),
		// 核心優化索引 2：專為時光機「特定時間點截止查詢」優化的索引，用於追溯歷史斷面
		@Index(name = "idx_es_occurred_at", columnList = "occurred_at") })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredEvent {

	/**
	 * 全域位置流水號 (Global Position)。 確保整個系統跨租戶、跨聚合所有事件發生的絕對物理先後順序。
	 * 避坑設計：在分散式叢集環境下，若單純依賴伺服器時間戳記 (Timestamp)，會因微小的時鐘偏移 (Clock Skew)
	 * 或高併發撞鐘導致順序錯亂。採用資料庫自增鎖 (Auto Increment) 是 RDBMS 模擬 Event Store 最穩健的全域順序方案。
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long globalPosition;

	/**
	 * 領域事件的唯一業務識別碼 (通常為 UUID)，用於追溯或與外部系統對接
	 */
	@Column(name = "event_id", nullable = false, updatable = false, length = 64)
	private String eventId;

	/**
	 * 多租戶隔離識別碼
	 */
	@Column(name = "tenant_id", nullable = false, updatable = false, length = 50)
	private String tenantId;

	/**
	 * 聚合根類型名稱 (如: "Department")
	 */
	@Column(name = "aggregate_type", nullable = false, updatable = false, length = 100)
	private String aggregateType;

	/**
	 * 聚合根唯一業務 ID
	 */
	@Column(name = "aggregate_id", nullable = false, updatable = false, length = 50)
	private String aggregateId;

	/**
	 * 領域事件型別名稱 (如: "DepartmentRestoredEvent")
	 */
	@Column(name = "event_type", nullable = false, updatable = false, length = 200)
	private String eventType;

	/**
	 * 該領域事件序列化後的 JSON 完整二進位或文本載體
	 */
	@Lob
	@Column(name = "payload", nullable = false, updatable = false)
	private String payload;

	/**
	 * 事件精確發生時間 (寫入端聚合根註冊事件時的系統時間)
	 */
	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	/**
	 * 觸發此業務變更的操作者或管理員 ID (審計核心欄位)
	 */
	@Column(name = "operator", nullable = false, updatable = false, length = 100)
	private String operator;

	/**
	 * 基於領域事件與序列化 JSON 文本構建 StoredEvent 實體。
	 */
	public StoredEvent(DomainEvent event, String payloadJson) {
		this.eventId = event.getEventId();
		this.tenantId = event.getTenantId();
		this.aggregateType = event.aggregateType();
		this.aggregateId = event.aggregateId();
		this.eventType = event.eventType();
		this.payload = payloadJson;
		this.occurredAt = event.getOccurredAt();
		this.operator = event.getOperator();
	}
}