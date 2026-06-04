package com.example.demo.application.shared.command;

/**
 * 移動部門 Command (調整從屬關係)
 */
public record MoveDepartmentCommand(String tenantId, String departmentId, String newParentId, String operator) {
}