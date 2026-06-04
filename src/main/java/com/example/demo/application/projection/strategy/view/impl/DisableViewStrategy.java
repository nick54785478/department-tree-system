package com.example.demo.application.projection.strategy.view.impl;

import org.springframework.stereotype.Component;

import com.example.demo.application.domain.dept.event.DepartmentDisabledEvent;
import com.example.demo.application.port.DepartmentViewProjectionHandlerPort;
import com.example.demo.application.projection.strategy.view.ViewProjectionStrategy;

import lombok.RequiredArgsConstructor;

/**
 * 部門停用視圖策略
 * 
 * <pre>
 * 將讀取端視圖表的狀態更新為 DISABLED，供前端 UI 呈現反灰或隱藏效果。
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class DisableViewStrategy implements ViewProjectionStrategy<DepartmentDisabledEvent> {
	private final DepartmentViewProjectionHandlerPort projection;

	@Override
	public Class<DepartmentDisabledEvent> supportedEvent() {
		return DepartmentDisabledEvent.class;
	}

	@Override
	public void execute(DepartmentDisabledEvent event) {
		projection.updateDepartmentStatus(event.getTenantId(), event.getDepartmentId(), "DISABLED");
	}
}