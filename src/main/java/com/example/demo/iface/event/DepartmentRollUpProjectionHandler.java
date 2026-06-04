package com.example.demo.iface.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.demo.application.domain.dept.event.EmployeeAssignedToDepartmentEvent;
import com.example.demo.application.domain.dept.event.EmployeeUnassignedFromDepartmentEvent;
import com.example.demo.application.port.DepartmentRollUpProjectionHandlerPort;
import com.example.demo.application.port.IdempotencyHandlerPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Department Roll-Up Projection Handler (讀取端 - 統計滾動加總投影攔截器)
 *
 * <pre>
 * 專責監聽「人員異動」相關事件，並利用閉包表 (Closure Table) 執行高效的樹狀結構人數統計滾動 (Roll-up)。 
 * 
 * <b>架構設計重點</b>：
 * 1. 最終一致性：採用 TransactionPhase.AFTER_COMMIT，確保核心業務資料已成功寫入 DB 後，才觸發統計更新。
 * 2. 交易隔離：搭配 @Transactional(propagation = Propagation.REQUIRES_NEW)，為讀取端的更新開啟獨立的
 * DB Transaction， 即使讀取端發生 Deadlock 或例外，也絕對不會回滾 (Rollback) 已經成功的寫入端業務行為。 
 * 3. 冪等性防護：利用 {@link IdempotencyHandlerPort} 確保事件重試時不會導致人數被重複加總。
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepartmentRollUpProjectionHandler {

	private final DepartmentRollUpProjectionHandlerPort adapter; // 負責執行原生 SQL 的 Adapter
	private final IdempotencyHandlerPort idempotencyHandler; // 完美的分散式冪等防護盾

	/**
	 * 處理員工加入部門事件 (總人數 +1)
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void on(EmployeeAssignedToDepartmentEvent event) {

		// 🛡️ 冪等性防禦：加上 _ROLLUP 後綴，建立獨立消費者防線
		String idempotencyKey = event.getEventId() + "_ROLLUP";

		if (!idempotencyHandler.tryProcess(idempotencyKey)) {
			log.trace("Event {} already processed for ROLLUP. Skipping roll-up.", event.getEventId());
			return;
		}

		String tenantId = event.getTenantId();
		String deptId = event.getDepartmentId();

		// 1. 直屬人數 +1 (僅針對目標部門單點更新)
		adapter.incrementDirectHeadcount(tenantId, deptId, 1);

		// 💡 2. 總人數滾動 +1 (利用 Closure Table 找出所有祖先，一口氣全部 +1)
		adapter.incrementTotalHeadcountForAncestors(tenantId, deptId, 1);
	}

	/**
	 * 處理員工移出部門事件 (總人數 -1)
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void on(EmployeeUnassignedFromDepartmentEvent event) {

		// 🛡️ 冪等性防禦：加上 _ROLLUP 後綴
		String idempotencyKey = event.getEventId() + "_ROLLUP";

		if (!idempotencyHandler.tryProcess(idempotencyKey)) {
			log.trace("Event {} already processed for ROLLUP. Skipping roll-up.", event.getEventId());
			return;
		}

		String tenantId = event.getTenantId();
		String deptId = event.getDepartmentId();

		// 反向操作：人數 -1 (直接傳入負數作為 delta)
		adapter.incrementDirectHeadcount(tenantId, deptId, -1);
		adapter.incrementTotalHeadcountForAncestors(tenantId, deptId, -1);
	}
}