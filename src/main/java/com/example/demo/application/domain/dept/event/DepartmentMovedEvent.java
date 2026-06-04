package com.example.demo.application.domain.dept.event;

import com.example.demo.application.domain.shared.event.DomainEvent;

import lombok.Getter;

/**
 * DepartmentMovedEvent (領域事件 - 部門組織拓撲移動事件)
 *
 * <pre>
 * 整個組織樹架構中幾何演算法最複雜、技術價值最高的事件。當部門變更父級從屬關係時觸發。 
 * <b>閉包表重組（CartesianProduct）：</b> 接收到此事件後，Projector 會驅動兩階段 SQL： 
 * 1. 斷尾：將此部門及其轄下子樹與所有「舊祖先」的路徑紀錄完全斬斷。 
 * 2. 重新掛載：讓此部門及其轄下子樹，批量繼承「新父部門」的所有直系祖先關係。
 * </pre>
 */
@Getter
public class DepartmentMovedEvent extends DomainEvent {

	/**
	 * 發生移動的部門 ID (即該受影響子樹的根節點)
	 */
	private final String departmentId;

	/**
	 * 移動前的舊直接父部門 ID (若原先為頂層 Root 則此欄位為 null)
	 */
	private final String oldParentId;

	/**
	 * 移動後的新直接父部門 ID (若移至最頂層升格為 Root 則此欄位為 null)
	 */
	private final String newParentId;

	/**
	 * 事件觸發時的幾何上下文根節點識別碼 (Root Context ID)。
	 * <p>
	 * 防禦性設計：用於在未來系統全域重播 (Rebuild/Replay)
	 * 或時光機回溯時，精確鎖定並限縮整棵幾何子樹的拓撲範圍，避免時鐘偏移或併發重播時找錯子樹 scope。
	 * </p>
	 */
	private final String subtreeRootId;

	public DepartmentMovedEvent(String tenantId, String departmentId, String oldParentId, String newParentId,
			String operator, String subtreeRootId) {
		super(tenantId, operator);
		this.departmentId = departmentId;
		this.oldParentId = oldParentId;
		this.newParentId = newParentId;
		this.subtreeRootId = subtreeRootId;
	}

	/** 🛡️ 反序列化專用無參建構子 */
	protected DepartmentMovedEvent() {
		super();
		this.departmentId = null;
		this.oldParentId = null;
		this.newParentId = null;
		this.subtreeRootId = null;
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