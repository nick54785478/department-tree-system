package com.example.demo.application.domain.dept.event;

import com.example.demo.application.domain.dept.aggregate.Department;
import com.example.demo.application.domain.shared.event.DomainEvent;

import lombok.Getter;

/**
 * DepartmentCreatedEvent (領域事件 - 部門建立事件)
 *
 * <pre>
 * 當系統成功建立一個全新部門（無論是根部門或子部門）時，由 {@link Department} 聚合根觸發並廣播此事件。 
 * 
 * <b>投影影響：</b>
 * 1. Tree Projector：在閉包表中為此節點建立自我參照記錄（Depth=0），若有父節點則繼承其所有祖先關係。 
 * 2. View Projector：在扁平視圖表中插入一筆基礎資料，並將狀態初始化為 "ACTIVE"。
 * </pre>
 */
@Getter
public class DepartmentCreatedEvent extends DomainEvent {

	/**
	 * 建立的部門唯一識別碼 (聚合根 ID)
	 */
	private final String departmentId;

	/**
	 * 直屬父部門 ID (若本身為頂層根部門則此欄位為 null)
	 */
	private final String parentId;

	/**
	 * 部門業務代碼 (例如: "HR-001")
	 */
	private final String code;

	/**
	 * 部門名稱
	 */
	private final String name;

	/**
	 * 全參數建構子。
	 */
	public DepartmentCreatedEvent(String tenantId, String departmentId, String parentId, String code, String name,
			String operator) {
		super(tenantId, operator); // 💡 將多租戶防護與操作者稽核軌跡交由基底類別統一維護
		this.departmentId = departmentId;
		this.parentId = parentId;
		this.code = code;
		this.name = name;
	}

	/**
	 * 反序列化防護：保留給 Jackson 序列化框架（JPA/EventStore）還原物件時使用的無參數建構子， 權限設為 protected
	 * 防止業務代碼不當呼叫，內部欄位一律給予 null 進行乾淨封裝。
	 */
	protected DepartmentCreatedEvent() {
		super();
		this.departmentId = null;
		this.parentId = null;
		this.code = null;
		this.name = null;
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