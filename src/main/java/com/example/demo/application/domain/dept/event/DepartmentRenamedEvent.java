package com.example.demo.application.domain.dept.event;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.projection.projector.DepartmentTreeProjector;

import lombok.Getter;

/**
 * DepartmentRenamedEvent (領域事件 - 部門更名事件)
 *
 * <pre>
 * 當部門顯示名稱發生變更時觸發。 
 * <b>唯讀端同步：</b> 驅動 View Projector 即時更新扁平視圖表的 name 欄位。 
 * 
 * 此事件完全不關心組織樹狀幾何拓撲，因此 {@link DepartmentTreeProjector} 在收到此事件時會進行優雅降級（忽略處理）。
 * </pre>
 */
@Getter
public class DepartmentRenamedEvent extends DomainEvent {

	/**
	 * 發生更名的部門唯一識別碼
	 */
	private final String departmentId;

	/**
	 * 精過檢驗與去背後的新部門顯示名稱
	 */
	private final String newName;

	public DepartmentRenamedEvent(String tenantId, String departmentId, String newName, String operator) {
		super(tenantId, operator);
		this.departmentId = departmentId;
		this.newName = newName;
	}

	/**
	 * 反序列化專用無參建構子
	 */
	protected DepartmentRenamedEvent() {
		super();
		this.departmentId = null;
		this.newName = null;
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