package com.example.demo.application.domain.dept.event;

import com.example.demo.application.domain.shared.event.DomainEvent;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * DepartmentRestoredEvent (領域事件 - 部門時光機復活與復原事件)
 *
 * <pre>
 * 當一個曾經被邏輯刪除的部門，從地獄被管理員成功「復活、復原 (Undelete/Restore)」時觸發。
 * 
 * <b>架構高階亮點 - 事件豐富化 (Event Enrichment Pattern)：</b> 
 * 此事件除了攜帶當前重掛載的 parentId，更刻意豐富化封裝了該部門「死前生前的完整業務元數據」（code, name, status）。 
 * 
 * 這樣一來，讀取端的 Projector 在接到此事件進行重建時，<b>不需要再去撈取其他歷史事件做比對</b>， 憑藉這顆事件中豐富化的資料，就能在單次 SQL
 * 內將視圖表中的名稱、代碼與生前狀態（如原先是 ACTIVE 還是 DISABLED）完美恢復，效能極高！
 * </pre>
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class DepartmentRestoredEvent extends DomainEvent {

	/**
	 * 重新復活的部門唯一識別碼
	 */
	private final String departmentId;

	/**
	 * 復活時重掛載的父部門 ID (若因孤兒節點防禦觸發，此處會被修正並轉綁為頂層 Root 的 null)
	 */
	private final String parentId;

	/**
	 * 豐富化欄位：還原該部門前世被刪除前的「生前生命周期狀態」 (如 ACTIVE 或 DISABLED)
	 */
	private final String status;

	/**
	 * 豐富化欄位：還原該部門前世被刪除前的「生前業務代碼」
	 */
	private final String code;

	/**
	 * 豐富化欄位：還原該部門前世被刪除前的「生前部門顯示名稱」
	 */
	private final String name;

	/**
	 * 豐富化設計全參數建構子。
	 */
	public DepartmentRestoredEvent(String tenantId, String operator, String departmentId, String parentId,
			String status, String code, String name) {
		super(tenantId, operator);
		this.departmentId = departmentId;
		this.parentId = parentId;
		this.status = status;
		this.code = code;
		this.name = name;
	}

	/**
	 * 反序列化專用無參建構子
	 */
	protected DepartmentRestoredEvent() {
		super();
		this.departmentId = null;
		this.parentId = null;
		this.status = null;
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