package com.example.demo.application.projection.strategy.view.impl;

import org.springframework.stereotype.Component;

import com.example.demo.application.domain.dept.event.DepartmentDeletedEvent;
import com.example.demo.application.port.DepartmentViewProjectionHandlerPort;
import com.example.demo.application.projection.strategy.view.ViewProjectionStrategy;

import lombok.RequiredArgsConstructor;

/**
 * 部門刪除視圖策略
 * 
 * <pre>
 * <b>架構呼應</b>：為了支援時光機復原 (Undelete) 機制，這裡執行的是「邏輯刪除」 (UPDATE status = 'DELETED')，
 * 將該節點轉化為幽靈節點，而非物理刪除。
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class DeleteViewStrategy implements ViewProjectionStrategy<DepartmentDeletedEvent> {
	
	private final DepartmentViewProjectionHandlerPort projection;

	@Override
	public Class<DepartmentDeletedEvent> supportedEvent() {
		return DepartmentDeletedEvent.class;
	}

	@Override
	public void execute(DepartmentDeletedEvent event) {
		projection.deleteDepartmentView(event.getTenantId(), event.getDepartmentId());
	}
}