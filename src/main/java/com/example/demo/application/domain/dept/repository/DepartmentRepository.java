package com.example.demo.application.domain.dept.repository;

import java.util.List;
import java.util.Optional;

import com.example.demo.application.domain.dept.aggregate.Department;
import com.example.demo.application.domain.dept.aggregate.vo.DepartmentId;
import com.example.demo.application.domain.dept.aggregate.vo.TenantId;

/**
 * Department Repository Port (領域層/寫入端 - 部門領域倉儲接口 Port)
 *
 * <pre>
 * 負責定義部門聚合根（Department Aggregate）生命週期管理（儲存、檢索、校驗）的抽象業務合約。
 * 
 * <b>六角架構與整潔架構原則 (Hexagonal Core Architecture)：</b> 
 * 1. <b>依賴反轉原則 (DIP)：</b> 本介面立足於領域核心的最內層，<b>絕對禁止依賴、引入或 import 任何特定基礎設施框架技術套件</b> （如 Spring Data JPA 的 @Repository、 JpaRepository } 或 Hibernate 相關註解）。 
 * 2. <b>強型別隔離：</b> 所有入參與出參皆以領域值物件（TenantId, DepartmentId）與充血模型為主體，徹底隔絕裸字串（String）帶來的隱性業務漏洞。 
 * 3. <b>租戶安全死線：</b> 除特殊背景排程或審計用途外，所有合約操作皆強烈要求以「租戶 (Tenant)」為第一隔離維度，確保多租戶邊界的絕對一致性。
 * </pre>
 */
public interface DepartmentRepository {

	/**
	 * 檢查指定租戶邊界下，該部門 ID 是否已重複存在。
	 * <p>
	 * 核心場景：專供 {@code DepartmentCommandService#createDepartment} 建立新部門前，執行不可變
	 * Aggregate ID 的唯一性校驗 (Duplicate Check)。
	 * </p>
	 *
	 * @param tenantId 租戶的唯一識別值物件
	 * @param id       欲檢查的部門唯一識別值物件
	 * @return {@code true} 代表已被佔用；{@code false} 代表該 ID 安全可用
	 */
	boolean existsByTenantIdAndId(TenantId tenantId, DepartmentId id);

	/**
	 * 透過部門 ID 全域尋找部門聚合根 (跨租戶全域尋找通道)。
	 * 
	 * <pre>
	 * <b>高風險運維警告 (Cross-Tenant Dynamic Escape)：</b>
	 * 此方法<b>刻意未包含</b>租戶邊界隔離。在常規業務使用案例中呼叫此方法會埋下嚴重的跨租戶越權漏洞。 
	 * 本合約通常<b>僅限於</b>系統底層背景排程（如 Outbox 異步派發器）、非同步補償任務、或是具備超級管理員上下文的全域審計情境。 
	 * 常規業務端操作請務必優先調用具備邊界防禦的 {@link #findByTenantIdAndId}。
	 * </pre>
	 *
	 * @param id 部門唯一識別值物件
	 * @return 封裝了部門聚合根實體的 Optional 容器；若無此記錄則回傳 {@code Optional.empty()}
	 */
	Optional<Department> findById(DepartmentId id);

	/**
	 * 透過 TenantId 和 Id 尋找特定租戶下的部門聚合根。
	 * 
	 * <pre>
	 * <b>首選聚合載入防線 (Horizontal Privilege Escalation Defense)：</b>
	 * 具備強制性的租戶水平越權防禦，為 Command Service 載入並修改聚合根狀態時的「首選方法」， 從底層直接杜絕直接對象參照越權 (IDOR) 漏洞。
	 * </pre>
	 *
	 * @param tenantId 租戶的唯一識別值物件
	 * @param id       部門唯一識別值物件
	 * @return 封裝了部門聚合根實體的 Optional 容器
	 */
	Optional<Department> findByTenantIdAndId(TenantId tenantId, DepartmentId id);

	/**
	 * 透過多個 Id 批次載入部門列表，並強制保證它們皆隸屬於同一個指定租戶。
	 * 
	 * <pre>
	 * 主要用於跨多個聚合根進行連鎖修改的操作場景。 例如：執行子樹邏輯刪除（Delete Subtree）或批次停用時，
	 * 應用層需將該幾何範疇下的所有子孫實體一次性載入記憶體中， 依序觸發其實體內部的業務行為，進而產生並累積對應的領域事件流。
	 * </pre>
	 *
	 * @param tenantId 租戶的唯一識別值物件
	 * @param ids      欲批量載入的部門強型別 ID 值物件清單
	 * @return 符合多租戶條件的部門聚合根實體集合，若皆無匹配則回傳空列表
	 */
	List<Department> findAllByTenantIdAndIds(TenantId tenantId, List<DepartmentId> ids);

	/**
	 * 儲存部門聚合根的最新變更狀態 (包含全新新增與樂觀鎖更新)。
	 * 
	 * <pre>
	 * <b>事件驅動連鎖約定 (Domain Event Publication Trigger)：</b> 當此方法被基礎設施層的 Adapter
	 * 實現並成功 Commit 時，底層持久化框架應同步攔截並擷取該實體內部 透過 {@code @DomainEvents} 累積的暫存事件流，
	 * 將其原子性存入 event_store 與發件匣（outbox_events）表中， 成為驅動 CQRS 唯讀端投影大同步與外部 MQ 分發的源頭動力。
	 * </pre>
	 *
	 * @param department 欲執行持久化儲存的部門聚合根充血實體
	 * @return 儲存後、獲取到最新資料庫樂觀鎖版本號（version）的部門聚合根實體
	 */
	Department save(Department department);

	/**
	 * 查詢指定部門底下的所有子孫節點 ID 列表 (包含自身幾何範圍)。
	 * 
	 * <pre>
	 * <b>幾何空間效能優化約定 (Tree Traversal Performance Guarantee)：</b>
	 * 雖然此合約定義在純潔的領域層，但基礎設施層的實作 Adapter 應充分發揮讀寫分離優勢， 直接調用並利用高性能的唯讀端 <b>幾何閉包表 (Closure
	 * Table)</b> 執行單次原生 SQL 查詢完成樹狀子樹打平遍歷。 這徹底消滅了將複雜、高開銷的樹狀遞迴或 CTE 運算拖入 JVM
	 * 記憶體處理的技術大坑，確保寫入端架構的高度輕量。
	 * </pre>
	 *
	 * @param tenantId 租戶的唯一識別值物件
	 * @param rootId   欲遍歷子樹的起點根節點部門 ID 值物件
	 * @return 該子樹下所有受影響的部門 ID 強型別值物件清單
	 */
	List<DepartmentId> findSubtreeIds(TenantId tenantId, DepartmentId rootId);
	
	/**
	   * 拓撲防禦專用：檢查節點 A 是否為節點 B 的祖先。
	   * <p>
	   * 專門在部門合併 (Merge) 或移動 (Move) 時使用，防止發生「把老爸併入兒子」的循環結構 (Cyclic Graph) 崩潰。
	   * </p>
	   * * @param tenantId     租戶識別值物件
	   * @param ancestorId   可能為祖先的部門 ID
	   * @param descendantId 可能為子孫的部門 ID
	   * @return 若具備祖先關係則回傳 true
	   */
	  boolean isAncestor(TenantId tenantId, DepartmentId ancestorId, DepartmentId descendantId);

	  /**
	   * 找出某個部門的「直屬第一代」子部門聚合根。
	   * <p>用於合併 (Merge) 或拆分 (Split) 部門時，將來源部門底下的第一層子部門，全部過繼掛載給目標部門。</p>
	   * * @param tenantId 租戶識別值物件
	   * @param parentId 父部門 ID
	   * @return 直屬子部門聚合根列表
	   */
	  List<Department> findDirectChildren(TenantId tenantId, DepartmentId parentId);
}