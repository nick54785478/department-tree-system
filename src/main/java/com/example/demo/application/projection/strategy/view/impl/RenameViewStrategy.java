package com.example.demo.application.projection.strategy.view.impl;

import org.springframework.stereotype.Component;

import com.example.demo.application.domain.dept.event.DepartmentRenamedEvent;
import com.example.demo.application.port.DepartmentViewProjectionHandlerPort;
import com.example.demo.application.projection.strategy.view.ViewProjectionStrategy;

import lombok.RequiredArgsConstructor;

/**
 * 部門更名視圖策略
 * 
 * <pre>
 * 實現讀寫端的名稱最終一致性。
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class RenameViewStrategy implements ViewProjectionStrategy<DepartmentRenamedEvent> {
	
	private final DepartmentViewProjectionHandlerPort projection;

	@Override
	public Class<DepartmentRenamedEvent> supportedEvent() {
		return DepartmentRenamedEvent.class;
	}

	@Override
	public void execute(DepartmentRenamedEvent event) {
		projection.updateDepartmentName(event.getTenantId(), event.getDepartmentId(), event.getNewName());
	}
}