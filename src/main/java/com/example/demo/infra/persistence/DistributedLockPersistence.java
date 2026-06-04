package com.example.demo.infra.persistence;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.infra.distlock.DistributedLock;

/**
 * Distributed Lock Persistence (基礎設施層 - 分散式鎖資料庫操作介面)
 *
 * <pre>
 * 專責與資料庫進行底層 SQL 互斥通訊以實現叢集互斥鎖。 利用關聯式資料庫 (RDBMS) 的 ACID
 * 強一致性事務隔離與主鍵唯一性限制，達成「零外部配置、零額外中介軟體」的輕量鎖機制。
 * </pre>
 */
@Repository
public interface DistributedLockPersistence extends JpaRepository<DistributedLock, String> {

	/**
	 * 確保鎖的紀錄存在 (防禦性預埋 Row 記錄)。
	 * 
	 * <pre>
	 * 為了避免在高併發搶鎖更新時發生 Record Not Found 的問題，搶鎖前會先發出此 Native SQL 試圖塞入一筆防空紀錄。 💡
	 * <b>資料庫方言 (Dialect) 注意事項：</b> 
	 * - 當前寫法為 MySQL / MariaDB 語法: {@code INSERT IGNORE INTO ...} 
	 * - 若未來切換為 PostgreSQL，必須修正為: {@code INSERT INTO ... ON CONFLICT (lock_key) DO NOTHING}
	 * </pre>
	 *
	 * @param lockKey 鎖的唯一業務識別 Key (如 "job:outbox-polling-lock")
	 */
	@Modifying
	@Transactional
	@Query(value = """
			INSERT IGNORE INTO distributed_locks (lock_key)
			VALUES (:lockKey)
			""", nativeQuery = true)
	void ensureLockExists(@Param("lockKey") String lockKey);

	/**
	 * 原子性爭搶排他鎖 (Atomic Lock Acquisition)。
	 * 
	 * <pre>
	 * <b>核心互斥原理：</b> 充分利用資料庫原生的 行級鎖 (Row-level Lock) 與 MVCC 機制。 當多台伺服器併發對同一個
	 * lockKey 發出此更新時，資料庫會將其串行化排隊。 只有在「鎖目前無人持有 (locked_by IS NULL)」或「鎖的歷史租約已經過期
	 * (expires_at <= :now)」的情況下， 該 UPDATE 語句才會實質成功執行並回傳影響行數
	 * 1。這保證了有且僅有一台伺服器能霸佔鎖定，其餘節點皆回傳 0 失敗。
	 * </pre>
	 *
	 * @param lockKey   鎖的唯一用途識別 Key
	 * @param lockedBy  當前嘗試搶鎖的 Worker 節點唯一 UUID 識別碼
	 * @param now       當前系統時間 (用於過期判定與寫入上鎖起點時間)
	 * @param expiresAt 預期的自動租約過期時間點 (死鎖防禦網，防止 Worker 掛點導致鎖永遠無法被他人釋放)
	 * @return 成功更新的行數 (1 代表成功爭搶到鎖；0 代表鎖正被其他節點強力佔用中)
	 */
	@Modifying
	@Transactional
	@Query("""
			UPDATE DistributedLock l
			SET l.lockedBy = :lockedBy,
			    l.lockedAt = :now,
			    l.expiresAt = :expiresAt
			WHERE l.lockKey = :lockKey
			  AND (l.expiresAt IS NULL OR l.expiresAt <= :now)
			""")
	int tryAcquireLock(@Param("lockKey") String lockKey, @Param("lockedBy") String lockedBy, @Param("now") Instant now,
			@Param("expiresAt") Instant expiresAt);

	/**
	 * 安全物理釋放鎖。
	 * 
	 * <pre>
	 * <b>安全性防禦：</b> 更新條件中嚴格綁定了 {@code l.lockedBy = :lockedBy}。 僅限當初成功爭搶到這把鎖的同一個
	 * Worker 持有者，才能執行解鎖抹除動作， 徹底杜絕了 A 伺服器因執行任務超時、去把 B 伺服器剛剛新搶到的鎖給誤釋放掉的經典併發災難。
	 * </pre>
	 *
	 * @param lockKey  鎖的唯一識別碼
	 * @param lockedBy 當前嘗試解鎖的 Worker 節點唯一 UUID
	 */
	@Modifying
	@Transactional
	@Query("""
			UPDATE DistributedLock l
			SET l.lockedBy = NULL,
			    l.lockedAt = NULL,
			    l.expiresAt = NULL
			WHERE l.lockKey = :lockKey
			  AND l.lockedBy = :lockedBy
			""")
	void releaseLock(@Param("lockKey") String lockKey, @Param("lockedBy") String lockedBy);
}