package com.example.demo.infra.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.infra.snapshot.AggregateSnapshot;

/**
 * Snapshot Persistence (基礎設施層 - 時光機歷史快照資料庫操作介面)
 *
 * <pre>
 * 專責管理與撈取特定聚合根在歷史上定格的檢查點 (Checkpoint) 快照記錄。 這是降低事件溯源重播成本、實現高效智能時光回溯的關鍵底座元件。
 * </pre>
 */
public interface SnapshotPersistence extends JpaRepository<AggregateSnapshot, Long> {

	/**
	 * 取得指定歷史時間點之前，版本號最大 (最新、離目標時間點最近) 的那一份有效快照。
	 * 
	 * <pre>
	 * <b>時光機精髓查詢：</b> 搭配 OccurredAtLessThanEqual 鎖定時間天花板，再透過 OrderByVersionDesc 將最新定格的快照排在第 1 筆， 
	 * 最後利用  findFirst 斬斷其餘記錄，在資料庫層級用最完美的索引效能，幫時光機引擎抓出回溯的「歷史跳板基底」。
	 * </pre>
	 *
	 * @param tenantId      租戶識別碼
	 * @param aggregateType 聚合根類型名稱 (如 "Department")
	 * @param aggregateId   聚合唯一業務 ID
	 * @param upToTimestamp 時光機指定的截止查詢歷史時刻
	 * @return 封裝了最新歷史快照實體的 Optional 容器；若在該歷史時間點之前從未建立過快照，則回傳
	 *         {@code Optional.empty()}
	 */
	Optional<AggregateSnapshot> findFirstByTenantIdAndAggregateTypeAndAggregateIdAndOccurredAtLessThanEqualOrderByVersionDesc(
			String tenantId, String aggregateType, String aggregateId, Instant upToTimestamp);
}