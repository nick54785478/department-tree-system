package com.example.demo.iface.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.projection.projector.DepartmentTreeProjector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Department Tree Projection Handler (讀取端 - 樹狀幾何投影攔截器)
 *
 * <pre>
 * 專責在正常業務流程中 (Command Side 成功 Commit 後)，非同步攔截部門結構變更相關的領域事件。
 * 
 * <b>職責分離原則</b>：本類別僅做「事件監聽與轉發 (Dispatcher)」，不包含任何業務邏輯。 
 * 攔截後全數委派給 {@link DepartmentTreeProjector} 進行後續的處理。
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepartmentTreeProjectionHandler {

	private final DepartmentTreeProjector projector;

	/**
	 * 統一攔截所有 DomainEvent，直接委派給 Projector 進行策略路由。
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void on(DomainEvent event) {
		log.trace("Tree Projection Handler intercepted event: {}", event.eventType());

		// 透過 Department Tree Projector (樹狀結構投影機 - 策略分派中心) 進行路由。
		// Projector 將使用策略工廠模式 (Strategy Factory) 找出對應的 Handler 執行閉包表重建。
		// 包括冪等性防護 (Idempotency) 與 Transaction REQUIRES_NEW 獨立邊界，
		// 皆已封裝在 Projector 與對應的 Strategy 實作中。
		projector.project(event);
	}

}