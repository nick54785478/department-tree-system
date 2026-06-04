package com.example.demo.application.domain.dept.event;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.service.DepartmentQueryService;

import lombok.Getter;

/**
 * DepartmentSortOrderChangedEvent (領域事件 - 部門前端顯示排序變更事件)
 *
 * <pre>
 * 當同一個層級下的部門進行 UI 上下拖拽、調整排序權重時觸發。 
 * 
 * <b>前端性能對齊：</b> 驅動 View Projector 更新視圖表的 sort_order 數值。 
 * 未來前端發出 GET 查詢樹狀圖時，應用服務層 {@link DepartmentQueryService}
 * 會依據此數值在記憶體中進行升序排序， 保證使用者看到的組織呈現順序與後台設定嚴格一致。
 * </pre>
 */
@Getter
public class DepartmentSortOrderChangedEvent extends DomainEvent {

	/**
	 * 調整排序權重的部門唯一識別碼
	 */
	private final String departmentId;

	/**
	 * 新的前端 UI 顯示排序數值 (通常數值越小，排在同級選單的最前面)
	 */
	private final int newSortOrder;

	public DepartmentSortOrderChangedEvent(String tenantId, String departmentId, int newSortOrder, String operator) {
		super(tenantId, operator);
		this.departmentId = departmentId;
		this.newSortOrder = newSortOrder;
	}

	/**
	 * 反序列化專用無參建構子
	 */
	protected DepartmentSortOrderChangedEvent() {
		super();
		this.departmentId = null;
		this.newSortOrder = 0; // 基礎型別重置為其預設值 0
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