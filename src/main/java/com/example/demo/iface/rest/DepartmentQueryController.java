package com.example.demo.iface.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.application.service.DepartmentQueryService;
import com.example.demo.iface.dto.res.DepartmentTreeGottenResource;
import com.example.demo.iface.dto.res.DepartmentsSearchedResource;
import com.example.demo.iface.dto.res.FlatDepartmentsGottenResource;
import com.example.demo.infra.shared.dto.DepartmentFlatNodeGottenView;
import com.example.demo.infra.shared.dto.DepartmentTreeNodeGottenView;

import lombok.RequiredArgsConstructor;

/**
 * Department Query Controller (基礎設施層 - 部門讀取端 API 控制器)
 *
 * <pre>
 * 遵循 CQRS (命令查詢職責分離) 架構規範，專責暴露並處理所有面向讀取端、高併發、低延遲的部門視圖查詢請求。
 *
 * <b>極致效能與架構純潔性：</b> 與 Command 寫入端截然不同，本控制器直接向 {@link DepartmentQueryService} 發出請求， 
 * 底層技術繞過任何複雜、帶鎖、充血的聚合根領域邏輯，直接從專為讀取優化的 department_views 與  department_tree 投影表中快查資料。
 * 
 * 享受純單表或單次高效 JOIN 的極致讀取效能。
 * </pre>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/departments")
public class DepartmentQueryController {

	/** 注入專注唯讀端組裝與排序的查詢應用服務 */
	private final DepartmentQueryService queryService;

	/**
	 * 1. 取得指定部門的完整 N 階層巢狀樹狀結構 (Tree View)。
	 * 
	 * <pre>
	 * <b>API 通道:</b> {@code GET /departments/{tenantId}/{id}/tree} <br>
	 * <b>主要用途:</b> 專門供應前端組織圖組件（Organization Chart / Tree Viewer）渲染全視圖使用。 
	 * 
	 * 會以單次 SQL 連鎖撈出該節點下的所有子孫，並在 Java 記憶體中高速裝配成帶有  children 的遞迴結構，內含預先滾動結算完畢的直屬與全體總人數統計。
	 * </pre>
	 *
	 * @param tenantId 租戶識別碼 (做為第一級隔離維度，URL 路徑參數)
	 * @param id       樹根起點部門 ID (將以此節點為根，向下挖出整棵幾何子樹)
	 * @return 夾帶多層級巢狀 DTO 結構的樹狀表現層資源
	 */
	@GetMapping("/{tenantId}/{id}/tree")
	public ResponseEntity<DepartmentTreeGottenResource> getTree(@PathVariable String tenantId,
			@PathVariable String id) {
		DepartmentTreeNodeGottenView data = queryService.getTree(tenantId, id);
		return ResponseEntity.ok(new DepartmentTreeGottenResource("200", "Success", data));
	}

	/**
	 * 2. 取得直系祖先麵包屑導覽路徑 (Breadcrumbs 路徑追溯)。
	 * 
	 * <pre>
	 * <b>API 通道:</b> {@code GET /departments/{tenantId}/{id}/breadcrumbs} <br>
	 * <b>主要用途:</b> 供應前端系統頂部導覽列渲染路徑痕跡（例如「總公司 > 研發處 > 後端二課」）。
	 * 底層透過幾何閉包表反向向上撈取，回傳的列表已依據深度（Depth）進行由高至低的精確打平排序。
	 * </pre>
	 *
	 * @param tenantId 租戶識別碼
	 * @param id       當前所在的終點部門 ID (以此為起點向上追溯)
	 * @return 經由資料庫打平、順序為 Root -> Parent -> Self 的輕量扁平部門清單資源
	 */
	@GetMapping("/{tenantId}/{id}/breadcrumbs")
	public ResponseEntity<FlatDepartmentsGottenResource> getBreadcrumbs(@PathVariable String tenantId,
			@PathVariable String id) {
		List<DepartmentFlatNodeGottenView> data = queryService.getBreadcrumbPath(tenantId, id);
		return ResponseEntity.ok(new FlatDepartmentsGottenResource("200", "Success", data));
	}

	/**
	 * 3. 跨層級全域關鍵字模糊搜尋部門 (Auto-complete 快速搜尋)。
	 * 
	 * <pre>
	 * <b>API 通道:</b> {@code GET /departments/{tenantId}/search?keyword=後端} <br>
	 * <b>主要用途:</b> 供應前端各表單中的下拉選單（Dropdown Item Selector）或全域彈窗關鍵字快速檢索。 支援同時針對「部門業務代碼
	 * (Code)」與「部門顯示名稱 (Name)」進行跨級別模糊比對。
	 * </pre>
	 *
	 * @param tenantId 租戶識別碼
	 * @param keyword  搜尋關鍵字 (透過 URL Query Parameter 傳遞，內部具備防禦性空值校驗以保護 DB)
	 * @return 滿足模糊比對條件的打平部門清單 (為維護分頁吞吐率，硬性限制最高前 50 筆)
	 */
	@GetMapping("/{tenantId}/search")
	public ResponseEntity<DepartmentsSearchedResource> searchDepartments(@PathVariable String tenantId,
			@RequestParam String keyword) {
		List<DepartmentFlatNodeGottenView> data = queryService.searchDepartments(tenantId, keyword);
		return ResponseEntity.ok(new DepartmentsSearchedResource("200", "Success", data));
	}
}