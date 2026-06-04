package com.example.demo.application.domain.dept.event;

import com.example.demo.application.domain.shared.event.DomainEvent;

import lombok.Getter;

/**
 * DepartmentDeletedEvent (領域事件 - 部門邏輯刪除事件)
 *
 * <pre>
 * 當某個部門聚合根（包含其整棵幾何子樹）執行邏輯刪除行為時觸發。 
 * 
 * <b>時光機特殊約定：</b> 雖然在寫入端實體與讀取端視圖表中是以 {@code status = 'DELETED'}邏輯隱藏該節點以供歷史時光機追溯復活，
 * 但在樹狀結構閉包表中會將此節點與其他組織的幾何關係「物理抹除」，切斷其空間牽連。
 * </pre>
 */
@Getter
public class DepartmentDeletedEvent extends DomainEvent {

	/**
	 * 遭到邏輯刪除的部門唯一識別碼
	 */
	private final String departmentId;

	public DepartmentDeletedEvent(String tenantId, String departmentId, String operator) {
		super(tenantId, operator);
		this.departmentId = departmentId;
	}

	/**
	 * 反序列化專用無參建構子
	 */
	protected DepartmentDeletedEvent() {
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