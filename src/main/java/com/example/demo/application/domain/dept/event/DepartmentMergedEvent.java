package com.example.demo.application.domain.dept.event;

import com.example.demo.application.domain.shared.event.DomainEvent;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 領域事件：部門已合併 (Domain Event - Department Merged)
 *
 * <pre>
 * <b>架構與業務語意：</b>
 * 當一個來源部門（Source）被完全整併至另一個目標部門（Target），並結束自身業務生命週期時，由來源部門的聚合根發布此事件。
 * 
 * 這是該部門生命週期中的「終結事件 (Terminal Event)」。 
 * <b>資料倉儲與時光機價值 (Data Warehouse & Time-Travel)：</b> 
 * 在事件溯源 (Event Sourcing) 架構中，此事件具備極高的分析含金量。
 * 它不僅代表了部門實體的「停用」，更在時間軸上明確標示了「組織血脈與資產（人員、轄下單位）的最終去向」。 
 * 當未來需要繪製「組織演進歷程樹 (Org Evolution Tree)」或追溯歷史斷層時，這個事件將是銜接兩棵樹的關鍵紐帶。
 * </pre>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 🌟 專供 Jackson 反序列化使用的無參建構子 (利用反射繞過強型別校驗)
public class DepartmentMergedEvent extends DomainEvent {

	/**
	 * 被整併（消滅）的來源部門 ID (Source Department)
	 * <p>
	 * 作為本次合併行為的主體，它的狀態將在此事件發布後轉為 DISABLED。
	 * </p>
	 */
	private String departmentId;

	/**
	 * 吸收資產的目標部門 ID (Target Department)
	 * <p>
	 * 繼承了來源部門所有直屬子部門與人員編制的存續部門。
	 * </p>
	 */
	private String targetDepartmentId;

	/**
	 * 寫入端業務建構子 (Command-Side Constructor)
	 * <p>
	 * 供 {@code Department} 聚合根在執行 {@code markAsMergedInto} 時實例化使用。
	 * </p>
	 *
	 * @param tenantId           多租戶識別碼 (Tenant Boundary)
	 * @param operator           執行此合併重組操作的管理員 ID
	 * @param departmentId       被消滅的來源部門 ID
	 * @param targetDepartmentId 吸收資產的目標部門 ID
	 */
	public DepartmentMergedEvent(String tenantId, String operator, String departmentId, String targetDepartmentId) {
		super(tenantId, operator); // 呼叫父類別，自動生成具備絕對時序的 eventId 與 occurredAt
		this.departmentId = departmentId;
		this.targetDepartmentId = targetDepartmentId;
	}

	// ==========================================
	// 實作父類別強制要求的聚合元數據 (Aggregate Metadata)
	// ==========================================

	/**
	 * 宣告發布此事件的聚合根類型。
	 * <pre>
	 * 供 Event Store 儲存時作為 Stream 分類標籤使用。
	 * </pre>
	 */
	@Override
	public String aggregateType() {
		return "Department";
	}

	/**
	 * 宣告發布此事件的聚合根 ID。
	 * <pre>
	 * <b>架構標定：</b> 由於是「來源部門」宣告自己被合併並結束生命， 因此這裡綁定的 Aggregate ID 必須是來源部門的
	 * ID，而非目標部門。
	 * </pre>
	 */
	@Override
	public String aggregateId() {
		return this.departmentId;
	}
}
