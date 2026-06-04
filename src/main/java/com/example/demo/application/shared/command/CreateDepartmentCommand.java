package com.example.demo.application.shared.command;

/**
 * 建立部門 Command
 */
public record CreateDepartmentCommand(String tenantId, String id, String parentId, // 允許為 null (代表建立根節點)
		String code, String name, String operator) {
}
