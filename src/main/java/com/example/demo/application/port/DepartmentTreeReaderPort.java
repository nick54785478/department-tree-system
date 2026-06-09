package com.example.demo.application.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.demo.infra.shared.dto.DepartmentNode;

/**
 * Department Tree Reader Port (讀取端 - 組織樹查詢合約)
 *
 * <pre>
 * 定義部門組織樹唯讀模型 (Read Model) 的高效查詢範疇。 專為前端樹狀 UI、麵包屑導航與高性能全域搜尋設計。
 * 遵循依賴防禦原則，所有的查詢方法皆強迫以 tenantId 作為第一維度入參，落實嚴格的越權存取 (IDOR) 防護。
 * </pre>
 */
public interface DepartmentTreeReaderPort {

	/**
	 * 載入指定部門的「完整子樹」 (包含該部門自身與底下的所有子孫節點)。
	 * <p>
	 * 底層技術透過 JOIN 閉包表 (Closure Table) 達成，將傳統的遞迴遞增查詢優化至 $O(1)$ 層級的單次扁平化 SQL 查詢。
	 * 適用於懶加載組織樹或大區塊檢視場景。
	 * </p>
	 *
	 * @param tenantId         租戶識別碼
	 * @param rootDepartmentId 起始樹根的部門唯一識別碼
	 * @param includeDisabled  是否包含狀態為 DISABLED 的節點
	 * @return 經由樹狀深度 (Depth) 升序與排序權重 (SortOrder) 複合排列後的扁平化節點列表
	 */
	List<DepartmentNode> getSubtree(String tenantId, String rootId, boolean includeDisabled);

	/**
	 * 取得特定部門的完整麵包屑路徑 (由下向上追溯所有直系祖先)。
	 * <p>
	 * 回傳範例：[總公司] -> [研發處] -> [後端核心二課]。
	 * </p>
	 *
	 * @param tenantId     租戶識別碼
	 * @param departmentId 目標部門的唯一識別碼
	 * @return 從最頂層根祖先依序排列至當前目標節點的列表 (依樹狀深度 Depth 遞減排序)
	 */
	List<DepartmentNode> getBreadcrumbPath(String tenantId, String departmentId);

	/**
	 * 全域模糊搜尋部門（支援跨層級依據「部門名稱」或「部門代碼」進行不精確匹配）。
	 *
	 * @param tenantId 租戶識別碼
	 * @param keyword  搜尋關鍵字 (如 "後端" 或 "IT-002")
	 * @return 符合條件的部門節點基本資訊列表 (此列表為資料打平狀態，不包含樹狀深度等結構欄位)
	 */
	List<DepartmentNode> searchDepartments(String tenantId, String keyword);

	/**
	 * 根據 ID 查詢單一部門基本視圖。
	 */
	Optional<DepartmentNode> findById(String tenantId, String id);

	/**
	 * 查詢特定部門的「直屬」子部門列表 (僅限下一層)。
	 */
	List<DepartmentNode> findDirectChildren(String tenantId, String parentId);

	/**
	 * 🌟 高效能批次關聯查詢：獲取一組部門的員工映射表。
	 * <p>
	 * 直擊 department_employees_view 投影表，利用複合索引優化提供高速回傳。
	 * </p>
	 *
	 * @param tenantId      租戶識別碼
	 * @param departmentIds 欲查詢的部門 ID 列表
	 * @return 鍵為部門 ID，值為員工 ID 清單的 Map 結構
	 */
	Map<String, List<String>> findEmployeeMappings(String tenantId, List<String> departmentIds);

}