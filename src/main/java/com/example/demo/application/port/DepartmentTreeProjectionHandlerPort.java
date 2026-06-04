package com.example.demo.application.port;

/**
 * Department Tree Projection Handler Port (寫入端 - 組織樹幾何投影合約)
 *
 * <pre>
 * 專責維護部門樹狀幾何結構的「閉包表 (Closure Table)」。 這是 CQRS 架構中寫入模型 (Write Model)
 * 變更成功後，驅動讀取端幾何路徑同步的基礎設施合約。 核心職責在於處理節點與節點間的祖先/子孫關係（幾何路徑的增刪改）。
 * 為了維護系統資料安全性，所有幾何操作都必須在同一個多租戶 (Tenant) 邊界內執行。
 * </pre>
 */
public interface DepartmentTreeProjectionHandlerPort {

	/**
	 * 寫入任兩個節點之間的父子或祖孫路徑關聯。
	 * <p>
	 * 用於手動構建或修補特定階層結構時使用。
	 * </p>
	 *
	 * @param tenantId     租戶識別碼，確保多租戶物理或邏輯隔離
	 * @param ancestorId   祖先節點的唯一識別碼 (上層部門)
	 * @param descendantId 子孫節點的唯一識別碼 (下層部門)
	 * @param depth        兩節點之間的階層深度距離 (直屬父子為 1，隔一代為 2，依此類推)
	 */
	void insertRelation(String tenantId, String ancestorId, String descendantId, int depth);

	/**
	 * 寫入節點的自我參照關聯 (深度為 0)。
	 * <p>
	 * 這是 Closure Table 演算法的核心基石。確保每個節點在進行子樹遍歷或統計查詢時， 都能透過自交 (Self Join) 把自己也算進去。
	 * </p>
	 *
	 * @param tenantId 租戶識別碼
	 * @param id       部門節點的唯一識別碼
	 */
	void insertSelfRelation(String tenantId, String id);

	/**
	 * 斷開特定節點及其整棵子樹與「所有舊祖先」的物理關聯。
	 * <p>
	 * 此操作為部門移動 (Move Department) 核心演算法的第一階段。
	 * 它會保留該子樹內部的既有幾何結構，但將整棵子樹從原有的上層組織結構中完全拔除（架空）。
	 * </p>
	 *
	 * @param tenantId     租戶識別碼
	 * @param descendantId 被移動的部門節點 ID (即該受影響子樹的根節點)
	 */
	void deleteRelationsByDescendant(String tenantId, String descendantId);

	/**
	 * 讓特定節點（或整棵子樹）非同步繼承新父節點的所有祖先路徑。
	 * <p>
	 * 此操作為部門建立 (Create) 或部門移動 (Move) 演算法的第二階段。
	 * 透過笛卡爾乘積原理，將新老爸的所有直系祖先路徑，批量複製給該節點及其底下的所有子孫，完成組織樹的重組掛載。
	 * </p>
	 *
	 * @param tenantId 租戶識別碼
	 * @param parentId 新的父部門唯一識別碼
	 * @param childId  當前被掛載的部門 ID (或被移動的子樹根節點 ID)
	 */
	void insertInheritedRelations(String tenantId, String parentId, String childId);

	/**
	 * 物理清除該部門在路徑表中的所有關係紀錄。
	 * <p>
	 * 不論該節點是作為他人的「祖先」還是他人的「子孫」，其相關幾何路徑皆會被全數抹除。 專門用於部門刪除 (Delete) Use Case 的唯讀端結構清理。
	 * </p>
	 *
	 * @param tenantId 租戶識別碼
	 * @param id       欲抹除關係的部門唯一識別碼
	 */
	void deleteNodeAndRelations(String tenantId, String id);
}