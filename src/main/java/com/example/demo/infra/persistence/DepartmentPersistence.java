package com.example.demo.infra.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.application.domain.dept.aggregate.Department;
import com.example.demo.application.domain.dept.aggregate.vo.DepartmentId;
import com.example.demo.application.domain.dept.aggregate.vo.TenantId;

/**
 * 組織部門 JPA 持久化介面 (Infrastructure Layer - Database Access)
 *
 * <pre>
 * <b>架構定位：</b> 專責與資料庫進行底層關聯式數據溝通，繼承 {@link JpaRepository} 取得強大的自動化 HQL/JPQL
 * 與 CRUD 能力。 
 * 
 * <b>架構隔離原則 (Hexagonal Architecture)：</b> 這是寫入端 (Write Model / Command Side) 聚合根的專屬基礎設施儲存庫。
 * 
 * 在純整潔架構下， 上層核心領域與應用服務絕對不可感知此介面的存在，其生存空間被完全收攏在 Infrastructure 層內的 Adapter 內部。
 * </pre>
 */
@Repository
public interface DepartmentPersistence extends JpaRepository<Department, DepartmentId> {

	/**
	 * 檢查特定租戶下的部門是否存在。
	 * <p>
	 * 用於等冪性校驗或建立前的唯一性檢查。
	 * </p>
	 *
	 * @param tenantId 租戶 ID 強型別值物件
	 * @param id       部門 ID 強型別值物件
	 * @return {@code true} 代表存在；{@code false} 代表不存在
	 */
	boolean existsByTenantIdAndId(TenantId tenantId, DepartmentId id);

	/**
	 * 透過 TenantId 和 Id 尋找部門 (多租戶安全防護查詢)。
	 * <p>
	 * 將租戶識別做為第一查詢維度，為防禦寫入端越權存取 (IDOR) 的核心底層防線。
	 * </p>
	 *
	 * @param tenantId 租戶 ID 強型別值物件
	 * @param id       部門 ID 強型別值物件
	 * @return 封裝了部門實體的 Optional
	 */
	Optional<Department> findByTenantIdAndId(TenantId tenantId, DepartmentId id);

	/**
	 * 批次載入特定租戶下的多個部門實體。
	 * <p>
	 * 通常用於子樹批量停用或連鎖變更時，將目標節點一次性載入 Hibernate Persistence Context 中， 方便進行領域狀態演進與事件觸發。
	 * </p>
	 *
	 * @param tenantId 租戶 ID 強型別值物件
	 * @param ids      部門 ID 強型別值物件清單
	 * @return 符合條件的部門實體列表，若皆不符合則回傳空列表
	 */
	List<Department> findAllByTenantIdAndIdIn(TenantId tenantId, List<DepartmentId> ids);

	/**
	 * 查詢子樹 ID 列表 (利用 Closure Table 幾何投影優化)。
	 * 
	 * <pre>
	 * <b>架構級精妙設計 (Pragmatic CQRS Boundary Cross)：</b> 雖然這是 Write Model 的
	 * Persistence 介面，但為了在刪除或停用子樹時，應用層能極速撈出所有的子孫節點 ID， 這裡直接透過 Native SQL 跨界向基礎設施層的
	 *department_tree 閉包表進行單表快查。 這在實務的 CQRS 架構中是極度推薦的優化手段。它打破了鄰接清單
	 * (Adjacency List) 需要用 Java 遞迴 或資料庫遞迴 (CTE) 的效能枷鎖，用單次 $O(1)$ 的 SQL 徹底消滅了記憶體 N+1
	 * 的效能炸彈。
	 * </pre>
	 *
	 * @param tenantId 租戶 ID 業務字串
	 * @param rootId   子樹的起點根節點部門 ID 業務字串
	 * @return 該子樹下所有子孫節點（包含自身）的部門 ID 字串列表
	 */
	@Query(value = """
			SELECT descendant_id
			FROM department_tree
			WHERE tenant_id = :tenantId
			  AND ancestor_id = :rootId
			""", nativeQuery = true)
	List<String> findSubtreeIds(@Param("tenantId") String tenantId, @Param("rootId") String rootId);

	/**
	 * 拓撲防禦底層查詢：檢查特定租戶下，祖先與子孫的幾何路徑是否存在。
	 * 
	 * <pre>
	 * <b>防呆防禦線：</b> 專門在部門合併 (Merge) 或移動 (Move) 時使用。 同樣利用原生 SQL 直攻
	 * department_tree 閉包表，單次 O(1) 暴力快查， 徹底防堵「將老爸部門併入兒子部門」這種會導致系統無限迴圈的循環結構(Cyclic Graph)。
	 * </pre>
	 * 
	 * @param tenantId     租戶 ID 業務字串
	 * @param ancestorId   可能為祖先的部門 ID 業務字串
	 * @param descendantId 可能為子孫的部門 ID 業務字串
	 * @return 若具備祖先路徑關聯則回傳 {@code true}
	 */
	@Query(value = """
			SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END
			FROM department_tree
			WHERE tenant_id = :tenantId
			  AND ancestor_id = :ancestorId
			  AND descendant_id = :descendantId
			""", nativeQuery = true)
	boolean isAncestor(@Param("tenantId") String tenantId, @Param("ancestorId") String ancestorId,
			@Param("descendantId") String descendantId);

	/**
	 * 尋找直屬子部門。
	 * 
	 * <pre>
	 * parentId 屬性， 這裡直接利用方法名推導 (Method Name Query Derivation)，Hibernate
	 * 會自動生成動態 JPQL， 完全不需手寫 SQL，主要用於轉移/過繼直屬子部門時使用。
	 * </pre>
	 * 
	 * * @param tenantId 租戶 ID 強型別值物件
	 * 
	 * @param parentId 父部門 ID 強型別值物件
	 * @return 符合條件的直屬子部門實體列表
	 */
	List<Department> findByTenantIdAndParentId(TenantId tenantId, DepartmentId parentId);

	// 🌟 註：原有的 findEmployeeIds 已被徹底刪除。
	// 因為人員名單的取得，已升級為透過 EventStore 在 Application Service 中進行記憶體事件重播 (Event Replay)。
}