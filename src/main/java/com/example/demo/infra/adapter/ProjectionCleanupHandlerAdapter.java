package com.example.demo.infra.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.demo.application.port.ProjectionCleanupHandlerPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Projection Cleanup Handler Adapter (Infrastructure Layer)
 *
 * <pre>
 * 負責物理清空讀取端視圖與相關防護表的具體實作。 極具破壞性的操作，主要用於「全域事件重播 (Global System Replay)」前的系統淨化作業。
 * </pre>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
class ProjectionCleanupHandlerAdapter implements ProjectionCleanupHandlerPort {

	private final JdbcTemplate jdbcTemplate;

	@Override
	public void truncateReadModels() {
		log.warn("Executing TRUNCATE on all read model tables...");

		// 實務提醒：不同的資料庫對於 TRUNCATE 遇到 Foreign Key 時的行為不同。
		// 如果是 PostgreSQL，可能需要加 CASCADE (如: TRUNCATE TABLE department_views CASCADE)
		// 如果是 MySQL，可能需要先暫時關閉 FK 檢查 (SET FOREIGN_KEY_CHECKS = 0;)
		// 此處以最標準的無實體關聯表寫法為例：

		jdbcTemplate.execute("TRUNCATE TABLE department_views");
		jdbcTemplate.execute("TRUNCATE TABLE department_tree");

		// 極度重要防呆機制：
		// 必須同步清空冪等性紀錄表 (processed_events)！
		// 否則在執行全域重播 (Replay) 時，舊的 Event ID 會被 IdempotencyHandler 擋下，
		// 導致所有的歷史事件都被當作「重複處理」而全數忽略，造成 Read Model 永遠是空的。
		jdbcTemplate.execute("TRUNCATE TABLE processed_events");

		log.warn("All read model tables and idempotency states have been successfully truncated.");
	}
}