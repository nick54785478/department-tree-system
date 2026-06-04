package com.example.demo.infra.projection;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Department Employee View (唯讀端 - 部門與員工關聯投影表)
 *
 * <pre>
 * <b>架構定位 (CQRS Read Model)：</b> 這是一張純粹為了「極速讀取」而生的扁平化視圖表。 它不具備任何業務行為，
 * 完全由領域事件 (Domain Events) 在背景異步/同步驅動更新。 
 * 
 * <b>索引優化：</b> 針對 RBAC 系統最常問的問題：「這個 employeeId 屬於哪些部門？」， 
 * 我們在 `employee_id` 與 `tenant_id` 上建立了複合索引，讓查詢效能達到 O(1) 等級。
 * </pre>
 */
@Entity
@Table(name = "department_employees_view", indexes = {
		// 專為 RBAC 反查設計的極速索引
		@Index(name = "idx_view_tenant_employee", columnList = "tenant_id, employee_id"),
		// 專為列出部門下所有員工設計的索引
		@Index(name = "idx_view_tenant_department", columnList = "tenant_id, department_id") })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepartmentEmployeeView {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@Column(name = "department_id", nullable = false, length = 50)
	private String departmentId;

	@Column(name = "employee_id", nullable = false, length = 50)
	private String employeeId;

	@Column(name = "assigned_at", nullable = false)
	private Instant assignedAt;

	/**
	 * 投影表專用建構子
	 */
	public DepartmentEmployeeView(String tenantId, String departmentId, String employeeId, Instant assignedAt) {
		this.tenantId = tenantId;
		this.departmentId = departmentId;
		this.employeeId = employeeId;
		this.assignedAt = assignedAt;
	}
}