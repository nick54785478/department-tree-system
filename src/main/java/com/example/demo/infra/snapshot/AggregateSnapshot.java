package com.example.demo.infra.snapshot;

import java.time.Instant;

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
 * AggregateSnapshot (基礎設施層 - 聚合根歷史快照物理實體)
 *
 * <pre>
 * 專為事件溯源 (Event Sourcing) 架構設計的狀態快照存檔表。
 * 
 * <b>架構優化核心意圖（Performance Optimization Pattern）</b>：
 * 隨著系統運行時間拉長，單一聚合根累積的生命週期事件數量會不斷堆疊（從數十顆到上千顆）。 如果時光機每次要查詢歷史狀態、或是 Command 端要載入聚合根時，
 * 都必須從盤古開天時期的第 1 顆事件開始重播，系統的讀取效能將會隨著時間推移而線性崩潰（時間複雜度為 $O(N)$）。
 * 
 * 本實體做為「記憶體狀態的定格檢查點 (Checkpoint)」，透過定期將充血狀態（如 {@code
 * DepartmentTemporalState
 * }）
 * 序列化為 JSON 文本並持久化於此，未來時光機在回溯歷史時，即可啟動【先找快照基底，再補歷史差額】的智能演算法， 成功將狀態重建的代價優化至
 * 趨近於 $O(1)$ 的極致效能。
 * </pre>
 */
@Getter
@Entity
@Table(name = "event_snapshots", indexes = {
		// 核心優化索引：專為時光機「向後追溯最新存檔點」設計。
		// 輸入租戶、聚合類型與 ID 後，能透過複合索引在極短時間內，精準撈出小於或等於目標時間點的最大（最新）版本號快照。
		@Index(name = "idx_snapshot_lookup", columnList = "tenant_id, aggregate_type, aggregate_id, version") })
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 保持 PROTECTED 權限，隔離外部不當的 new 操作，統一由底層儲存 Port 與 JPA 管理
public class AggregateSnapshot {

	/**
	 * 快照記錄的物理自增主鍵
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * 多租戶隔離識別碼，落實多租戶資料在持久化層的邏輯防護牆
	 */
	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	/**
	 * 聚合根類型名稱 (例如: "Department")，用以在單一快照表中區隔不同的業務邊界
	 */
	@Column(name = "aggregate_type", nullable = false, length = 100)
	private String aggregateType;

	/**
	 * 聚合根的業務唯一識別識別碼
	 */
	@Column(name = "aggregate_id", nullable = false, length = 50)
	private String aggregateId;

	/**
	 * 快照定格事件版本號。
	 * <p>
	 * 差額計算的重要基準：記錄了這份快照到底「包含並結算到哪一個特定的歷史版本號」。 在本系統架構中，此數值通常對應 {@code StoredEvent} 的
	 * {@code globalPosition}，或是該寫入端聚合根的最新 {@code version} 序號。
	 * 未來重播引擎便以此數值為起點，只撈取大於此版本號的 Delta Events 進行記憶體補齊。
	 * </p>
	 */
	@Column(name = "version", nullable = false)
	private Long version;

	/**
	 * 本次快照建立時的精確歷史截止時間戳記，作為時光機斷面搜尋的黃金時間線依據
	 */
	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	/**
	 * 快照狀態 JSON 文本載體。
	 * <p>
	 * 技術隔離設計：存放的是 {@code DepartmentTemporalState} 或充血領域狀態物件經由序列化 Port 打包後的結果。 採用
	 * {@code @Lob} 大物件映射，確保能容納包含組織屬性、排序權重、甚至是當下指派的人員清單快照。
	 * </p>
	 */
	@Lob
	@Column(name = "payload", nullable = false)
	private String payload;

	/**
	 * 全參數建構子，供背景非同步快照調度器 (SnapshotCommandService) 在結算進度時實例化快照使用。
	 *
	 * @param tenantId      租戶識別碼
	 * @param aggregateType 聚合根類型
	 * @param aggregateId   聚合根 ID
	 * @param version       結算截止的事件版本序號
	 * @param occurredAt    快照定格的歷史時間戳記
	 * @param payload       序列化後的動態狀態 JSON 密閉 Payload
	 */
	public AggregateSnapshot(String tenantId, String aggregateType, String aggregateId, Long version,
			Instant occurredAt, String payload) {
		this.tenantId = tenantId;
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.version = version;
		this.occurredAt = occurredAt;
		this.payload = payload;
	}
}