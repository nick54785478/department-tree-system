package com.example.demo.application.shared.dto;

import java.util.List;

/**
 * 部門上下層編制與階層關係視圖 (CQRS Read Model - Department Hierarchy Resource)
 *
 * <p>
 * 支援縱向無限階層延伸，下屬分支結構已全面升級為遞迴巢狀樹狀結構。
 * </p>
 */
public record DepartmentHierarchyGottenResult(DepartmentSummaryResource currentDepartment, List<String> currentEmployees,
		DepartmentSummaryResource parentDepartment, List<String> parentEmployees,
		List<ChildDepartmentNodeResource> childDepartments // 🌟 內部已升級為巢狀樹
) {

	public record DepartmentSummaryResource(String id, String code, String name, String status) {
	}

	/**
	 * 下屬部門節點 (🌟 遞迴結構)
	 */
	public record ChildDepartmentNodeResource(DepartmentSummaryResource department, List<String> employees,
			List<ChildDepartmentNodeResource> children // 🌟 透過自我參照，支援向下無限延伸
	) {
	}
}