package com.example.demo.application.shared.dto;

import java.time.Instant;
import java.util.List;

import com.example.demo.application.domain.dept.event.DepartmentCreatedEvent;
import com.example.demo.application.domain.dept.event.DepartmentDeletedEvent;
import com.example.demo.application.domain.dept.event.DepartmentDisabledEvent;
import com.example.demo.application.domain.dept.event.DepartmentMovedEvent;
import com.example.demo.application.domain.dept.event.DepartmentRenamedEvent;
import com.example.demo.application.domain.dept.event.DepartmentRestoredEvent;
import com.example.demo.application.domain.dept.event.DepartmentSortOrderChangedEvent;
import com.example.demo.application.domain.shared.event.DomainEvent;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Department Temporal State (時光機 - 部門動態歷史投影狀態模型)
 *
 * <pre>
 * 這是系統實踐事件溯源 (Event Sourcing) 與時光機機制的靈魂物件。 
 * 
 * 充血動態投影模型： 
 * 本類別是一個典型的「記憶體狀態重播模型」。
 * 它不對應任何寫入端實體表，其唯一職責就是作為一個白紙容器，接收從古至今、按時間順序排列的歷史事件流 (Event Stream)， 
 * 並透過內部的 apply 方法進行狀態流轉 (State Mutation)，進而精準還原任意歷史時刻的部門樣貌。
 * </pre>
 */
@Data
@NoArgsConstructor
public class DepartmentTemporalState {

	/**
	 * 租戶識別碼
	 */
	private String tenantId;

	/**
	 * 部門唯一識別碼
	 */
	private String id;

	/**
	 * 歷史當下的父部門識別碼 (隨移動事件動態變更)
	 */
	private String parentId;

	/**
	 * 部門代碼
	 */
	private String code;

	/**
	 * 部門顯示名稱 (隨更名事件動態變更)
	 */
	private String name;

	/**
	 * 歷史當下的生命週期狀態 (ACTIVE, DISABLED 等)
	 */
	private String status;

	/**
	 * 歷史當下的排序權重
	 */
	private int sortOrder;

	/**
	 * 邏輯刪除標記 (隨刪除與復原事件在 true/false 間流轉)
	 */
	private boolean deleted;

	/**
	 * 歷史軌跡：最後一次修改此狀態的精確歷史時間戳記
	 */
	private Instant lastModifiedAt;

	/**
	 * 歷史軌跡：最後一次修改此狀態的歷史操作者識別碼
	 */
	private String lastModifiedBy;

	/**
	 * 時光機引擎核心：將事件依序套用以完整重建歷史狀態。
	 * <p>
	 * 演算法起點：若歷史事件流為空，代表此部門聚合根在目標時間點「尚未出生」，直接優雅回傳 null。
	 * </p>
	 *
	 * @param history 按全域位置或時間戳記嚴格**由小到大(升序)**排列的不可變歷史事件流清單
	 * @return 重建完畢的部門歷史狀態模型；若尚未誕生則回傳 {@code null}
	 */
	public static DepartmentTemporalState rebuild(List<DomainEvent> history) {
		if (history == null || history.isEmpty()) {
			return null; // 代表在該時間點之前，這個部門根本還沒誕生
		}

		DepartmentTemporalState state = new DepartmentTemporalState();
		for (DomainEvent event : history) {
			state.apply(event);
		}
		return state;
	}

	/**
	 * 根據不同的事件類型，利用多型或條件分派改變當前狀態不變量 (Pattern Matching Strategy)。
	 * <p>
	 * 此處採用 Java 17+ 的 Pattern Matching for switch 技術，提供了極高可讀性且型別安全的事件流轉矩陣。
	 * </p>
	 *
	 * @param event 繼承自 {@code DomainEvent} 的具體歷史業務事件
	 */
	public void apply(DomainEvent event) {
		// 每次流轉皆更新審計歷史軌跡，忠實記錄這一步是誰在什麼時候造成的
		this.lastModifiedAt = event.getOccurredAt();
		this.lastModifiedBy = event.getOperator();

		switch (event) {
		// 1. 出生事件：初始化所有基礎屬性欄位
		case DepartmentCreatedEvent e -> {
			this.tenantId = e.getTenantId();
			this.id = e.getDepartmentId();
			this.parentId = e.getParentId();
			this.code = e.getCode();
			this.name = e.getName();
			this.status = "ACTIVE";
			this.sortOrder = 0;
			this.deleted = false;
		} // 2. 更名事件
		case DepartmentRenamedEvent e -> this.name = e.getNewName();

		// 3. 移動事件：變更父節點指向 (樹狀拓撲變更)
		case DepartmentMovedEvent e -> this.parentId = e.getNewParentId();

		// 4. 停用事件
		case DepartmentDisabledEvent e -> this.status = "DISABLED";

		// 5. 排序變更事件
		case DepartmentSortOrderChangedEvent e -> this.sortOrder = e.getNewSortOrder();

		// 6. 邏輯刪除事件：將狀態翻轉為已刪除
		case DepartmentDeletedEvent e -> this.deleted = true;

		// 7. 復活事件：當時光倒流或系統管理員執行復原時，把自己從邏輯刪除的地獄拉回來
		case DepartmentRestoredEvent e -> {
			this.deleted = false;
			this.parentId = e.getParentId(); // 💡 重新掛回事件豐富化中攜帶的「安全父節點 ID」 (防禦孤兒節點)
			this.status = e.getStatus(); // 💡 關鍵優化：精準還原為事件中綁定的「前世生前狀態」(如 ACTIVE 或 DISABLED)
		}

		default -> {
			// 職責解耦：像是 EmployeeAssignedToDepartmentEvent 這種「人進來、人出去」的事件，
			// 只影響讀取端人數計數，並不影響部門本身基本結構（如名稱、代碼）。
			// 因此在此純部門基本狀態視圖中，可以直接優雅忽略 (Ignore)，不進行任何 Mutation。
		}
		}
	}
}