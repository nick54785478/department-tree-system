package com.example.demo.application.shared.command;

/**
 * 刪除部門 Command (包含刪除其轄下整個子樹)
 */
public record DeleteDepartmentCommand(String tenantId, String departmentId, String operator) {
}