package com.example.demo.iface.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.demo.application.domain.dept.event.DepartmentDisabledEvent;
import com.example.demo.application.domain.dept.event.EmployeeAssignedToDepartmentEvent;
import com.example.demo.application.domain.dept.event.EmployeeUnassignedFromDepartmentEvent;
import com.example.demo.application.port.IdempotencyHandlerPort;
import com.example.demo.infra.persistence.DepartmentEmployeeViewPersistence;
import com.example.demo.infra.projection.DepartmentEmployeeView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Department Employee Projection Handler (唯讀端 - 人員編制視圖投影器帶高可用冪等防護)
 *
 * <pre>
 * <b>CQRS 跨節點防禦牆：</b> 負責攔截部門聚合根發出的領域事件，並將其狀態投影到扁平化的 View 表中。 
 * 本類別全面導入了 {@link IdempotencyHandlerPort} 防禦核心，確保即使網路發生抖動、 訊息佇列 (Message Queue)
 * 發生重複投遞，唯讀端視圖的資料也絕對不會產生雙重寫入或數據撕裂。
 *
 * <b>交易原子性綁定：</b> 藉由 TransactionPhase.BEFORE_COMMIT ，冪等狀態記錄的霸佔與 View 表的業務異動會
 * 併入同一個本地 Transaction。 若併發請求導致鎖定衝突，整個資料庫事務將會原子性回滾，從物理底層徹底消滅 Race Condition。
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepartmentEmployeeProjectionHandler {

	private final DepartmentEmployeeViewPersistence persistence;

	/**
	 * 注入無狀態的分散式/關聯式冪等防護組件
	 */
	private final IdempotencyHandlerPort idempotencyHandler;

	/**
	 * 攔截【員工指派事件】：將關聯寫入 View 表 (內建防禦重試)
	 */
	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void handle(EmployeeAssignedToDepartmentEvent event) {
		log.info("[Projection Pipeline] Intercepted event '{}' for evaluation.", event.getEventId());

		// 核心防線 1：原子性搶佔首次處理權，加上 _EMP_VIEW 後綴
		String idempotencyKey = event.getEventId() + "_EMP_VIEW";

		if (!idempotencyHandler.tryProcess(idempotencyKey)) {
			log.warn(
					"[Idempotency Guard] Duplicate event injection detected! Key [{}] has already been processed or is under transaction. Dropping gracefully.",
					idempotencyKey);
			return;
		}

		log.info(
				"[Projection] First delivery confirmed. Projecting EmployeeAssignedEvent to View: Dept [{}] + Emp [{}]",
				event.aggregateId(), event.getEmployeeId());

		DepartmentEmployeeView view = new DepartmentEmployeeView(event.getTenantId(), event.aggregateId(),
				event.getEmployeeId(), event.getOccurredAt());
		persistence.save(view);
	}

	/**
	 * 攔截【員工解編事件】：將關聯從 View 表中物理刪除
	 */
	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void handle(EmployeeUnassignedFromDepartmentEvent event) {
		log.info("[Projection Pipeline] Intercepted event '{}' for evaluation.", event.getEventId());

		// 核心防線 2：反查並鎖定解編事件，加上 _EMP_VIEW 後綴
		String idempotencyKey = event.getEventId() + "_EMP_VIEW";

		if (!idempotencyHandler.tryProcess(idempotencyKey)) {
			log.warn("[Idempotency Guard] Duplicate event injection detected! Key [{}] skipped.", idempotencyKey);
			return;
		}

		log.info(
				"[Projection] First delivery confirmed. Projecting EmployeeUnassignedEvent to View: Removing Dept [{}] + Emp [{}]",
				event.aggregateId(), event.getEmployeeId());

		persistence.deleteByTenantIdAndDepartmentIdAndEmployeeId(event.getTenantId(), event.aggregateId(),
				event.getEmployeeId());
	}

	/**
	 * 攔截【部門停用事件】：批量拔除該部門下的所有人員關聯
	 */
	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void handle(DepartmentDisabledEvent event) {
		log.info("[Projection Pipeline] Intercepted event '{}' for evaluation.", event.getEventId());

		// 核心防線 3：反查並鎖定組織裁剪事件，加上 _EMP_VIEW 後綴
		String idempotencyKey = event.getEventId() + "_EMP_VIEW";

		if (!idempotencyHandler.tryProcess(idempotencyKey)) {
			log.warn("[Idempotency Guard] Duplicate event injection detected! Key [{}] skipped.", idempotencyKey);
			return;
		}

		log.info(
				"[Projection] First delivery confirmed. Projecting DepartmentDisabledEvent to View: Clearing all employees for Dept [{}]",
				event.aggregateId());

		persistence.deleteAllByTenantIdAndDepartmentId(event.getTenantId(), event.aggregateId());
	}
}