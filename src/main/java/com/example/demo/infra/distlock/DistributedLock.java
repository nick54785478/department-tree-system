package com.example.demo.infra.distlock;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DistributedLock (基礎設施層 - 關係型資料庫分散式鎖實體)
 *
 * <pre>
 * 配合資料庫原生的原子性更新 (Atomic UPDATE WHERE Expiry) 策略，實作出的輕量級非阻塞分散式鎖模型。 
 * 
 * <b>核心價值</b>：
 * - 在不引入額外快取中介軟體 (如 Redis/Redisson、ZooKeeper) 的前提下，達成「零外部依賴、零技術配置」的叢集互斥。
 * - 確保多台機器橫向擴充部署時，同一個後台任務 (如 Outbox 異步輪詢派發任務、定時快照生成任務)
 * - 在同一個時間片段內，<b>絕對只會由一台機器的單一線程爭搶執行</b>，徹底杜絕併發重複處理的亂象。
 * </pre>
 */
@Getter
@Entity
@Table(name = "distributed_locks")
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 保持 PROTECTED 權限，由基礎設施 Adapter 專屬維護
public class DistributedLock {

	/**
	 * 鎖的唯一業務用途識別 Key (例如: "job:outbox-polling-lock")
	 */
	@Id
	@Column(name = "lock_key", length = 64)
	private String lockKey;

	/**
	 * 當前成功霸佔這把鎖的伺服器節點工作識別碼 (通常為各節點啟動時隨機產生的執行期 workerId UUID)
	 */
	@Column(name = "locked_by", length = 128)
	private String lockedBy;

	/**
	 * 上鎖成功的絕對系統時間戳記
	 */
	@Column(name = "locked_at")
	private Instant lockedAt;

	/**
	 * 鎖的租約自動過期截止時間。
	 * 
	 * 死鎖防禦網：當某台伺服器成功搶鎖後意外遭遇 OOM 崩潰、重啟或網路斷線，導致未能執行 finally 釋放鎖時， 其他健康節點可以透過
	 * {@code WHERE expires_at < NOW()} 判斷其過期，並安全地接管該鎖，徹底杜絕全系統死鎖 (Deadlock)。
	 */
	@Column(name = "expires_at")
	private Instant expiresAt;

}