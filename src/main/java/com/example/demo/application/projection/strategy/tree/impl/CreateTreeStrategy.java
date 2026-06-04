package com.example.demo.application.projection.strategy.tree.impl;

import org.springframework.stereotype.Component;

import com.example.demo.application.domain.dept.event.DepartmentCreatedEvent;
import com.example.demo.application.port.DepartmentTreeProjectionHandlerPort;
import com.example.demo.application.projection.strategy.tree.TreeProjectionStrategy;

import lombok.RequiredArgsConstructor;

/**
 * 部門建立策略 (Create Tree Strategy)
 *
 * <p>
 * 當新部門建立時，維護其在閉包表中的幾何路徑。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class CreateTreeStrategy implements TreeProjectionStrategy<DepartmentCreatedEvent> {

	private final DepartmentTreeProjectionHandlerPort treePort;

	@Override
	public Class<DepartmentCreatedEvent> supportedEvent() {
		return DepartmentCreatedEvent.class;
	}

	@Override
	public void execute(DepartmentCreatedEvent event) {
		// 1. 建立自我關聯 (Depth = 0)，這是任何節點存在於閉包表的基本條件
		treePort.insertSelfRelation(event.getTenantId(), event.getDepartmentId());

		// 2. 若不是根節點 (有 parentId)，則繼承父節點的所有路徑 (Depth = 父路徑 Depth + 1)
		if (event.getParentId() != null) {
			treePort.insertInheritedRelations(event.getTenantId(), event.getParentId(), event.getDepartmentId());
		}
	}
}