package com.example.demo.infra.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.infra.outbox.OutboxEvent;
import com.example.demo.infra.outbox.vo.OutboxStatus;

/**
 * Outbox Event Persistence (基礎設施層 - 交易發件匣事件持久化介面)
 *
 * <pre>
 * 專責 Transactional Outbox Pattern 的事件掃描與讀取。 提供給背景異步排程輪詢器 (Poller)
 * 定期拉取尚未派發的領域事件，以確保事件能 100% Guaranteed Delivery 遞交給消息中介軟體。
 * </pre>
 */
@Repository
public interface OutboxEventPersistence extends JpaRepository<OutboxEvent, Long> {

	/**
	 * 撈取指定狀態的事件，並依照發生時間進行升序排序 (先發生先派發)。
	 * 
	 * <pre>
	 * <b>防禦性高效能設計：</b> 限制 Top 100 批次拉取是極其關鍵的生產級做法。它能有效防禦在突發大流量下，
	 * 背景執行緒一次性將數萬筆大 JSON Payload 撈入 JVM 記憶體中所導致的 OOM 災難，同時能縮短資料庫的鎖表時間。 
	 * 
	 * 配合實體表上的 idx_outbox_tenant_status 複合索引，可達成單表毫秒級的極速滑動掃描。
	 * </pre>
	 *
	 * @param status 發件匣事件狀態 (常規輪詢時通常傳入 {@link OutboxStatus#PENDING})
	 * @return 依時間排序最舊的 100 筆待處理發件匣記錄清單
	 */
	List<OutboxEvent> findTop100ByStatusOrderByOccurredAt(OutboxStatus status);
}