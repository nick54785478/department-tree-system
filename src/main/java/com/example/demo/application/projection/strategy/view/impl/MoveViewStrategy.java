package com.example.demo.application.projection.strategy.view.impl;

import org.springframework.stereotype.Component;

import com.example.demo.application.domain.dept.event.DepartmentMovedEvent;
import com.example.demo.application.port.DepartmentViewProjectionHandlerPort;
import com.example.demo.application.projection.strategy.view.ViewProjectionStrategy;

import lombok.RequiredArgsConstructor;

/**
 * 部門移動視圖策略
 * <pre>
 * 僅負責更新扁平視圖表中的 parent_id 欄位，不涉及複雜的閉包表幾何運算。
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class MoveViewStrategy implements ViewProjectionStrategy<DepartmentMovedEvent> {
	
	private final DepartmentViewProjectionHandlerPort projection;

	@Override
	public Class<DepartmentMovedEvent> supportedEvent() {
		return DepartmentMovedEvent.class;
	}

	@Override
	public void execute(DepartmentMovedEvent event) {
		projection.updateDepartmentViewParent(event.getTenantId(), event.getDepartmentId(), event.getNewParentId());
	}
}