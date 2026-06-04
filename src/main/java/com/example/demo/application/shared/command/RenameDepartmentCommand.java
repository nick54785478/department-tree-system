package com.example.demo.application.shared.command;

/**
 * 部門更名 Command
 */
public record RenameDepartmentCommand(String tenantId, String departmentId, String newName, String operator) {
}