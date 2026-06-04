package com.example.demo.application.domain.dept.event;

import com.example.demo.application.domain.shared.event.DomainEvent;

import lombok.Getter;

/**
 * EmployeeUnassignedFromDepartmentEvent (領域事件 - 員工移出/解除指派部門事件)
 *
 * <pre>
 * 當員工離職、調職、從原本從屬的部門拔除時觸發。
 * <b>反向人數統計扣減：</b> 與
 * {@link EmployeeAssignedToDepartmentEvent} 運作機理完全相同、方向相反。 
 * 驅動統計投影處理器將目標部門的 direct_headcount 執行 -1，
 * 並通過幾何閉包表 將其上層所有直系老爸、老爹的組織 total_headcount 一路向上批量扣減 -1。
 * </pre>
 */
@Getter
public class EmployeeUnassignedFromDepartmentEvent extends DomainEvent {

	/**
	 * 執行員工移出的原所屬部門唯一識別碼
	 */
	private final String departmentId;

	/**
	 * 被移出組織的員工唯一工號識別碼
	 */
	private final String employeeId;

	public EmployeeUnassignedFromDepartmentEvent(String tenantId, String departmentId, String employeeId,
			String operator) {
		super(tenantId, operator);
		this.departmentId = departmentId;
		this.employeeId = employeeId;
	}

	/**
	 * 反序列化專用無參建構子
	 */
	protected EmployeeUnassignedFromDepartmentEvent() {
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