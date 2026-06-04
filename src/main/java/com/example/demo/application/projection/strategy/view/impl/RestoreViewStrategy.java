package com.example.demo.application.projection.strategy.view.impl;

import org.springframework.stereotype.Component;

import com.example.demo.application.domain.dept.event.DepartmentRestoredEvent;
import com.example.demo.application.port.DepartmentViewProjectionHandlerPort;
import com.example.demo.application.projection.strategy.view.ViewProjectionStrategy;

import lombok.RequiredArgsConstructor;

/**
 * 部門復原視圖策略
 * <p>
 * 當時光機觸發部門復活時，重建該部門的讀取端基本資料狀態。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class RestoreViewStrategy implements ViewProjectionStrategy<DepartmentRestoredEvent> {

	private final DepartmentViewProjectionHandlerPort projection;

	@Override
	public Class<DepartmentRestoredEvent> supportedEvent() {
		return DepartmentRestoredEvent.class;
	}

	@Override
	public void execute(DepartmentRestoredEvent event) {
		// 架構亮點 (事件豐富化 / Event Enrichment)：
		// 不再將狀態寫死為 "ACTIVE"，而是精準取用 RestoredEvent 中所攜帶的「生前狀態」。
		// 確保原先為 DISABLED 的部門在復活後，依然維持 DISABLED。
		projection.updateDepartmentStatus(event.getTenantId(), event.getDepartmentId(), event.getStatus());

		// 若未來事件中有帶上 name 與 code，也可以在此處進行 update 確保資料同步。
	}
}