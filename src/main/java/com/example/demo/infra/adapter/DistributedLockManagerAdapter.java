package com.example.demo.infra.adapter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.demo.application.port.DistributedLockManagerPort;
import com.example.demo.infra.persistence.DistributedLockPersistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 分散式鎖 Adapter (Infrastructure Layer)
 * 
 * <pre>
 * 基於關聯式資料庫的非阻塞 (Non-blocking) 鎖實作。 
 * 
 * <strong>演算法策略</strong>：採用 "Ensure Exists -> Atomic Update" 兩段式鎖定，保證零外部依賴與零配置 (Zero Config)。 
 * 每一個服務實例 (Adapter) 在啟動時會產生一組隨機的 UUID 作為 workerId，供搶鎖時辨識持有者身分， 避免發生 A 釋放了 B 的鎖的問題。
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DistributedLockManagerAdapter implements DistributedLockManagerPort {

	private final DistributedLockPersistence persistence;

	// 產生該應用程式節點專屬的唯一工作識別碼 (Process-level identity)
	private final String workerId = UUID.randomUUID().toString();

	/**
	 * 帶有安全保護網的任務執行機制。 自動處理搶鎖、執行任務，並於 finally 區塊保證鎖的釋放。
	 */
	@Override
	public void executeWithLock(String lockKey, Duration lockDuration, Runnable task) {
		if (acquireLock(lockKey, lockDuration)) {
			try {
				log.debug("Lock [{}] acquired by worker [{}].", lockKey, workerId);
				task.run();
			} finally {
				releaseLock(lockKey);
				log.debug("Lock [{}] released by worker [{}].", lockKey, workerId);
			}
		} else {
			log.trace("Lock [{}] is held by another instance. Skipping task.", lockKey);
		}
	}

	/**
	 * 核心搶鎖邏輯 (Try-Lock)
	 */
	@Override
	public boolean acquireLock(String lockKey, Duration duration) {
		// 1. 先新增：確保資料庫裡有這把鎖的 Row (利用 DB 忽略重複主鍵的特性，保證記錄存在)
		persistence.ensureLockExists(lockKey);

		// 2. 準備搶鎖參數
		Instant now = Instant.now();
		Instant expiresAt = now.plus(duration);

		// 3. 再搶鎖：利用原子性 UPDATE 進行非阻塞 Try-Lock。
		// 僅在鎖過期或無人持有時，才允許將 workerId 更新為自己。
		int updatedRows = persistence.tryAcquireLock(lockKey, workerId, now, expiresAt);

		return updatedRows > 0;
	}

	/**
	 * 釋放持有的鎖。
	 */
	@Override
	public void releaseLock(String lockKey) {
		// 嚴格校驗：只允許釋放 workerId 等於自己的鎖
		persistence.releaseLock(lockKey, workerId);
	}
}