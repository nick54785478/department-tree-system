package com.example.demo.iface.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.projection.projector.DepartmentViewProjector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Department View Projection Handler (讀取端 - 扁平視圖投影攔截器)
 *
 * <pre>
 * 專責在正常業務流程中 (Command Side 成功 Commit 後)，非同步攔截部門基本資料變更相關的領域事件。 
 * <b>職責分離原則</b>：本類別作為 Event Dispatcher，與 Tree Projection Handler 徹底解耦。 
 * 
 * 專注於將事件派發給 {@link DepartmentViewProjector} 更新單維度的部門視圖表 (department_views)。
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepartmentViewProjectionHandler {

	private final DepartmentViewProjector projector;

	/**
	 * 統一攔截所有 DomainEvent，直接委派給 Projector 進行策略路由。
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void on(DomainEvent event) {
		log.trace("View Projection Handler intercepted event: {}", event.eventType());

		// 透過 Department View Projector (視圖投影機 - 策略分派中心) 進行路由。
		// Projector 會將事件配對至具體的更新策略 (如 UpdateNameStrategy, DeleteStrategy)，
		// 並由各策略內部處理專屬的冪等性與 DB Transaction。
		projector.project(event);
	}

}