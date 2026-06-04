package com.example.demo.application.projection.strategy.view.impl;

import org.springframework.stereotype.Component;

import com.example.demo.application.domain.dept.event.DepartmentCreatedEvent;
import com.example.demo.application.port.DepartmentViewProjectionHandlerPort;
import com.example.demo.application.projection.strategy.view.ViewProjectionStrategy;

import lombok.RequiredArgsConstructor;

/**
 * 部門建立視圖策略
 * 
 * <pre>
 * 當新部門建立時，在讀取端視圖表中新增一筆對應紀錄，並將預設狀態設為 ACTIVE。
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class CreateViewStrategy implements ViewProjectionStrategy<DepartmentCreatedEvent> {
	
	private final DepartmentViewProjectionHandlerPort projection;

	@Override
	public Class<DepartmentCreatedEvent> supportedEvent() {
		return DepartmentCreatedEvent.class;
	}

	@Override
	public void execute(DepartmentCreatedEvent event) {
		projection.insertDepartmentView(event.getTenantId(), event.getDepartmentId(), event.getParentId(),
				event.getCode(), event.getName(), "ACTIVE", 0);
	}
}