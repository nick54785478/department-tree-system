package com.example.demo.application.projection.strategy.view.impl;

import org.springframework.stereotype.Component;

import com.example.demo.application.domain.dept.event.DepartmentSortOrderChangedEvent;
import com.example.demo.application.port.DepartmentViewProjectionHandlerPort;
import com.example.demo.application.projection.strategy.view.ViewProjectionStrategy;

import lombok.RequiredArgsConstructor;

/**
 * 部門排序變更視圖策略
 * <pre>
 * 更新節點在同層級之中的 UI 顯示權重。
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class ChangeSortOrderViewStrategy implements ViewProjectionStrategy<DepartmentSortOrderChangedEvent> {
	private final DepartmentViewProjectionHandlerPort projection;

	@Override
	public Class<DepartmentSortOrderChangedEvent> supportedEvent() {
		return DepartmentSortOrderChangedEvent.class;
	}

	@Override
	public void execute(DepartmentSortOrderChangedEvent event) {
		projection.updateDepartmentSortOrder(event.getTenantId(), event.getDepartmentId(), event.getNewSortOrder());
	}
}