package com.example.demo.application.projection.strategy.tree.impl;

import org.springframework.stereotype.Component;

import com.example.demo.application.domain.dept.event.DepartmentMovedEvent;
import com.example.demo.application.port.DepartmentTreeProjectionHandlerPort;
import com.example.demo.application.projection.strategy.tree.TreeProjectionStrategy;

import lombok.RequiredArgsConstructor;

/**
 * 部門移動策略 (Move Tree Strategy)
 *
 * <p>
 * 當部門變更父節點時，執行閉包表中最複雜的「子樹轉移」演算法。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class MoveTreeStrategy implements TreeProjectionStrategy<DepartmentMovedEvent> {

	private final DepartmentTreeProjectionHandlerPort treePort;

	@Override
	public Class<DepartmentMovedEvent> supportedEvent() {
		return DepartmentMovedEvent.class;
	}

	@Override
	public void execute(DepartmentMovedEvent event) {
		// 1. 斷尾求生：從舊的祖先樹中完全斷開 (但完美保留被移動子樹內部的幾何結構)
		treePort.deleteRelationsByDescendant(event.getTenantId(), event.getDepartmentId());

		// 2. 重新掛載：若移到新的父節點底下 (非移至 Root)，則讓整棵子樹繼承新父節點的所有祖先路徑
		if (event.getNewParentId() != null) {
			treePort.insertInheritedRelations(event.getTenantId(), event.getNewParentId(), event.getDepartmentId());
		}
	}
}