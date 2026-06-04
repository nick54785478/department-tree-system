package com.example.demo.application.domain.dept.aggregate.vo;

/**
 * 部門生命週期狀態
 */
public enum DepartmentStatus {
	/**
	 * 啟用中：可進行人員分派與架構移動
	 */
	ACTIVE,

	/**
	 * 停用中：不可分派新人員，不可成為新部門的父節點
	 */
	DISABLED
}