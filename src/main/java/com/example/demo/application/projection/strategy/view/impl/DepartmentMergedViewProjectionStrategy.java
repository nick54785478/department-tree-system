package com.example.demo.application.projection.strategy.view.impl;

import org.springframework.stereotype.Component;

import com.example.demo.application.domain.dept.event.DepartmentMergedEvent;
import com.example.demo.application.port.DepartmentViewProjectionHandlerPort;
import com.example.demo.application.projection.strategy.view.ViewProjectionStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 部門合併視圖投影策略 (View Projection Strategy - Merged)
 *
 * <p>
 * 負責處理 {@link DepartmentMergedEvent}。 💡 業務語意：當來源部門被合併後，必須將其在扁平視圖
 * (department_views) 中的狀態立即轉為 DISABLED。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepartmentMergedViewProjectionStrategy implements ViewProjectionStrategy<DepartmentMergedEvent> {

	private final DepartmentViewProjectionHandlerPort viewProjectionHandler;

	@Override
	public Class<DepartmentMergedEvent> supportedEvent() {
		return DepartmentMergedEvent.class; // 註冊綁定對應的 Event Class
	}

	@Override
	public void execute(DepartmentMergedEvent event) {
		// 專注於純粹的業務投影邏輯，無須理會 Transaction 與 Idempotency
		viewProjectionHandler.updateDepartmentStatus(event.getTenantId(), event.getDepartmentId(), "DISABLED");

		log.info("View Projection Strategy: 部門 [{}] 已合併，狀態更新為 DISABLED", event.getDepartmentId());
	}
}