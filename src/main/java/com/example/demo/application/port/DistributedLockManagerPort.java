package com.example.demo.application.port;

import java.time.Duration;

/**
 * Distributed Lock Manager Port (寫入端/基礎設施 - 分散式鎖管理合約)
 *
 * <pre>
 * 定義全域高併發環境下的分散式鎖互斥合約。 遵守依賴反轉原則 (DIP)，呼叫端 (如應用層) 僅宣告鎖的互斥意圖，完全無需知曉底層技術棧是基於 Redis
 * (Redisson)、ZooKeeper 還是關聯式資料庫的悲觀鎖實作。 
 * 核心場景：主要用於後台排程任務 (如: Outbox Pattern Event Polling) 防止多節點併發重複執行。
 * </pre>
 */
public interface DistributedLockManagerPort {

	/**
	 * 在互斥鎖的保護下安全地執行特定業務任務 (推薦優先使用的高階範本 API)。
	 * <p>
	 * 此方法屬於防禦性設計，實作類別會負責處理：搶鎖、搶鎖失敗的優雅退避、鎖定期間的續期、 以及**無論任務成功或拋出異常，都保證在最後的 finally
	 * 區塊中安全釋放鎖**，徹底杜絕死鎖 (Deadlock)。
	 * </p>
	 *
	 * @param lockKey      分散式鎖的唯互斥識別碼 (例如: "job:outbox-polling-lock")
	 * @param lockDuration 鎖的最大自動租約保留時間 (防止執行節點突發性崩潰、重啟導致鎖永遠無法釋放)
	 * @param task         成功獲取鎖定後，允許被執行的具體 Lambda 業務邏輯
	 */
	void executeWithLock(String lockKey, Duration lockDuration, Runnable task);

	/**
	 * 嘗試獲取排他性分散式鎖 (低階控制 API)。
	 * <p>
	 * 呼叫後立即回傳結果，不進行阻塞等待。使用此方法時，呼叫端必須自行控制釋放時機。
	 * </p>
	 *
	 * @param lockKey  分散式鎖的唯一識別碼
	 * @param duration 鎖定的有效保留時間
	 * @return {@code true} 代表成功霸佔此鎖；{@code false} 代表鎖目前正被其他節點或執行緒佔用
	 */
	boolean acquireLock(String lockKey, Duration duration);

	/**
	 * 主動物理釋放特定的分散式鎖 (低階控制 API)。
	 * <p>
	 * 通常與 {@link #acquireLock} 配對使用。呼叫時需注意不可釋放非本人所持有的鎖。
	 * </p>
	 *
	 * @param lockKey 欲釋放的分散式鎖唯一識別碼
	 */
	void releaseLock(String lockKey);
}