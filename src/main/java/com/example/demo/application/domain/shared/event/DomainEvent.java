package com.example.demo.application.domain.shared.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Base Domain Event (領域層 - 領域事件絕對骨幹) *
 * <p>
 * 實務優化版：合併了原本的純介面，由抽象類別一肩扛起業務合約與欄位共用。 0% 框架污染，完全不 import 任何 Jackson 註解。
 * </p>
 */
public abstract class DomainEvent {

	private String eventId;
	private Instant occurredAt;
	private String tenantId;
	private String operator;

	/**
	 * 寫入端業務建構子：聚合根內部 raise 事件時呼叫
	 */
	protected DomainEvent(String tenantId, String operator) {
		this.eventId = UUID.randomUUID().toString();
		this.occurredAt = Instant.now();
		this.tenantId = Objects.requireNonNull(tenantId, "TenantId required");
		this.operator = Objects.requireNonNull(operator, "Operator required");
	}

	/**
	 * 基礎設施端建構子：專供 Jackson 反序列化反射時「繞過 requireNonNull 驗證」的安全通道
	 */
	protected DomainEvent() {
		// 故意保持全空，Jackson 會透過 Field 反射直接把值塞入 private 欄位
	}

	// ==========================================
	// 業務合約方法 (Getter)
	// ==========================================
	public String getEventId() {
		return this.eventId;
	}

	public Instant getOccurredAt() {
		return this.occurredAt;
	}

	public String getTenantId() {
		return this.tenantId;
	}

	public String getOperator() {
		return this.operator;
	}

	// ==========================================
	// 強制子類別實作的聚合元數據
	// ==========================================
	public abstract String aggregateType();

	public abstract String aggregateId();

	// ==========================================
	// 預設共用行為
	// ==========================================
	public String eventType() {
		return this.getClass().getSimpleName();
	}

	public String routingKey() {
		return tenantId + "." + aggregateType() + "." + eventType();
	}
}