package com.example.demo.infra.event.mixin;

import com.example.demo.config.JacksonConfiguration;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Domain Event Jackson MixIn (基礎設施/序列化 - 領域事件多型元數據混入)
 *
 * <pre>
 * 這是專門為抽象基底類別 {@code
 * DomainEvent
 * } 量身打造的 Jackson 序列化與反序列化多型控制規則。
 *
 * <b>非侵入式整潔架構設計原則 (MixIn Pattern)：</b> 
 * 
 * 在純粹的 DDD 實踐中，領域事件（Domain Event）屬於 Domain Layer 的核心資產，<b>絕對不允許 import 任何第三方 JSON 框架 (如 Jackson
 * 的 @JsonTypeInfo, @JsonProperty) 的 Annotation</b>，否則領域層就會遭到技術框架綁架。
 *
 * 本類別作為一個技術隔離的「抽象外掛影子」，在基礎設施層宣告規則，並在系統啟動時（{@link JacksonConfiguration}）
 * 強制混入綁定。這使得領域事件在維持 100% 乾淨、不帶任何技術痕跡的情況下，依然能獲得頂級的序列化控制。
 * </pre>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, // 1. 宣告反序列化時的識別策略：使用「自訂名稱 (SimpleName/EventName)」作為型別指引
		include = JsonTypeInfo.As.EXISTING_PROPERTY, // 2. 宣告識別標籤的安放位置：直接利用物件現有的屬性欄位，不產生多餘的巢狀 JSON 外殼
		property = "eventType", // 3. 識別標籤的 JSON 欄位 key 值命名為 "eventType"
		visible = true // 4. 反序列化時，保持該欄位對 Java 屬性設值的可見性，確保 Event 實體能正確填入資料
)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class DomainEventMixIn {

	// 強迫 Jackson 遇到 DomainEvent 的子類別時，反序列化一律走無參建構子
	protected DomainEventMixIn() {
	}

	@JsonProperty("eventType")
	public abstract String eventType();

	@JsonIgnore
	public abstract String routingKey();

	@JsonProperty("eventId")
	public abstract String getEventId();

	@JsonProperty("occurredAt")
	public abstract java.time.Instant getOccurredAt();

	@JsonProperty("tenantId")
	public abstract String getTenantId();

	@JsonProperty("operator")
	public abstract String getOperator();
}