package com.example.demo.application.dispatcher;

import java.time.Instant;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.example.demo.application.service.DepartmentSnapshotCommandService;

import lombok.RequiredArgsConstructor;

/**
 * Snapshot Async Dispatcher (基礎設施層 - 快照非同步執行緒派發器)
 *
 * <pre>
 * 本類別屬於外圍的 Infrastructure Layer，專責與 Spring 框架的非同步任務執行緒池對接。 
 * 
 * <b>架構邊界防禦設計（Thread Boundary Isolation Pattern）：</b> 在整潔架構（Clean Architecture）與六角架構中，
 * 核心應用層（Application Layer）的 Use Case 服務， 應該保持絕對的純潔，不應該 import 框架特有的技術標籤（如 {@code @Async}），
 * 更不應該與任何特定執行緒模型或併發框架產生強耦合。 
 * 
 * 本分派器作為一個技術隔離的「中繼站」，在基礎設施層攔截請求，並宣告 {@code @Async} 技術指引。 
 * 
 * 這樣一來，主業務執行緒在 commit 寫入端事務後，會在此處將快照建立的重量級 I/O 工作直接丟進獨立的背景執行緒池中非同步執行， 
 * 主執行緒得以及時釋放並快速回應前端請求，徹底杜絕高併發下的 Request 阻塞（Thread Starvation）危機。
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class SnapshotAsyncDispatcher {

	/**
	 * 注入應用層專屬的同步快照命令服務
	 */
	private final DepartmentSnapshotCommandService commandService;

	/**
	 * 背景非同步派發執行快照建立任務。
	 * 
	 * <pre>
	 * <b>非同步執行序防護機制：</b> 
	 * 當此方法被呼叫時，Spring 框架會透過 AOP 代理攔截，將本次呼叫封裝成一個 {@link Runnable} 任務， 
	 * 拋入系統配置好的專屬 TaskExecutor 背景執行緒池中。 隨後，背景執行緒會安全地呼叫應用層同步方法 commandService.execute，
	 * 即使快照建立邏輯在執行期發生意外崩潰，其異常邊界也會被收攏在背景執行緒中，絕對不會回滾或阻塞主業務的事務。
	 * </pre>
	 *
	 * @param tenantId    租戶識別碼 (用於多租戶歷史資料防護與隔離)
	 * @param aggregateId 欲建立快照的部門聚合根唯一識別碼
	 * @param occurredAt  快照定格的時間基準點
	 */
	@Async
	public void dispatch(String tenantId, String aggregateId, Instant occurredAt) {
		// 門面轉發：讓背景執行緒安全進入應用層同步 Use Case 流程
		commandService.execute(tenantId, aggregateId, occurredAt);
	}
}