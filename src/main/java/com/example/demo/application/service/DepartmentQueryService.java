package com.example.demo.application.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
 *擔任 CQRS 架構中「讀取端 (Query Side)」的 Use-Case Orchestrator。
 *<b>架構職責與邊界：</b>
 * 1. <b>隔離技術細節：</b> 不直接依賴特定的資料庫存取技術 (如 JPA 或 JDBC)，而是透過 {@link DepartmentTreeReaderPort} 取得資料。
 * 2. <b>不可變視圖轉換：</b> 負責將底層查詢回來的扁平 DTO，以函數式編程 (Functional Programming) 轉換為前端 UI 所需的 Immutable Record 樹狀視圖。
 * 3. <b>記憶體自癒運算 (Self-Healing in Memory)：</b> 在組裝樹狀結構的同時，拋棄對資料庫 totalHeadcount 的信任，
 * 改採 DFS 深度優先演算法，於記憶體中動態滾動計算總人數，徹底免疫事件亂序帶來的數據錯亂風險。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 🌟 全局宣告唯讀事務，最佳化資料庫連線效能，並防止意外的寫入操作
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
	 * @param tenantId        租戶識別碼
	 * @param rootId          作為起點的根節點部門 ID
	 * @param includeDisabled 是否包含 Disabled 資料
	 * @return 組裝完畢、人數精準計算且排序好的巢狀部門視圖
	 * @throws IllegalArgumentException 若查無此部門或子樹
	 */
	public DepartmentTreeNodeGottenView getTree(String tenantId, String rootId, boolean includeDisabled) {

		// 1. 透過 Port 取得單次 SQL 查詢打平後的節點清單 (已利用 Closure Table 將時間複雜度降為 O(1) DB
		// Round-trip)
		List<DepartmentNode> flatNodes = departmentQueryPort.getSubtree(tenantId, rootId, includeDisabled);

		if (flatNodes.isEmpty()) {
			throw new IllegalArgumentException("Department subtree not found for rootId: " + rootId);
		}

		// 2. 鎖定目標根節點 (Source DTO)
		DepartmentNode rootDto = flatNodes.stream().filter(n -> n.id().equals(rootId)).findFirst()
				.orElseThrow(() -> new IllegalStateException("Root node data is missing in the result set"));

		// 3. 建立 O(1) 查找的 Adjacency List (將扁平清單按 parentId 分組)
		// 大幅降低遞迴尋找子節點的時間複雜度
		Map<String, List<DepartmentNode>> childrenGroupMap = flatNodes.stream().filter(n -> n.parentId() != null)
				.collect(Collectors.groupingBy(DepartmentNode::parentId));

		// 4. 啟動由下而上的遞迴組裝引擎
		return buildNodeRecursively(rootDto, childrenGroupMap);
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
	 * 核心組裝演算法：由下而上 (Bottom-Up) 的函數式遞迴構建引擎。
	 * 
	 * <pre>
	 * 完美契合 Java Record 的不可變特性 (Immutability)。 採用 DFS (深度優先搜尋) 策略，在實例化當前節點的 Record
	 * 之前，必定先遞迴完成所有子孫節點的構建， 並將子孫節點的總人數向上一層滾動累加。
	 * </pre>
	 *
	 * @param currentNode      當下準備轉換為視圖的底層 DTO 節點
	 * @param childrenGroupMap 預先分組好的 Adjacency List，用於 O(1) 查找子節點清單
	 * @return 裝載完畢且具備絕對準確數學加總的不可變樹狀視圖
	 */
	private DepartmentTreeNodeGottenView buildNodeRecursively(DepartmentNode currentNode,
			Map<String, List<DepartmentNode>> childrenGroupMap) {

		// 1. 取出屬於自己的直屬子節點 DTO
		List<DepartmentNode> childDtos = childrenGroupMap.getOrDefault(currentNode.id(), Collections.emptyList());

		List<DepartmentTreeNodeGottenView> childrenViews = new ArrayList<>();

		// 2. 滾動加總起點：預設總人數 = 自己的直屬人數 (唯一信任的 DB 基礎值)
		int accumulatedTotalHeadcount = currentNode.directHeadcount();

		// 3. DFS 深度優先遍歷：要求所有子節點先完成自我組裝
		for (DepartmentNode childDto : childDtos) {
			DepartmentTreeNodeGottenView childView = buildNodeRecursively(childDto, childrenGroupMap);
			childrenViews.add(childView);

			// 將子樹算好的總人數往上層累加
			accumulatedTotalHeadcount += childView.totalHeadcount();
		}

		// 4. 視圖排序：確保同層級兄弟節點依業務指定的 sortOrder 升序排列
		childrenViews.sort(Comparator.comparingInt(DepartmentTreeNodeGottenView::sortOrder));

		// 5. 終極防禦：建立並回傳不可變的 Record 視圖
		return new DepartmentTreeNodeGottenView(currentNode.tenantId(), currentNode.id(), currentNode.parentId(),
				currentNode.code(), currentNode.name(), currentNode.status(), currentNode.sortOrder(),
				currentNode.depth(), currentNode.directHeadcount(), accumulatedTotalHeadcount, // 注入記憶體精準推演出來的結果，無視 DB
																								// 原有的幽靈髒資料
				Collections.unmodifiableList(childrenViews) // 封裝防禦，避免外層代碼篡改子節點清單
		);
	}
}