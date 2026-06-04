package com.example.demo.application.shared.command;

/**
 * 將員工分派至部門 Command
 */
public record AssignEmployeeCommand(String tenantId, String departmentId, String employeeId, String operator) {
}