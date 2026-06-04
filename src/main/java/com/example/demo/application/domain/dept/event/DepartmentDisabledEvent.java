package com.example.demo.application.domain.dept.event;

import com.example.demo.application.domain.shared.event.DomainEvent;

import lombok.Getter;

/**
 * DepartmentDisabledEvent (領域事件 - 部門業務停用事件)
 *
 * <pre>
 * 當部門遭到行政停用（例如部門裁撤過渡期、凍結編制）時由聚合根觸發。 
 * 
 * <b>投影影響：</b> 驅動 View Projector
 * 將視圖表的狀態欄位變更為 {@code 'DISABLED'}
 * 
 * 此事件與刪除不同，它<b>不會</b>改變閉包表中的組織樹狀幾何路徑連結，該部門依然存在於階層架構中，僅狀態反灰。
 * </pre>
 */
@Getter
public class DepartmentDisabledEvent extends DomainEvent {

	/**
	 * 遭停用的部門唯一識別碼
	 */
	private final String departmentId;

	public DepartmentDisabledEvent(String tenantId, String departmentId, String operator) {
		super(tenantId, operator);
		this.departmentId = departmentId;
	}

	/** 反序列化專用無參建構子 */
	protected DepartmentDisabledEvent() {
		super();
		this.departmentId = null;
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