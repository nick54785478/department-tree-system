package com.example.demo.application.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.port.DepartmentTreeReaderPort;
import com.example.demo.infra.shared.dto.DepartmentFlatNodeGottenView;
import com.example.demo.infra.shared.dto.DepartmentNode;
import com.example.demo.infra.shared.dto.DepartmentTreeNodeGottenView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Department Query Application Service (部門查詢應用服務)
 *
 * <pre>
 * 擔任 CQRS 架構中「讀取端 (Query Side)」的 Use-Case Orchestrator。 
 * <b>架構職責與邊界</b>： 
 * 1. 隔離技術細節：不直接依賴特定的資料庫存取技術 (如 JPA 或 JDBC)，而是透過 {@link DepartmentTreeReaderPort} 取得資料。
 * 2. 視圖轉換與組裝：負責將底層查詢回來的扁平 DTO，轉換並組裝成前端 UI 所需的複雜樹狀視圖 (View)。 
 * 3. 效能最佳化：運用 O(N) 的 HashMap 演算法在 Java 記憶體中完成樹狀結構的重建，避免資料庫層級的遞迴查詢負擔。 
 * 4. 擴充性：未來若需導入 Redis 快取邏輯 (Cache-Aside Pattern) 或資料權限的二次檢驗，皆應在此層實作。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 全局宣告唯讀事務，最佳化資料庫連線效能，並防止意外的寫入操作
public class DepartmentQueryService {

	private final DepartmentTreeReaderPort departmentQueryPort;

	// =========================================================
	// 1. 取得樹狀結構
	// =========================================================

	/**
	 * 取得特定部門的完整巢狀樹狀結構 (包含自身與所有子孫)。
	 * <p>
	 * 適用於前端渲染組織樹 (Organization Tree) 或階層式選單。
	 * </p>
	 *
	 * @param tenantId 租戶識別碼
	 * @param rootId   作為起點的根節點部門 ID
	 * @return 組裝完畢且排序好的巢狀部門視圖
	 * @throws IllegalArgumentException 若查無此部門或子樹
	 */
	public DepartmentTreeNodeGottenView getTree(String tenantId, String rootId) {

		// 透過 Port 取得單次 SQL 查詢打平後的節點清單 (已利用 Closure Table 將時間複雜度降為 O(1) DB Round-trip)
		List<DepartmentNode> flatNodes = departmentQueryPort.getSubtree(tenantId, rootId);

		if (flatNodes.isEmpty()) {
			throw new IllegalArgumentException("Department subtree not found for rootId: " + rootId);
		}

		// 轉交給內部演算法進行記憶體組裝與排序
		return buildAndSortTree(flatNodes, rootId);
	}

	// =========================================================
	// 2. 取得麵包屑路徑
	// =========================================================

	/**
	 * 取得特定部門的麵包屑路徑 (Breadcrumb Path)。
	 * <p>
	 * 由最頂層根節點一路向下排列到目標節點，例如：[總公司] -> [研發處] -> [後端二課]。
	 * </p>
	 *
	 * @param tenantId     租戶識別碼
	 * @param departmentId 目標部門 ID
	 * @return 扁平化的節點視圖列表，依階層由高至低排列
	 */
	public List<DepartmentFlatNodeGottenView> getBreadcrumbPath(String tenantId, String departmentId) {
		List<DepartmentNode> nodes = departmentQueryPort.getBreadcrumbPath(tenantId, departmentId);

		// 將底層 DTO 轉換為前端專用的扁平 View 列表
		return nodes.stream().map(this::toFlatView).toList();
	}

	// =========================================================
	// 3. 模糊搜尋部門
	// =========================================================

	/**
	 * 根據關鍵字 (部門代碼或名稱) 全域模糊搜尋部門。
	 * <p>
	 * 適用於前端 Auto-complete (自動補全) 搜尋框元件。
	 * </p>
	 *
	 * @param tenantId 租戶識別碼
	 * @param keyword  搜尋關鍵字
	 * @return 符合條件的扁平化部門視圖列表
	 */
	public List<DepartmentFlatNodeGottenView> searchDepartments(String tenantId, String keyword) {
		// 防禦性編程：關鍵字為空時直接回傳空陣列，保護資料庫避免執行掃全表 (Full Table Scan) 的 LIKE '%%' 查詢
		if (keyword == null || keyword.trim().isEmpty()) {
			return List.of();
		}

		List<DepartmentNode> nodes = departmentQueryPort.searchDepartments(tenantId, keyword.trim());

		return nodes.stream().map(this::toFlatView).toList();
	}

	// =========================================================
	// 內部輔助與演算法方法 (Internal Helpers)
	// =========================================================

	/**
	 * 映射方法：將資料庫回傳的 DepartmentNode (DTO) 對應至 DepartmentFlatNodeGottenView (View)。
	 */
	private DepartmentFlatNodeGottenView toFlatView(DepartmentNode node) {
		return new DepartmentFlatNodeGottenView(node.tenantId(), node.id(), node.parentId(), node.code(), node.name(),
				node.status(), node.sortOrder(), node.depth());
	}

	/**
	 * 核心組裝演算法：將一維的扁平清單，組裝成 N 階層的巢狀樹狀結構並進行排序。
	 * <p>
	 * 時間複雜度：O(N) + O(N log N) 排序。 利用 HashMap 提供 O(1) 的節點查找能力，只需遍歷兩次平坦陣列即可完成所有父子
	 * Reference 的掛載。
	 * </p>
	 *
	 * @param flatNodes 資料庫回傳的無序扁平節點列表
	 * @param rootId    起點部門 ID
	 * @return 組裝完成的樹狀根節點
	 */
	private DepartmentTreeNodeGottenView buildAndSortTree(List<DepartmentNode> flatNodes, String rootId) {
		Map<String, DepartmentTreeNodeGottenView> nodeMap = new HashMap<>();

		// 第一階段 (O(N))：將資料庫的扁平結構 (DTO) 轉換為純粹的視圖 (View)，並存入 Map 方便快速查找
		for (DepartmentNode node : flatNodes) {
			nodeMap.put(node.id(),
					new DepartmentTreeNodeGottenView(node.tenantId(), node.id(), node.parentId(), node.code(),
							node.name(), node.status(), node.sortOrder(), node.depth(), node.directHeadcount(),
							node.totalHeadcount(), new ArrayList<>() // 預先初始化空的子節點清單，避免 NullPointerException
					));
		}

		DepartmentTreeNodeGottenView rootNode = null;

		// 第二階段 (O(N))：組裝親子關係 (建立記憶體 Reference 連結)
		for (DepartmentNode node : flatNodes) {
			DepartmentTreeNodeGottenView currentNode = nodeMap.get(node.id());

			if (node.id().equals(rootId)) {
				rootNode = currentNode; // 標記目標根節點
			} else {
				DepartmentTreeNodeGottenView parentNode = nodeMap.get(node.parentId());
				// 將自己掛載到父親的 children 清單底下 (這會直接改變存在 Map 內的同一個物件參考)
				if (parentNode != null) {
					parentNode.children().add(currentNode);
				}
			}
		}

		// 第三階段：在 Service 層統一處理視圖的排序邏輯，確保 View 物件本身的貧血/純粹性
		if (rootNode != null) {
			sortChildrenRecursively(rootNode);
		}

		return rootNode;
	}

	/**
	 * 遞迴輔助方法：依照 sortOrder (排序權重) 遞迴排序所有子節點。
	 * <p>
	 * 確保前端無論展開哪一個層級，子部門都會依照業務指定的順序呈現。
	 * </p>
	 *
	 * @param node 當下準備排序其子節點的部門節點
	 */
	private void sortChildrenRecursively(DepartmentTreeNodeGottenView node) {
		if (node.children() != null && !node.children().isEmpty()) {
			// 1. 使用 Comparator 針對當前節點的 children 進行升序排序 (數字越小排越前面)
			node.children().sort(Comparator.comparingInt(DepartmentTreeNodeGottenView::sortOrder));

			// 2. 繼續往下遞迴，確保每一層的子樹都被正確排序
			node.children().forEach(this::sortChildrenRecursively);
		}
	}
}