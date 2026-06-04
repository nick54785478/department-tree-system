package com.example.demo.application.shared.command;

/**
 * 停用部門 Command
 */
public record DisableDepartmentCommand(String tenantId, String departmentId, String operator) {
}