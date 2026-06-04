package com.example.demo.application.projection.strategy.tree.impl;

import org.springframework.stereotype.Component;

import com.example.demo.application.domain.dept.event.DepartmentDeletedEvent;
import com.example.demo.application.port.DepartmentTreeProjectionHandlerPort;
import com.example.demo.application.projection.strategy.tree.TreeProjectionStrategy;

import lombok.RequiredArgsConstructor;

/**
 * 部門刪除策略 (Delete Tree Strategy)
 *
 * <p>
 * 當部門被刪除時，物理拔除其在閉包表中的所有幾何路徑。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class DeleteTreeStrategy implements TreeProjectionStrategy<DepartmentDeletedEvent> {

	private final DepartmentTreeProjectionHandlerPort treePort;

	@Override
	public Class<DepartmentDeletedEvent> supportedEvent() {
		return DepartmentDeletedEvent.class;
	}

	@Override
	public void execute(DepartmentDeletedEvent event) {
		// 架構意圖：雖然視圖端 (View) 是做邏輯刪除 (status='DELETED')，
		// 但在樹狀幾何端 (Tree) 必須執行「物理刪除」，以徹底切斷它與其他節點的上下級空間關聯。
		treePort.deleteNodeAndRelations(event.getTenantId(), event.getDepartmentId());
	}
}