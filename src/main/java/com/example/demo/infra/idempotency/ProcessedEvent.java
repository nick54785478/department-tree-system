package com.example.demo.infra.idempotency;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ProcessedEvent (基礎設施層 - 讀取端消費冪等性防護實體)
 *
 * <pre>
 * 專門用於實踐「冪等消費者模式 (Idempotent Consumer Pattern)」的物理去重表。
 * 
 * <b>分散式防護最後防線<b/>：
 * 當訊息佇列 (Message Broker) 因網路抖動觸發 At-Least-Once (至少一次遞送) 重試，或是背景 Projector 
 * 重播歷史事件時，本表利用底層資料庫的 <b>主鍵唯一性約束 (Unique Primary Key Constraint)<b/> 進行強互斥攔截。
 * 只要相同的 event_id 企圖重複寫入，資料庫便會原子性地拋出主鍵衝突異常，藉此保證同一個事件「絕對只會被唯讀端投影成功處理一次」。
 * </pre>
 */
@Getter
@Entity
@Table(name = "processed_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

	/**
	 * 領域事件的唯一識別碼。
	 * <p>
	 * 設計擴充提醒：在多重投影機 (Multiple Projectors) 併發消費同一個事件的場景下，
	 * </p>
	 * 此處可延伸儲存為「{@code ProjectorName_EventID}」的複合加工識別 Key，實現精確到處理器層級的冪等控制。
	 */
	@Id
	@Column(name = "event_id", length = 128, nullable = false, updatable = false)
	private String eventId;

	/**
	 * 該領域事件首次被成功消費並 Commit 的系統時間，主要用於大批量重播時的稽核與清理
	 */
	@Column(name = "processed_at", nullable = false, updatable = false)
	private Instant processedAt;

	/**
	 * 構建一筆全新事件的冪等防禦記錄。
	 */
	public ProcessedEvent(String eventId) {
		this.eventId = eventId;
		this.processedAt = Instant.now();
	}
}