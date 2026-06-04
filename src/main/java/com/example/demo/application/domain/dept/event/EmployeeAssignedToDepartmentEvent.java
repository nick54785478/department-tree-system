package com.example.demo.application.domain.dept.event;

import com.example.demo.application.domain.shared.event.DomainEvent;

import lombok.Getter;

/**
 * EmployeeAssignedToDepartmentEvent (領域事件 - 員工指派加入部門事件)
 *
 * <pre>
 * 當組織內有人員調動、入職、指派新部門時，由部門聚合根觸發。 
 * 
 * <b>讀取端人數滾動統計核心 (Roll-up Counter)：</b>
 * 接收到此事件後，{@link DepartmentRollUpProjectionHandler} 會利用獨立事務進行高鐵級同步： 
 * 1. 單點遞增：將目標部門的 direct_headcount (直屬人數) 原子性 +1。 
 * 2. 幾何滾動：利用閉包表子查詢，一口氣將該部門「所有直系祖先組織」的 total_headcount (總人數) 批量 +1。
 * </pre>
 */
@Getter
public class EmployeeAssignedToDepartmentEvent extends DomainEvent {

	/**
	 * 目標被指派加入的部門唯一識別碼
	 */
	private final String departmentId;

	/**
	 * 被分配進來的員工唯一工號識別碼
	 */
	private final String employeeId;

	public EmployeeAssignedToDepartmentEvent(String tenantId, String departmentId, String employeeId, String operator) {
		super(tenantId, operator);
		this.departmentId = departmentId;
		this.employeeId = employeeId;
	}

	/**
	 * 反序列化專用無參建構子
	 */
	protected EmployeeAssignedToDepartmentEvent() {
		super();
		this.departmentId = null;
		this.employeeId = null;
	}

	@Override
	public String aggregateType() {
		return "Department";
	}

	@Override
	public String aggregateId() {
		return departmentId;
	}
}