package com.example.demo.application.shared.command;

/**
 * 調整部門排序 Command
 */
public record ChangeDepartmentSortOrderCommand(String tenantId, String departmentId, int sortOrder, String operator) {
}