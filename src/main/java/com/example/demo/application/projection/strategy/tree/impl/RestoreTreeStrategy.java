package com.example.demo.application.projection.strategy.tree.impl;

import org.springframework.stereotype.Component;

import com.example.demo.application.domain.dept.event.DepartmentRestoredEvent;
import com.example.demo.application.port.DepartmentTreeProjectionHandlerPort;
import com.example.demo.application.projection.strategy.tree.TreeProjectionStrategy;

import lombok.RequiredArgsConstructor;

/**
 * 部門復原策略 (Restore Tree Strategy)
 *
 * <p>
 * 當時光機觸發部門復活時，重建該部門在閉包表中的空間座標。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class RestoreTreeStrategy implements TreeProjectionStrategy<DepartmentRestoredEvent> {

	private final DepartmentTreeProjectionHandlerPort treePort;

	@Override
	public Class<DepartmentRestoredEvent> supportedEvent() {
		return DepartmentRestoredEvent.class;
	}

	@Override
	public void execute(DepartmentRestoredEvent event) {
		// 1. 重建自我關聯 (Depth = 0)
		treePort.insertSelfRelation(event.getTenantId(), event.getDepartmentId());

		// 2. 重新掛載：若原本不是根節點 (或復原時指定的安全父節點存在)，則繼承該父節點的所有路徑
		if (event.getParentId() != null) {
			treePort.insertInheritedRelations(event.getTenantId(), event.getParentId(), event.getDepartmentId());
		}
	}
}